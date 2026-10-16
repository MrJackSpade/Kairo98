package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.LibraryItem
import com.mrjackspade.kairo.frontend.DocumentTreeWalker
import com.mrjackspade.kairo.frontend.ExternalGameFile
import com.mrjackspade.kairo.frontend.VersionedLibraryCache
import com.mrjackspade.kairo.frontend.readOptionalText
import com.mrjackspade.kairo.frontend.readText
import com.mrjackspade.kairo.frontend.writeOptionalText
import com.mrjackspade.kairo.frontend.writeText
import com.mrjackspade.kairo.frontend.LibraryScanPipeline

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** A source location is separate from the uncompressed disk-image content ID. */
data class LibraryEntry(
    override val id: String,
    override val uri: String,
    override val path: String,
    override val zipEntry: String?,
    val sourceSize: Long,
    val sourceModified: Long,
    val imageSize: Long,
    val imageCrc: Long,
    override val contentId: String?,
    override val error: String? = null,
    /** The other files the image reads: a CD's track data, by document URI or by ZIP entry. */
    val companions: List<String> = emptyList()
) : LibraryItem {
    override val displayName: String get() = (zipEntry ?: path).substringAfterLast('/').substringAfterLast('\\')
    override val playable: Boolean get() = contentId != null && error == null
    override val mediaLabel: String get() = when {
        isFloppy -> "Floppy disk"
        isCd -> "CD-ROM"
        else -> "Hard disk"
    }
    val isFloppy: Boolean get() = DiskFormat.isFloppy(displayName)
    val isCd: Boolean get() = DiskFormat.isCd(displayName)
}

data class BootMedia(val hardDisk: LibraryEntry, val bootFloppy: LibraryEntry)

private val BOOT_DISK_NAME = Regex("(?i)\\bboot[ _-]*disk\\b")

fun requiredFloppyFor(contentId: String, source: LibraryEntry,
                      entries: List<LibraryEntry>): LibraryEntry =
    requiredMediaFor(contentId, source, entries, "floppy") { it.isFloppy }

/** A disk a game names by content ID, preferring a copy from the game's own ZIP or folder. */
fun requiredMediaFor(contentId: String, source: LibraryEntry, entries: List<LibraryEntry>,
                     label: String, kind: (LibraryEntry) -> Boolean): LibraryEntry {
    val candidates = entries.filter { it.playable && kind(it) && it.contentId == contentId }
    return candidates.firstOrNull { it.uri == source.uri } ?: candidates.firstOrNull()
        ?: error("Required $label ${contentId.substringAfter(':').take(8)} is missing from the library")
}

/** Entries from the same ZIP as [entry], or from the same folder for a loose file. */
fun sameSource(entry: LibraryEntry, other: LibraryEntry): Boolean =
    if (entry.zipEntry != null) other.zipEntry != null && other.uri == entry.uri
    else other.zipEntry == null &&
        other.path.substringBeforeLast('/', "") == entry.path.substringBeforeLast('/', "")

/**
 * The disc for a game's CD-ROM drive: the CD its catalog names, else the one CD beside it.
 * A CD entry is its own disc.
 */
fun cdFor(entry: LibraryEntry, catalogCdId: String?, entries: List<LibraryEntry>): LibraryEntry? {
    if (entry.isCd) return entry
    catalogCdId?.let { return requiredMediaFor(it, entry, entries, "CD") { candidate -> candidate.isCd } }
    return entries.filter { it.playable && it.isCd && sameSource(entry, it) }.singleOrNull()
}

/**
 * What a CD boots from: the disk its catalog names, else the one hard disk or floppy beside it.
 * PC-98 CDs are not bootable, so a CD without one cannot start.
 */
