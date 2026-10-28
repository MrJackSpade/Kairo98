package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.DiskImageEntry
import com.mrjackspade.kairo.frontend.DiskImageLibrary
import com.mrjackspade.kairo.frontend.GameKey
import com.mrjackspade.kairo.frontend.GameKeyMigration
import com.mrjackspade.kairo.frontend.UiText
import com.mrjackspade.kairo.frontend.VersionedLibraryCache
import com.mrjackspade.kairo.frontend.companionFor
import com.mrjackspade.kairo.frontend.mediaFor
import com.mrjackspade.kairo.frontend.readOptionalText
import com.mrjackspade.kairo.frontend.readText
import com.mrjackspade.kairo.frontend.sameSource

import android.content.Context
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** PC-98 media: hard disks, floppies, and CloneCD discs whose track data is the .img beside the .ccd. */
object Pc98Media {
    private val floppyExtensions = setOf("fdi", "d88", "88d", "d98", "98d", "nfd", "fdd", "dcp", "dcu", "hdm", "xdf")

    val format = DiskImageLibrary.Format(
        kinds = floppyExtensions.associateWith { "fd" } + mapOf("hdi" to "hdi", "ccd" to "cd"),
        labels = mapOf("fd" to UiText.res(R.string.floppy_disk), "cd" to UiText.res(R.string.cd_rom),
            "hdi" to UiText.res(R.string.hard_disk)),
        maxBytes = mapOf("fd" to 64L * 1024 * 1024, "cd" to 2L * 1024 * 1024 * 1024, "hdi" to 4L * 1024 * 1024 * 1024),
        guestName = "PC-98",
        data = mapOf("ccd" to listOf("img")))

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isFloppy(name: String) = extension(name) in floppyExtensions
    fun isCd(name: String) = extension(name) == "ccd"
    fun supported(name: String) = format.isImage(name)
    fun suffix(name: String) = ".${extension(name)}"
    fun maxBytes(name: String) = format.limit(name)

    /**
     * The library and its cache. Layouts 4 and later are the shared library's; layouts 2 and 3 and the
     * JSON cache of version 1.0.3 and earlier are read once and replaced.
     */
    fun library(context: Context) = DiskImageLibrary(context, format, DiskImageLibrary.Cache(
        file = "library-v2.bin", mediaFormatsVersion = 4,
        legacy = VersionedLibraryCache.Legacy(File(context.filesDir, "library-v1.json"), 1, "entries", ::decodeLegacyEntry),
        earlierRecords = mapOf(2 to ::readCacheRecordV2, 3 to ::readCacheRecordV3)))

    /** Layout 3, from before games had keys: its ID hashed the entry's document. */
    private fun readCacheRecordV3(input: DataInputStream) = with(input) {
        keyed(readFields(input).copy(companions = List(readInt()) { readText() }))
    }

    /** Layout 2, from before CD track data was recorded. */
    private fun readCacheRecordV2(input: DataInputStream) = keyed(readFields(input))

    private fun readFields(input: DataInputStream) = with(input) {
        DiskImageEntry(readText(), readText(), readText(), readOptionalText(), readLong(), readLong(),
            readLong(), readLong(), readOptionalText(), readOptionalText())
    }

    /** An entry from before games had keys: its key is its path, and its content names its catalog record. */
    private fun keyed(entry: DiskImageEntry) = entry.copy(
        id = (entry.zipEntry?.let { GameKey.inArchive(entry.path, it) } ?: GameKey(entry.path)).path,
        catalogId = entry.contentId)

    /** The JSON cache that version 1.0.3 and earlier wrote. */
    private fun decodeLegacyEntry(value: JSONObject) = keyed(DiskImageEntry(value.getString("id"),
        value.getString("uri"), value.getString("path"),
        value.optString("zipEntry").takeIf { it.isNotEmpty() },
        value.optLong("sourceSize", -1), value.optLong("sourceModified"),
        value.optLong("imageSize", -1), value.optLong("imageCrc", -1),
        value.optString("contentId").takeIf { it.isNotEmpty() },
        value.optString("error").takeIf { it.isNotEmpty() }))

    /** The ID [entry] had before games had keys, which named its working image. */
    fun legacyEntryId(entry: DiskImageEntry): String =
        sha256("${entry.uri}\u0000${entry.zipEntry ?: ""}".toByteArray()).take(32)

