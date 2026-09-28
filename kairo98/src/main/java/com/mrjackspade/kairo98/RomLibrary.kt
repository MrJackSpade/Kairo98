package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.LibraryItem
import com.mrjackspade.kairo.frontend.DocumentTreeWalker
import com.mrjackspade.kairo.frontend.ExternalGameFile

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import org.json.JSONArray
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
    val uri: String,
    override val path: String,
    override val zipEntry: String?,
    val sourceSize: Long,
    val sourceModified: Long,
    val imageSize: Long,
    val imageCrc: Long,
    override val contentId: String?,
    override val error: String? = null
) : LibraryItem {
    override val displayName: String get() = (zipEntry ?: path).substringAfterLast('/').substringAfterLast('\\')
    override val playable: Boolean get() = contentId != null && error == null
    override val mediaLabel: String get() = if (isFloppy) "Floppy disk" else "Hard disk"
    val isFloppy: Boolean get() = DiskFormat.isFloppy(displayName)
}

data class BootMedia(val hardDisk: LibraryEntry, val bootFloppy: LibraryEntry)

fun requiredFloppyFor(contentId: String, source: LibraryEntry,
                      entries: List<LibraryEntry>): LibraryEntry {
    val candidates = entries.filter { it.playable && it.isFloppy && it.contentId == contentId }
    return candidates.firstOrNull { it.uri == source.uri } ?: candidates.firstOrNull()
        ?: error("Required floppy ${contentId.substringAfter(':').take(8)} is missing from the library")
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
    val hardDisks = siblings.filter { !it.isFloppy }
    val bootFloppies = siblings.filter {
        it.isFloppy && Regex("(?i)\\bboot[ _-]*disk\\b").containsMatchIn(it.displayName)
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
    fun supported(name: String): Boolean = extension(name) == "hdi" || isFloppy(name)
    fun suffix(name: String): String = ".${extension(name)}"
    fun maxBytes(name: String): Long = if (isFloppy(name)) 64L * 1024 * 1024
        else 4L * 1024 * 1024 * 1024
}

class RomLibrary(private val context: Context) {
    private val store = File(context.filesDir, "library-v1.json")
    private val scanLock = Any()
    private val archiveCache = File(context.cacheDir, "rom-archives").apply { mkdirs() }
    private val imageStore = File(context.filesDir, "media").apply {
        mkdirs()
        listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }
    val catalog = GameCatalog(context)

    @Volatile var hashCount = 0
        private set

    fun cached(treeUri: Uri): List<LibraryEntry> = readStore().let { snapshot ->
        if (snapshot.first == treeUri.toString()) snapshot.second else emptyList()
    }

    /** Delete the source document shared by every image entry from that file. */
    fun deleteSource(entry: LibraryEntry, entries: List<LibraryEntry>) {
        val sourceUri = Uri.parse(entry.uri)
        require(DocumentsContract.isDocumentUri(context, sourceUri)) {
            "This game is not a removable library document"
        }
        require(DocumentsContract.deleteDocument(context.contentResolver, sourceUri)) {
            "The document provider could not delete the game file"
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
            val names = HashSet<String>()
            val found = ArrayList<LibraryEntry>()
            val entries = zip.entries()
            var inspected = 0
            while (entries.hasMoreElements()) {
                checkCancelled(cancelled)
                require(++inspected <= MAX_ZIP_ENTRIES) { "ZIP has too many entries" }
                val item = entries.nextElement()
                val path = safeEntryName(item.name)
                require(names.add(path.lowercase(Locale.ROOT))) { "Duplicate ZIP entry" }
                if (item.isDirectory || !DiskFormat.supported(path)) continue
                require(found.size < MAX_ZIP_IMAGES) { "ZIP has too many disk images" }
                require(item.size in 1..imageLimit(path) && item.compressedSize > 0 &&
                    item.size / item.compressedSize <= MAX_EXPANSION_RATIO) {
                    "Disk image exceeds size or expansion limit"
                }
                val id = zip.getInputStream(item).use {
                    contentId(it, item.size, item.crc, cancelled, path)
                }
                found.add(entry(source, item.name, item.size, item.crc, id))
            }
            require(found.isNotEmpty()) { "No PC-98 disk image in ZIP" }
            found
        }
    }

    fun scan(treeUri: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
             progress: (String) -> Unit): List<LibraryEntry> = synchronized(scanLock) {
        checkCancelled(cancelled)
        val snapshot = readStore()
        val prior = if (snapshot.first == treeUri.toString()) snapshot.second.associateBy { it.id }
            else emptyMap()
        val formatsCurrent = snapshot.third >= MEDIA_FORMATS_VERSION
        val result = ArrayList<LibraryEntry>()
        val (files, folderErrors) = enumerate(treeUri, cancelled, progress)
        result.addAll(folderErrors)
        hashCount = 0
        for ((index, source) in files.withIndex()) {
            checkCancelled(cancelled)
            progress("Scanning ${index + 1}/${files.size}: ${source.path}")
            if (source.path.endsWith(".zip", ignoreCase = true)) {
                val oldEntries = prior.values.filter { it.uri == source.uri.toString() }
                if (!forceHash && formatsCurrent && trusted(source) && oldEntries.isNotEmpty() &&
                    oldEntries.all { sameSource(it, source) && it.error == null }) {
                    result.addAll(oldEntries)
                    continue
                }
                try {
                    val zipFile = cachedZip(source, forceHash, cancelled)
                    ZipFile(zipFile).use { zip ->
                        val names = HashSet<String>()
                        val playable = ArrayList<java.util.zip.ZipEntry>()
                        val entries = zip.entries()
                        var inspected = 0
                        while (entries.hasMoreElements()) {
                            checkCancelled(cancelled)
                            require(++inspected <= MAX_ZIP_ENTRIES) { "ZIP has too many entries" }
                            val item = entries.nextElement()
                            val normalized = safeEntryName(item.name)
                            require(names.add(normalized.lowercase(Locale.ROOT))) { "Duplicate ZIP entry" }
                            if (!item.isDirectory && DiskFormat.supported(normalized)) {
                                require(playable.size < MAX_ZIP_IMAGES) { "ZIP has too many disk images" }
                                require(item.size in 1..imageLimit(normalized)) { "Disk image exceeds size limit" }
                                require(item.compressedSize > 0 &&
                                    item.size / item.compressedSize <= MAX_EXPANSION_RATIO) {
                                    "Disk image expansion ratio is too large"
                                }
                                playable.add(item)
                            }
                        }
                        require(playable.isNotEmpty()) { "No supported disk image in ZIP" }
                        for (item in playable) {
                            checkCancelled(cancelled)
                            val candidate = entry(source, item.name, item.size, item.crc, null)
                            val old = prior[candidate.id]
                            try {
                                val id = if (!forceHash && trusted(source) && old != null &&
                                    sameFingerprint(old, candidate) && GameCatalog.validId(old.contentId ?: "")) {
                                    old.contentId
                                } else {
                                    progress("Hashing ${item.name}")
                                    hashCount++
                                    zip.getInputStream(item).use {
                                        contentId(it, item.size, item.crc, cancelled, item.name)
                                    }
                                }
                                result.add(candidate.copy(contentId = id))
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) {
                                result.add(candidate.copy(error = error.message ?: "Disk image unreadable"))
                            }
                        }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    result.add(entry(source, null, source.size, -1, null, error.message ?: "ZIP unreadable"))
                }
            } else {
                val candidate = entry(source, null, source.size, -1, null)
                val old = prior[candidate.id]
                try {
                    val id = if (!forceHash && trusted(source) && old != null &&
                        sameFingerprint(old, candidate) && GameCatalog.validId(old.contentId ?: "")) {
                        old.contentId
                    } else {
                        progress("Hashing ${source.path}")
                        hashCount++
                        context.contentResolver.openInputStream(source.uri)?.use {
                            contentId(it, source.size, -1, cancelled, source.path)
                        } ?: error("Unable to open disk image")
                    }
                    result.add(candidate.copy(contentId = id))
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    result.add(candidate.copy(error = error.message ?: "Disk image unreadable"))
                }
            }
        }
        checkCancelled(cancelled)
        saveStore(treeUri, result)
        pruneArchives(files)
        result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + (it.zipEntry ?: "") })
    }

    /** Return an app-private writable working image for this source and content version. */
    @Synchronized fun prepare(entry: LibraryEntry, cancelled: AtomicBoolean,
                progress: (String) -> Unit): File {
        require(entry.playable) { "This entry is not ready to launch" }
        val file = File(imageStore,
            "${entry.id}-${entry.contentId!!.substringAfter(':')}${DiskFormat.suffix(entry.displayName)}")
        if (file.isFile && file.length() > 0) return file
        if (entry.imageSize > 0) require(imageStore.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            "Not enough free space for this disk image"
        }
        val partial = File(imageStore, "${file.name}.part")
        partial.delete()
        try {
            val source = Source(Uri.parse(entry.uri), entry.path, entry.sourceSize, entry.sourceModified)
            val expected = entry.contentId
            if (entry.zipEntry == null) {
                progress("Preparing ${entry.displayName}")
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    partial.outputStream().use { output ->
                        require(contentId(input, entry.imageSize, -1, cancelled,
                            entry.displayName, output) == expected) {
                            "Disk image changed since scan; refresh the library"
                        }
                    }
                } ?: error("Unable to open disk image")
            } else {
                val archive = cachedZip(source, false, cancelled)
                ZipFile(archive).use { zip ->
                    val image = zip.getEntry(entry.zipEntry) ?: error("Disk image missing from ZIP")
                    require(safeEntryName(image.name) == safeEntryName(entry.zipEntry))
                    require(image.size in 1..imageLimit(image.name) && image.compressedSize > 0 &&
                        image.size / image.compressedSize <= MAX_EXPANSION_RATIO) {
                        "Disk image expansion ratio is too large"
                    }
                    progress("Preparing ${entry.displayName}")
                    zip.getInputStream(image).use { input ->
                        partial.outputStream().use { output ->
                            require(contentId(input, image.size, image.crc, cancelled,
                                image.name, output) == expected) {
                                "Disk image changed since scan; refresh the library"
                            }
                        }
                    }
                }
            }
            require(partial.renameTo(file)) { "Unable to save working disk image" }
            return file
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long)

    private fun enumerate(tree: Uri, cancelled: AtomicBoolean,
                          progress: (String) -> Unit): Pair<List<Source>, List<LibraryEntry>> {
        val result = DocumentTreeWalker(context.contentResolver).scan(tree, cancelled,
            { name -> DiskFormat.supported(name) || name.endsWith(".zip", true) }, progress)
        return result.files.map { Source(it.uri, it.path, it.size, it.modified) } to
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
        val kind = if (DiskFormat.isFloppy(imageName)) "fd" else "hdi"
        return "sha256-$kind-v1:${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun imageLimit(name: String) = DiskFormat.maxBytes(name)

    private fun entry(source: Source, zipEntry: String?, imageSize: Long, imageCrc: Long,
                      contentId: String?, error: String? = null): LibraryEntry {
        val id = sha256("${source.uri}\u0000${zipEntry ?: ""}".toByteArray()).take(32)
        return LibraryEntry(id, source.uri.toString(), source.path, zipEntry,
            source.size, source.modified, imageSize, imageCrc, contentId, error)
    }

    private fun trusted(source: Source) = source.size >= 0 && source.modified > 0
    private fun sameSource(old: LibraryEntry, source: Source) =
        old.sourceSize == source.size && old.sourceModified == source.modified
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

    private fun readStore(): Triple<String?, List<LibraryEntry>, Int> = try {
        if (!store.isFile || store.length() > MAX_STORE_BYTES) Triple(null, emptyList(), 0)
        else {
            val json = JSONObject(AtomicFile(store).readFully().toString(Charsets.UTF_8))
            if (json.optInt("schemaVersion") != 1) Triple(null, emptyList(), 0)
            else {
                val array = json.optJSONArray("entries") ?: JSONArray()
                val items = ArrayList<LibraryEntry>()
                for (i in 0 until array.length()) {
                    val value = array.optJSONObject(i) ?: continue
                    val id = value.optString("id")
                    val uri = value.optString("uri")
                    val path = value.optString("path")
                    val zipEntry = value.optString("zipEntry").takeIf { it.isNotEmpty() }
                    if (!LOCATION_ID.matches(id) || !uri.startsWith("content://") ||
                        path.isBlank() || path.length > 4096 ||
                        (zipEntry != null && (zipEntry.length > 4096 ||
                            runCatching { safeEntryName(zipEntry) }.isFailure))) continue
                    val sourceSize = value.optLong("sourceSize", -1)
                    val sourceModified = value.optLong("sourceModified")
                    val imageSize = value.optLong("imageSize", -1)
                    val imageCrc = value.optLong("imageCrc", -1)
                    if (sourceSize < -1 || sourceModified < 0 || imageSize < -1 || imageCrc < -1) continue
                    items.add(LibraryEntry(id, uri, path, zipEntry, sourceSize, sourceModified,
                        imageSize, imageCrc,
                        value.optString("contentId").takeIf(GameCatalog::validId),
                        value.optString("error").takeIf { it.length in 1..1024 }))
                }
                Triple(json.optString("treeUri"), items, json.optInt("mediaFormatsVersion", 1))
            }
        }
    } catch (_: Exception) { Triple(null, emptyList(), 0) }

    private fun saveStore(tree: Uri, entries: List<LibraryEntry>) {
        if (store.isFile) {
            val existing = try { JSONObject(AtomicFile(store).readFully().toString(Charsets.UTF_8)) }
                catch (_: Exception) { null }
            require(existing == null || existing.optInt("schemaVersion") == 1) {
                "Library cache uses a newer schema; update Kairo98 before scanning"
            }
        }
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONObject().put("id", entry.id).put("uri", entry.uri)
                .put("path", entry.path).put("zipEntry", entry.zipEntry ?: "")
                .put("sourceSize", entry.sourceSize).put("sourceModified", entry.sourceModified)
                .put("imageSize", entry.imageSize).put("imageCrc", entry.imageCrc)
                .put("contentId", entry.contentId ?: "").put("error", entry.error ?: ""))
        }
        val bytes = JSONObject().put("schemaVersion", 1)
            .put("mediaFormatsVersion", MEDIA_FORMATS_VERSION).put("treeUri", tree.toString())
            .put("entries", array).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STORE_BYTES) { "Library cache is too large" }
        val atomic = AtomicFile(store)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }

    companion object {
        private const val MEDIA_FORMATS_VERSION = 3
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ZIP_ENTRIES = 50_000
        private const val MAX_ZIP_IMAGES = 1_000
        private const val MAX_ARCHIVE_BYTES = 8L * 1024 * 1024 * 1024
        private const val MAX_EXPANSION_RATIO = 1000L
        private const val MAX_STORE_BYTES = 16L * 1024 * 1024
        private val LOCATION_ID = Regex("[0-9a-f]{32}")
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