fun cdBootDiskFor(cd: LibraryEntry, catalogBootId: String?, entries: List<LibraryEntry>): LibraryEntry {
    catalogBootId?.let { id ->
        return requiredMediaFor(id, cd, entries, "boot disk") { !it.isCd }
    }
    val beside = entries.filter { it.playable && !it.isCd && sameSource(cd, it) }
    return beside.singleOrNull() ?: error(if (beside.isEmpty())
        "${cd.displayName} needs a boot disk; start the game's hard disk or floppy instead"
        else "${cd.displayName} has several disks beside it; start the one that boots")
}

/** Resolve an explicit catalog dependency, or a clearly labelled pair in one archive. */
fun bootMediaFor(entry: LibraryEntry, entries: List<LibraryEntry>, requiredFloppyId: String?): BootMedia? {
    if (!entry.playable) return null
    if (!entry.isFloppy && requiredFloppyId != null) {
        val floppy = requiredFloppyFor(requiredFloppyId, entry, entries)
        return BootMedia(entry, floppy)
    }
    if (entry.zipEntry == null) {
        if (entry.isFloppy && entry.contentId == requiredFloppyId)
            error("Matching hard disk is missing from the library")
        return null
    }
    val siblings = entries.filter { it.playable && it.zipEntry != null && it.uri == entry.uri }
    val hardDisks = siblings.filter { !it.isFloppy && !it.isCd }
    val bootFloppies = siblings.filter {
        it.isFloppy && BOOT_DISK_NAME.containsMatchIn(it.displayName)
    }
    if (hardDisks.size != 1 || bootFloppies.size != 1) {
        if (entry.isFloppy && entry.contentId == requiredFloppyId)
            error("Matching hard disk is missing or ambiguous in this archive")
        return null
    }
    val pair = BootMedia(hardDisks.single(), bootFloppies.single())
    return pair.takeIf { entry.id == it.hardDisk.id || entry.id == it.bootFloppy.id }
}

object DiskFormat {
    private val floppyExtensions = setOf("fdi", "d88", "88d", "d98", "98d",
        "nfd", "fdd", "dcp", "dcu", "hdm", "xdf")
    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isFloppy(name: String): Boolean = extension(name) in floppyExtensions
    /** A CloneCD descriptor; its track data is [cdData]. */
    fun isCd(name: String): Boolean = extension(name) == "ccd"
    /** The core reads a CD's track data from the descriptor's name with ".img". */
    fun cdData(name: String): String = name.substringBeforeLast('.') + ".img"
    fun supported(name: String): Boolean = extension(name) == "hdi" || isFloppy(name) || isCd(name)
    fun suffix(name: String): String = ".${extension(name)}"
    /** The size limit of the image an entry hashes: for a CD, its track data. */
    fun maxBytes(name: String): Long = when {
        isFloppy(name) -> 64L * 1024 * 1024
        isCd(name) -> 2L * 1024 * 1024 * 1024
        else -> 4L * 1024 * 1024 * 1024
    }
    /** The content ID scheme: sha256-<kind>-v1, hashing a CD's track data. */
    fun kind(name: String): String = when {
        isFloppy(name) -> "fd"
        isCd(name) -> "cd"
        else -> "hdi"
    }
}