    /** Working images were named by entry ID and content ID before games had keys. */
    fun legacyWorkingImages(library: DiskImageLibrary) = object : GameKeyMigration.EntryStore<DiskImageEntry> {
        private val directory = library.workingDirectory
        private var names = emptyList<String>()
        override fun refresh() {
            names = directory.list().orEmpty().filter { LEGACY_NAME.matches(it) }
        }
        private fun legacyName(claim: GameKeyMigration.Claim<DiskImageEntry>, entry: DiskImageEntry) =
            "${claim.entryId}-${entry.contentId?.substringAfter(':')}${suffix(entry.displayName)}"
        override fun has(claim: GameKeyMigration.Claim<DiskImageEntry>) = names.any { it.startsWith("${claim.entryId}-") }
        override fun move(claim: GameKeyMigration.Claim<DiskImageEntry>) {
            val entry = claim.item
            val from = File(directory, legacyName(claim, entry))
            // Copies made for an earlier version of the source stay where they are, as before.
            if (!from.isFile) return
            val to = library.workingImage(entry)
            val pairs = listOf(from to to) + if (isCd(entry.displayName))
                listOf(File(directory, cdData(from.name)) to File(directory, cdData(to.name)))
            else emptyList()
            for ((old, new) in pairs) if (old.exists()) {
                check(!new.exists()) { "${entry.displayName} has working copies in two places" }
                check(old.renameTo(new)) { "Could not move the working copy of ${entry.displayName}" }
            }
            names = names - pairs.map { it.first.name }.toSet()
        }
        // Copies for earlier versions of a source were never read again, and are not claimed.
        override fun isEmpty() = true
    }

    private fun cdData(name: String) = name.substringBeforeLast('.') + ".img"

    /** A working image's name before games had keys: `<entry ID>-<content>`, then its suffix. */
    private val LEGACY_NAME = Regex("[0-9a-f]{32}-.+")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

val DiskImageEntry.isFloppy: Boolean get() = Pc98Media.isFloppy(displayName)
val DiskImageEntry.isCd: Boolean get() = Pc98Media.isCd(displayName)

data class BootMedia(val hardDisk: DiskImageEntry, val bootFloppy: DiskImageEntry)

private val BOOT_DISK_NAME = Regex("(?i)\\bboot[ _-]*disk\\b")

fun requiredFloppyFor(context: Context, contentId: String, source: DiskImageEntry,
                      entries: List<DiskImageEntry>): DiskImageEntry =
    requiredMediaFor(context, contentId, source, entries, R.string.required_floppy_missing) { it.isFloppy }

/**
 * A disk a game names by content ID, which the library must have (see [mediaFor]); [missing] says
 * it is not, of the content ID's first characters.
 */
fun requiredMediaFor(context: Context, contentId: String, source: DiskImageEntry, entries: List<DiskImageEntry>,
                     missing: Int, kind: (DiskImageEntry) -> Boolean): DiskImageEntry =
    mediaFor(contentId, source, entries, kind)
        ?: error(context.getString(missing, contentId.substringAfter(':').take(8)))

/**
 * The disc for a game's CD-ROM drive: the CD its catalog names, else the one CD beside it.
 * A CD entry is its own disc.
 */
fun cdFor(entry: DiskImageEntry, catalogCdId: String?, entries: List<DiskImageEntry>): DiskImageEntry? =
    if (entry.isCd) entry else companionFor(catalogCdId, entry, entries, UiText.res(R.string.companion_cd)) { it.isCd }

/**
 * What a CD boots from: the disk its catalog names, else the one hard disk or floppy beside it.
 * PC-98 CDs are not bootable, so a CD without one cannot start.
 */
fun cdBootDiskFor(context: Context, cd: DiskImageEntry, catalogBootId: String?,
                  entries: List<DiskImageEntry>): DiskImageEntry {
    catalogBootId?.let { id ->
        return requiredMediaFor(context, id, cd, entries, R.string.required_boot_disk_missing) { !it.isCd }
    }
    val beside = entries.filter { it.playable && !it.isCd && sameSource(cd, it) }
    return beside.singleOrNull() ?: error(context.getString(
        if (beside.isEmpty()) R.string.cd_needs_boot_disk else R.string.cd_several_boot_disks, cd.displayName))
}

/** Resolve an explicit catalog dependency, or a clearly labelled pair in one archive. */
fun bootMediaFor(context: Context, entry: DiskImageEntry, entries: List<DiskImageEntry>,
                 requiredFloppyId: String?): BootMedia? {
    if (!entry.playable) return null
    if (!entry.isFloppy && requiredFloppyId != null) {
        val floppy = requiredFloppyFor(context, requiredFloppyId, entry, entries)
        return BootMedia(entry, floppy)
    }
    if (entry.zipEntry == null) {
        if (entry.isFloppy && requiredFloppyId != null && requiredFloppyId in setOf(entry.contentId, entry.catalogId))
            error(context.getString(R.string.hard_disk_missing))
        return null
    }
    val siblings = entries.filter { it.playable && it.zipEntry != null && it.uri == entry.uri }
    val hardDisks = siblings.filter { !it.isFloppy && !it.isCd }
    val bootFloppies = siblings.filter { it.isFloppy && BOOT_DISK_NAME.containsMatchIn(it.displayName) }
    if (hardDisks.size != 1 || bootFloppies.size != 1) {
        if (entry.isFloppy && requiredFloppyId != null && requiredFloppyId in setOf(entry.contentId, entry.catalogId))
            error(context.getString(R.string.hard_disk_missing_in_archive))
        return null
    }
    val pair = BootMedia(hardDisks.single(), bootFloppies.single())
    return pair.takeIf { entry.id == it.hardDisk.id || entry.id == it.bootFloppy.id }
}
