package com.mrjackspade.kairo98

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

/** A source location is separate from the uncompressed HDI content ID. */
data class LibraryEntry(
    val id: String,
    val uri: String,
    val path: String,
    val zipEntry: String?,
    val sourceSize: Long,
    val sourceModified: Long,
    val imageSize: Long,
    val imageCrc: Long,
    val contentId: String?,
    val error: String? = null
) {
    val displayName: String get() = (zipEntry ?: path).substringAfterLast('/').substringAfterLast('\\')
    val playable: Boolean get() = contentId != null && error == null
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

    fun scan(treeUri: Uri, forceHash: Boolean, cancelled: AtomicBoolean,
             progress: (String) -> Unit): List<LibraryEntry> = synchronized(scanLock) {
        checkCancelled(cancelled)
        val prior = cached(treeUri).associateBy { it.id }
        val result = ArrayList<LibraryEntry>()
        val (files, folderErrors) = enumerate(treeUri, cancelled, progress)
        result.addAll(folderErrors)
        hashCount = 0
        for ((index, source) in files.withIndex()) {
            checkCancelled(cancelled)
            progress("Scanning ${index + 1}/${files.size}: ${source.path}")
            if (source.path.endsWith(".zip", ignoreCase = true)) {
                val oldEntries = prior.values.filter { it.uri == source.uri.toString() }
                if (!forceHash && trusted(source) && oldEntries.isNotEmpty() &&
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
                            if (!item.isDirectory && normalized.endsWith(".hdi", ignoreCase = true)) {
                                require(playable.size < MAX_ZIP_IMAGES) { "ZIP has too many HDIs" }
                                require(item.size in 1..MAX_IMAGE_BYTES) { "HDI exceeds size limit" }
                                require(item.compressedSize > 0 &&
                                    item.size / item.compressedSize <= MAX_EXPANSION_RATIO) {
                                    "HDI expansion ratio is too large"
                                }
                                playable.add(item)
                            }
                        }
                        require(playable.isNotEmpty()) { "No HDI in ZIP" }
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
                                    zip.getInputStream(item).use { contentId(it, item.size, item.crc, cancelled) }
                                }
                                result.add(candidate.copy(contentId = id))
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) {
                                result.add(candidate.copy(error = error.message ?: "HDI unreadable"))
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
                            contentId(it, source.size, -1, cancelled)
                        } ?: error("Unable to open HDI")
                    }
                    result.add(candidate.copy(contentId = id))
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    result.add(candidate.copy(error = error.message ?: "HDI unreadable"))
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
        val file = File(imageStore, "${entry.id}-${entry.contentId!!.substringAfter(':')}.hdi")
        if (file.isFile && file.length() > 0) return file
        if (entry.imageSize > 0) require(imageStore.usableSpace > entry.imageSize + 16L * 1024 * 1024) {
            "Not enough free space for this HDI"
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
                        require(contentId(input, entry.imageSize, -1, cancelled, output) == expected) {
                            "HDI changed since scan; refresh the library"
                        }
                    }
                } ?: error("Unable to open HDI")
            } else {
                val archive = cachedZip(source, false, cancelled)
                ZipFile(archive).use { zip ->
                    val image = zip.getEntry(entry.zipEntry) ?: error("HDI missing from ZIP")
                    require(safeEntryName(image.name) == safeEntryName(entry.zipEntry))
                    require(image.size in 1..MAX_IMAGE_BYTES && image.compressedSize > 0 &&
                        image.size / image.compressedSize <= MAX_EXPANSION_RATIO) {
                        "HDI expansion ratio is too large"
                    }
                    progress("Preparing ${entry.displayName}")
                    zip.getInputStream(image).use { input ->
                        partial.outputStream().use { output ->
                            require(contentId(input, image.size, image.crc, cancelled, output) == expected) {
                                "HDI changed since scan; refresh the library"
                            }
                        }
                    }
                }
            }
            require(partial.renameTo(file)) { "Unable to save working HDI" }
            return file
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    private data class Source(val uri: Uri, val path: String, val size: Long, val modified: Long)

    private fun enumerate(tree: Uri, cancelled: AtomicBoolean,
                          progress: (String) -> Unit): Pair<List<Source>, List<LibraryEntry>> {
        val result = ArrayList<Source>()
        val errors = ArrayList<LibraryEntry>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootId to "")
        var visited = 0
        while (queue.isNotEmpty()) {
            checkCancelled(cancelled)
            val (parentId, parentPath) = queue.removeFirst()
            require(parentPath.count { it == '/' } <= MAX_DEPTH) { "ROM folder is too deep" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            try { context.contentResolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val timeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    checkCancelled(cancelled)
                    require(++visited <= MAX_DOCUMENTS) { "ROM folder has too many files" }
                    val id = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn) ?: continue
                    val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    if (cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(id to path)
                    } else if (name.endsWith(".hdi", true) || name.endsWith(".zip", true)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val size = if (cursor.isNull(sizeColumn)) -1 else cursor.getLong(sizeColumn)
                        val modified = if (cursor.isNull(timeColumn)) 0 else cursor.getLong(timeColumn)
                        result.add(Source(uri, path, size, modified))
                        if (result.size % 20 == 0) progress("Found ${result.size} media files")
                    }
                }
            } ?: error("Unable to read ROM folder")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (parentPath.isEmpty()) throw error
                val folder = Source(DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
                    parentPath, -1, 0)
                errors.add(entry(folder, null, -1, -1, null,
                    "Unreadable folder: ${error.message ?: "provider error"}"))
                progress("Skipping unreadable folder: $parentPath")
            }
        }
        return result to errors
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
                          cancelled: AtomicBoolean, output: OutputStream? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            checkCancelled(cancelled)
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= MAX_IMAGE_BYTES) { "HDI exceeds size limit" }
            digest.update(buffer, 0, read)
            crc.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        require(count > 0) { "Empty HDI" }
        if (expectedSize >= 0) require(count == expectedSize) { "HDI size changed during read" }
        if (expectedCrc >= 0) require(crc.value == expectedCrc) { "ZIP entry checksum mismatch" }
        return "sha256-hdi-v1:${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

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

    private fun readStore(): Pair<String?, List<LibraryEntry>> = try {
        if (!store.isFile || store.length() > MAX_STORE_BYTES) null to emptyList()
        else {
            val json = JSONObject(AtomicFile(store).readFully().toString(Charsets.UTF_8))
            if (json.optInt("schemaVersion") != 1) null to emptyList()
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
                json.optString("treeUri") to items
            }
        }
    } catch (_: Exception) { null to emptyList() }

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
        val bytes = JSONObject().put("schemaVersion", 1).put("treeUri", tree.toString())
            .put("entries", array).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STORE_BYTES) { "Library cache is too large" }
        val atomic = AtomicFile(store)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }

    companion object {
        private const val MAX_DEPTH = 24
        private const val MAX_DOCUMENTS = 50_000
        private const val MAX_ZIP_ENTRIES = 50_000
        private const val MAX_ZIP_IMAGES = 1_000
        private const val MAX_IMAGE_BYTES = 4L * 1024 * 1024 * 1024
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