class RomLibrary(private val context: Context) {
    private val storeCache = VersionedLibraryCache(File(context.filesDir, "library-v2.bin"), 3,
        MAX_STORE_BYTES, MAX_DOCUMENTS, ::writeCacheRecord, ::readCacheRecord,
        VersionedLibraryCache.Legacy(File(context.filesDir, "library-v1.json"), 1, "entries",
            ::decodeLegacyEntry), earlierRecords = mapOf(2 to ::readCacheRecordV2))
    private val scanPipeline by lazy {
        LibraryScanPipeline(storeCache, LibraryEntry::id, ::planScan, ::inspectSource,
            ::failedSource,
            { source, index, total -> "Scanning ${index + 1}/$total: ${source.path}" },
            displayOrder = { entries -> entries.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + (it.zipEntry ?: "") }) },
            metadata = { JSONObject().put("mediaFormatsVersion", MEDIA_FORMATS_VERSION) },
            afterCommit = { plan -> pruneArchives(plan.work) })
    }
    private val archiveCache = File(context.cacheDir, "rom-archives").apply { mkdirs() }
    private val imageStore = File(context.filesDir, "media").apply {
        mkdirs()
        listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }
    val catalog = GameCatalog(context)

    val hashCount: Int get() = scanPipeline.hashCount

    fun cached(treeUri: Uri): List<LibraryEntry> = scanPipeline.cached(treeUri)

    /** Saves a library edited without a scan, such as after deleting a source. */
    fun store(treeUri: Uri, entries: List<LibraryEntry>) = scanPipeline.store(treeUri, entries)

    /** Delete the source document shared by every image entry from that file. */
    fun deleteSource(entry: LibraryEntry, entries: List<LibraryEntry>) {
        val sourceUri = Uri.parse(entry.uri)
        require(DocumentsContract.isDocumentUri(context, sourceUri)) {
            "This game is not a removable library document"
        }
        require(DocumentsContract.deleteDocument(context.contentResolver, sourceUri)) {
            "The document provider could not delete the game file"
        }
        if (entry.zipEntry == null) entry.companions.forEach { companion ->
            require(DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(companion))) {
                "The document provider could not delete ${DiskFormat.cdData(entry.displayName)}"
            }
        }
        val sourceEntries = entries.filter { it.uri == entry.uri }
        val archiveKey = sha256(entry.uri.toByteArray()).take(32)
        archiveCache.listFiles()?.filter { it.name.startsWith("$archiveKey-") ||
            it.name == "$archiveKey.part" }?.forEach { it.delete() }
        val imageIds = sourceEntries.map { it.id }.toSet()
        imageStore.listFiles()?.filter { file ->
            imageIds.any { file.name.startsWith("$it-") }
        }?.forEach { it.delete() }
    }

    /** Inspect one launcher-provided document without requiring a selected ROM tree. */
    fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean): List<LibraryEntry> {
        val source = Source(file.uri, file.name, file.size, file.modified)
        require(!DiskFormat.isCd(file.name)) {
            "A CD needs its ${DiskFormat.cdData(file.name)}; open the CD's folder or a ZIP that holds both"
        }
        if (DiskFormat.supported(file.name)) {
            val candidate = entry(source, null, file.size, -1, null)
            val id = context.contentResolver.openInputStream(file.uri)?.use {
                contentId(it, file.size, -1, cancelled, file.name)
            } ?: error("Unable to open ${file.name}")
            return listOf(candidate.copy(contentId = id))
        }
        require(file.name.endsWith(".zip", true)) { "Unsupported PC-98 game file: ${file.name}" }
        val archive = cachedZip(source, false, cancelled)
        return ZipFile(archive).use { zip ->
            zipImages(zip) { checkCancelled(cancelled) }.map { image ->
                val data = image.data ?: error(image.missing())
                val id = zip.getInputStream(data).use {
                    contentId(it, data.size, data.crc, cancelled, image.name)
                }
                entry(source, image.entry.name, data.size, data.crc, id, companions = image.companions)
            }
        }
    }

    fun scan(treeUri: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
             progress: (String) -> Unit): List<LibraryEntry> =
        scanPipeline.scan(treeUri, forceHash, cancelled, progress)

    private fun planScan(tree: Uri, cancelled: AtomicBoolean,
                         progress: (String) -> Unit): LibraryScanPipeline.Plan<Source, LibraryEntry> {
        val (files, folderErrors) = enumerate(tree, cancelled, progress)
        return LibraryScanPipeline.Plan(files, initialEntries = folderErrors)
    }

    /**
     * A ZIP's disk images, each with the member its bytes come from: the image itself, or a CD's
     * track data. A CD whose track data is missing has none, and fails alone.
     */
    private class ZipImage(val entry: java.util.zip.ZipEntry, val name: String,
                           val data: java.util.zip.ZipEntry?) {
        val companions: List<String> get() = if (data == null || data === entry) emptyList() else listOf(data.name)
        fun missing() = "${DiskFormat.cdData(name)} is missing from the ZIP"
    }

    private fun zipImages(zip: ZipFile, checkCancelled: () -> Unit): List<ZipImage> {
        val members = HashMap<String, java.util.zip.ZipEntry>()
        val images = ArrayList<Pair<java.util.zip.ZipEntry, String>>()
        val entries = zip.entries()
        var inspected = 0
        while (entries.hasMoreElements()) {
            checkCancelled()
            require(++inspected <= MAX_ZIP_ENTRIES) { "ZIP has too many entries" }
            val item = entries.nextElement()
            val normalized = safeEntryName(item.name)
            require(members.put(normalized.lowercase(Locale.ROOT), item) == null) { "Duplicate ZIP entry" }
            if (!item.isDirectory && DiskFormat.supported(normalized)) {
                require(images.size < MAX_ZIP_IMAGES) { "ZIP has too many disk images" }
                images.add(item to normalized)
            }
        }
        require(images.isNotEmpty()) { "No supported disk image in ZIP" }
        return images.map { (item, name) ->
            val data = if (DiskFormat.isCd(name)) members[DiskFormat.cdData(name).lowercase(Locale.ROOT)]
                else item
            if (data != null) require(data.size in 1..imageLimit(name) && data.compressedSize > 0 &&
                data.size / data.compressedSize <= MAX_EXPANSION_RATIO) {
                "Disk image exceeds size or expansion limit"
            }
            ZipImage(item, name, data)
        }
    }

    private fun inspectSource(source: Source,
                              scan: LibraryScanPipeline.ScanContext<LibraryEntry>): List<LibraryEntry> {
        if (source.path.endsWith(".zip", ignoreCase = true)) {
            val oldEntries = scan.prior.values.filter { it.uri == source.uri.toString() }
            val formatsCurrent = (scan.previous?.root?.optInt("mediaFormatsVersion", 1) ?: 0) >=
                MEDIA_FORMATS_VERSION
            if (!scan.forceHash && formatsCurrent && trusted(source) && oldEntries.isNotEmpty() &&
                oldEntries.all { sameSource(it, source) && it.error == null }) return oldEntries
            val zipFile = cachedZip(source, scan.forceHash, scan.cancelled)
            return ZipFile(zipFile).use { zip ->
                zipImages(zip, scan::checkCancelled).map { image ->
                    scan.checkCancelled()
                    val data = image.data
                    val candidate = entry(source, image.entry.name, data?.size ?: -1, data?.crc ?: -1,
                        null, companions = image.companions)
                    val old = scan.prior[candidate.id]
                    try {
                        requireNotNull(data) { image.missing() }
                        val id = if (!scan.forceHash && trusted(source) && old != null &&
                            sameFingerprint(old, candidate) && GameCatalog.validId(old.contentId ?: "")) {
                            old.contentId
                        } else {
                            scan.progress("Hashing ${image.entry.name}")
                            scan.hashStarted()
                            zip.getInputStream(data).use {
                                contentId(it, data.size, data.crc, scan.cancelled, image.name)
                            }
                        }
                        candidate.copy(contentId = id)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        scan.checkCancelled()
                        candidate.copy(error = error.message ?: "Disk image unreadable")
                    }
                }
            }
        }
        if (DiskFormat.isCd(source.path)) {
            val data = source.cdData ?: error("${DiskFormat.cdData(source.path.substringAfterLast('/'))} is missing")
            val candidate = entry(source, null, data.size, -1, null, companions = listOf(data.uri.toString()))
            val old = scan.prior[candidate.id]
            val id = if (!scan.forceHash && trusted(source) && trusted(data) && old != null &&
                sameFingerprint(old, candidate) && GameCatalog.validId(old.contentId ?: "")) old.contentId
            else {
                scan.progress("Hashing ${data.path}")
                scan.hashStarted()
                context.contentResolver.openInputStream(data.uri)?.use {
                    contentId(it, data.size, -1, scan.cancelled, source.path)
                } ?: error("Unable to open ${data.path}")
            }
            return listOf(candidate.copy(contentId = id))
        }
        val candidate = entry(source, null, source.size, -1, null)
        val old = scan.prior[candidate.id]
        val id = if (!scan.forceHash && trusted(source) && old != null &&
            sameFingerprint(old, candidate) && GameCatalog.validId(old.contentId ?: "")) old.contentId
        else {
            scan.progress("Hashing ${source.path}")
            scan.hashStarted()
            context.contentResolver.openInputStream(source.uri)?.use {
                contentId(it, source.size, -1, scan.cancelled, source.path)
            } ?: error("Unable to open disk image")
        }
        return listOf(candidate.copy(contentId = id))
    }

    private fun failedSource(source: Source,
                             scan: LibraryScanPipeline.ScanContext<LibraryEntry>,
                             error: Exception): List<LibraryEntry> = listOf(
        entry(source, null, source.size, -1, null,
            error.message ?: if (source.path.endsWith(".zip", ignoreCase = true))
                "ZIP unreadable" else "Disk image unreadable")
    )

    /**
     * Return an app-private working image for this source and content version. A CD is its
     * descriptor, with its track data beside it under the name the core derives.
     */
    @Synchronized fun prepare(entry: LibraryEntry, cancelled: AtomicBoolean,
                progress: (String) -> Unit): File {
        require(entry.playable) { "This entry is not ready to launch" }
        val stem = "${entry.id}-${entry.contentId!!.substringAfter(':')}"
        val file = File(imageStore, "$stem${DiskFormat.suffix(entry.displayName)}")
        val data = if (entry.isCd) File(imageStore, DiskFormat.cdData(file.name)) else file
        if (file.isFile && file.length() > 0 && data.isFile && data.length() > 0) return file
        if (entry.imageSize > 0) require(imageStore.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            "Not enough free space for this disk image"
        }
        val partial = File(imageStore, "${data.name}.part")
        val descriptor = File(imageStore, "${file.name}.part")
        partial.delete()
        descriptor.delete()
        try {
            progress("Preparing ${entry.displayName}")
            if (entry.zipEntry == null) {
                val dataUri = if (entry.isCd) Uri.parse(entry.companions.single()) else Uri.parse(entry.uri)
                context.contentResolver.openInputStream(dataUri)?.use { input ->
                    partial.outputStream().use { output -> copyVerified(entry, input, -1, cancelled, output) }
                } ?: error("Unable to open disk image")
                if (entry.isCd) context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    descriptor.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open ${entry.displayName}")
            } else {
                ZipFile(archive(entry, cancelled)).use { zip ->
                    fun member(name: String) = zip.getEntry(name)?.also {
                        require(safeEntryName(it.name) == safeEntryName(name))
                    } ?: error("$name is missing from the ZIP")
                    val image = member(entry.companions.singleOrNull() ?: entry.zipEntry)
                    require(image.size in 1..imageLimit(entry.displayName) && image.compressedSize > 0 &&
                        image.size / image.compressedSize <= MAX_EXPANSION_RATIO) {
                        "Disk image expansion ratio is too large"
                    }
                    zip.getInputStream(image).use { input ->
                        partial.outputStream().use { output -> copyVerified(entry, input, image.crc, cancelled, output) }
                    }
                    if (entry.isCd) zip.getInputStream(member(entry.zipEntry)).use { input ->
                        descriptor.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            require(partial.renameTo(data)) { "Unable to save working disk image" }
            if (entry.isCd) require(descriptor.renameTo(file)) { "Unable to save ${entry.displayName}" }
            return file
        } catch (error: Exception) {
            partial.delete()
            descriptor.delete()
            throw error
        }
    }

    private fun copyVerified(entry: LibraryEntry, input: InputStream, crc: Long,
                             cancelled: AtomicBoolean, output: OutputStream) {
        require(contentId(input, entry.imageSize, crc, cancelled, entry.displayName, output) ==
            entry.contentId) { "Disk image changed since scan; refresh the library" }
    }

    /** The local copy of the ZIP that holds [entry]. */
    fun archive(entry: LibraryEntry, cancelled: AtomicBoolean): File {
        require(entry.zipEntry != null) { "${entry.displayName} is not in a ZIP" }
        return cachedZip(Source(Uri.parse(entry.uri), entry.path, entry.sourceSize, entry.sourceModified),
            false, cancelled)
    }

    /** A document to inspect; a CD descriptor also carries its track data. */
    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long,
                              val cdData: Source? = null)

    private fun enumerate(tree: Uri, cancelled: AtomicBoolean,
                          progress: (String) -> Unit): Pair<List<Source>, List<LibraryEntry>> {
        val result = DocumentTreeWalker(context.contentResolver).scan(tree, cancelled,
            { name -> DiskFormat.supported(name) || name.endsWith(".zip", true) ||
                name.endsWith(".img", true) }, progress)
        val files = result.files.map { Source(it.uri, it.path, it.size, it.modified) }
        // Track data is read through its descriptor; an .img without one is not a PC-98 image.
        val byPath = files.associateBy { it.path.lowercase(Locale.ROOT) }
        return files.filter { !it.path.endsWith(".img", true) }.map { source ->
            if (DiskFormat.isCd(source.path))
                source.copy(cdData = byPath[DiskFormat.cdData(source.path).lowercase(Locale.ROOT)])
            else source
        } to
            result.folders.map { folder ->
                entry(Source(folder.uri, folder.path, -1, 0), null, -1, -1, null,
                    "Unreadable folder: ${folder.message}")
            }
    }

    @Synchronized private fun cachedZip(source: Source, force: Boolean,
                                        cancelled: AtomicBoolean): File {
        val key = sha256(source.uri.toString().toByteArray()).take(32)
        val file = File(archiveCache, "$key-${source.size}-${source.modified}.zip")
        if (!force && trusted(source) && file.isFile && file.length() == source.size) return file
        require(source.size <= MAX_ARCHIVE_BYTES) { "ZIP exceeds size limit" }
        if (source.size >= 0) require(archiveCache.usableSpace > source.size + 16L * 1024 * 1024) {
            "Not enough free space to inspect ZIP"
        }
        val partial = File(archiveCache, "$key.part")
        partial.delete()
        try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        checkCancelled(cancelled)
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= MAX_ARCHIVE_BYTES) { "ZIP exceeds size limit" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Unable to open ZIP")
            if (source.size >= 0) require(partial.length() == source.size) { "ZIP changed during read" }
            if (file.exists()) file.delete()
            require(partial.renameTo(file)) { "Unable to cache ZIP" }
            return file
        } finally { partial.delete() }
    }

    @Synchronized private fun pruneArchives(files: List<Source>) {
        val active = files.filter { it.path.endsWith(".zip", true) }.map {
            val key = sha256(it.uri.toString().toByteArray()).take(32)
            "$key-${it.size}-${it.modified}.zip"
        }.toSet()
        archiveCache.listFiles()?.forEach { file ->
            if ((file.name.endsWith(".zip") && file.name !in active) ||
                file.name.endsWith(".part")) file.delete()
        }
    }

    private fun contentId(input: InputStream, expectedSize: Long, expectedCrc: Long,
                          cancelled: AtomicBoolean, imageName: String,
                          output: OutputStream? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            checkCancelled(cancelled)
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= imageLimit(imageName)) { "Disk image exceeds size limit" }
            digest.update(buffer, 0, read)
            crc.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        require(count > 0) { "Empty disk image" }
        if (expectedSize >= 0) require(count == expectedSize) { "Disk image size changed during read" }
        if (expectedCrc >= 0) require(crc.value == expectedCrc) { "ZIP entry checksum mismatch" }
        val kind = DiskFormat.kind(imageName)
        return "sha256-$kind-v1:${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun imageLimit(name: String) = DiskFormat.maxBytes(name)

    /** A loose CD's modification time is its newest file's, so changed track data rehashes. */
    private fun entry(source: Source, zipEntry: String?, imageSize: Long, imageCrc: Long,
                      contentId: String?, error: String? = null,
                      companions: List<String> = emptyList()): LibraryEntry {
        val id = sha256("${source.uri}\u0000${zipEntry ?: ""}".toByteArray()).take(32)
        return LibraryEntry(id, source.uri.toString(), source.path, zipEntry,
            source.size, maxOf(source.modified, source.cdData?.modified ?: 0), imageSize, imageCrc,
            contentId, error, companions)
    }

    private fun trusted(source: Source) = source.size >= 0 && source.modified > 0
    private fun sameSource(old: LibraryEntry, source: Source) =
        old.sourceSize == source.size &&
            old.sourceModified == maxOf(source.modified, source.cdData?.modified ?: 0)
    private fun sameFingerprint(old: LibraryEntry, item: LibraryEntry) =
        sameSource(old, Source(Uri.parse(item.uri), item.path, item.sourceSize, item.sourceModified)) &&
        old.imageSize == item.imageSize && old.imageCrc == item.imageCrc

    private fun safeEntryName(name: String): String {
        val normalized = name.replace('\\', '/').removeSuffix("/")
        require(normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.length <= 4096 && normalized.split('/').none(String::isEmpty) &&
            !normalized.contains(':') && !normalized.contains('\u0000') &&
            normalized.split('/').none { it == ".." || it == "." }) { "Unsafe ZIP entry path" }
        return normalized
    }

    private fun writeCacheRecord(output: java.io.DataOutputStream, entry: LibraryEntry) = with(output) {
        writeText(entry.id); writeText(entry.uri); writeText(entry.path); writeOptionalText(entry.zipEntry)
        writeLong(entry.sourceSize); writeLong(entry.sourceModified)
        writeLong(entry.imageSize); writeLong(entry.imageCrc)
        writeOptionalText(entry.contentId); writeOptionalText(entry.error)
        writeInt(entry.companions.size); entry.companions.forEach { writeText(it) }
    }

    private fun readCacheRecord(input: java.io.DataInputStream) = with(input) {
        readCacheRecordV2(input).copy(companions = List(readInt()) { readText() })
    }

    /** Layout 2, from before CD track data was recorded. */
    private fun readCacheRecordV2(input: java.io.DataInputStream) = with(input) {
        LibraryEntry(readText(), readText(), readText(), readOptionalText(), readLong(), readLong(),
            readLong(), readLong(), readOptionalText(), readOptionalText())
    }

    /** The JSON cache that version 1.0.3 and earlier wrote. */
    private fun decodeLegacyEntry(value: JSONObject) = LibraryEntry(value.getString("id"),
        value.getString("uri"), value.getString("path"),
        value.optString("zipEntry").takeIf { it.isNotEmpty() },
        value.optLong("sourceSize", -1), value.optLong("sourceModified"),
        value.optLong("imageSize", -1), value.optLong("imageCrc", -1),
        value.optString("contentId").takeIf { it.isNotEmpty() },
        value.optString("error").takeIf { it.isNotEmpty() })


    companion object {
        private const val MEDIA_FORMATS_VERSION = 4
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ZIP_ENTRIES = 50_000
        private const val MAX_ZIP_IMAGES = 1_000
        private const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
        private const val MAX_EXPANSION_RATIO = 1000L
        private const val MAX_STORE_BYTES = 16 * 1024 * 1024
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        private fun checkCancelled(cancelled: AtomicBoolean) {
            if (cancelled.get()) throw CancellationException("Cancelled")
        }
    }
}
