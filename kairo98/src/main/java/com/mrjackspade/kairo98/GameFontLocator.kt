package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.DiskImageEntry
import com.mrjackspade.kairo.frontend.DiskImageLibrary
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the FONT.BMP nearest a library game, as translated releases ship one beside the disk.
 * The game's own ZIP folders come first, then its library folders up to the library root.
 */
internal class GameFontLocator(private val resolver: ContentResolver, private val target: File) {

    /**
     * Copies and validates the nearest FONT.BMP into [target] and returns it, or null when
     * the game has none. [tree] is null for a game opened outside the library folder, and
     * [library] reads the game's archive in place when it came from one.
     */
    fun prepare(entry: DiskImageEntry, tree: Uri?, library: DiskImageLibrary?, cancelled: AtomicBoolean): File? {
        val member = entry.zipEntry
        if (library != null && member != null)
            library.readArchive(entry) { archive -> fromArchive(archive, member) }?.let { return it }
        return tree?.let { fromTree(it, entry.path, cancelled) }
    }

    private fun fromArchive(archive: DiskImageLibrary.ArchiveFiles, imageName: String): File? {
        val fonts = archive.files.entries.groupBy { it.key.replace('\\', '/').lowercase(Locale.ROOT) }
        val folders = imageName.replace('\\', '/').split('/').dropLast(1)
        for (depth in folders.size downTo 0) {
            val path = (folders.take(depth) + FONT_NAME).joinToString("/").lowercase(Locale.ROOT)
            val matches = fonts[path] ?: continue
            require(matches.size == 1) { "Archive has more than one $path" }
            val (name, size) = matches.single()
            require(size in 1..Pc98FontBitmap.MAX_BYTES) { "Game $name is not a valid FONT.BMP" }
            return copy(name) { archive.open(name) }
        }
        return null
    }

    private fun fromTree(tree: Uri, gamePath: String, cancelled: AtomicBoolean): File? {
        val folders = gamePath.split('/').dropLast(1)
        var parentId = DocumentsContract.getTreeDocumentId(tree)
        var nearest: Pair<Uri, String>? = null
        for (depth in 0..folders.size) {
            if (cancelled.get()) throw CancellationException("Cancelled")
            val prefix = folders.take(depth).joinToString("/")
            val children = list(tree, parentId)
            val fonts = children.filter { !it.directory && it.name.equals(FONT_NAME, ignoreCase = true) }
            require(fonts.size <= 1) { "More than one FONT.BMP in ${prefix.ifEmpty { "the games folder" }}" }
            fonts.singleOrNull()?.let {
                nearest = DocumentsContract.buildDocumentUriUsingTree(tree, it.id) to
                    (if (prefix.isEmpty()) it.name else "$prefix/${it.name}")
            }
            if (depth == folders.size) break
            val next = children.filter { it.directory && it.name == folders[depth] }
            require(next.size == 1) { "Game folder ${folders.take(depth + 1).joinToString("/")} " +
                "is missing or ambiguous; refresh the library" }
            parentId = next.single().id
        }
        return nearest?.let { (uri, path) ->
            copy(path) { requireNotNull(resolver.openInputStream(uri)) { "Unable to open game $path" } }
        }
    }

    private data class Child(val id: String, val name: String, val directory: Boolean)

    private fun list(tree: Uri, parentId: String): List<Child> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return requireNotNull(resolver.query(children, PROJECTION, null, null, null)) {
            "Unable to read game folder"
        }.use { cursor ->
            val result = ArrayList<Child>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                result.add(Child(id, name, cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
            }
            result
        }
    }

    /** Copies the font through a partial file so a rejected font never replaces the last good one. */
    private fun copy(label: String, open: () -> InputStream): File {
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            open().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= Pc98FontBitmap.MAX_BYTES) { "Game $label is too large" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            try {
                Pc98FontBitmap.validate(partial)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Game $label: ${error.message}", error)
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally {
            partial.delete()
        }
    }

    companion object {
        private const val FONT_NAME = "font.bmp"
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
    }
}
