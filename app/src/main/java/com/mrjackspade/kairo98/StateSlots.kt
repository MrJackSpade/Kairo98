package com.mrjackspade.kairo98

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Save-state slots for one game, keyed by content ID so a renamed or moved file keeps its states.
 * Each slot directory holds the core's state file, copies of the mounted disk images, and a
 * thumbnail. A save is written to a scratch directory first and swapped in only when complete.
 */
class StateSlots(root: File, contentId: String) {
    data class Slot(val index: Int, val savedAt: Long?, val directory: File) {
        val empty: Boolean get() = savedAt == null
    }

    private val base = File(root, contentId.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    fun slots(): List<Slot> = (1..SLOT_COUNT).map { index ->
        val directory = slotDirectory(index)
        val state = File(directory, STATE_FILE)
        Slot(index, state.takeIf { it.isFile }?.lastModified(), directory)
    }

    fun slot(index: Int): Slot = slots()[index - 1]

    /** Returns an empty scratch directory for a save into [index]. */
    fun beginSave(index: Int): File {
        val scratch = File(base, "slot$index.part")
        scratch.deleteRecursively()
        check(scratch.mkdirs()) { "Could not create save-state storage" }
        return scratch
    }

    fun commitSave(index: Int, scratch: File, thumbnail: Bitmap?) {
        thumbnail?.let { bitmap ->
            File(scratch, THUMBNAIL_FILE).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val target = slotDirectory(index)
        val previous = File(base, "slot$index.old")
        previous.deleteRecursively()
        if (target.exists() && !target.renameTo(previous)) error("Could not replace the old save")
        if (!scratch.renameTo(target)) {
            previous.renameTo(target)
            error("Could not store the save")
        }
        previous.deleteRecursively()
    }

    fun abandonSave(scratch: File) { scratch.deleteRecursively() }

    fun thumbnail(slot: Slot): Bitmap? = File(slot.directory, THUMBNAIL_FILE)
        .takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(index: Int) { slotDirectory(index).deleteRecursively() }

    private fun slotDirectory(index: Int) = File(base, "slot$index")

    companion object {
        const val SLOT_COUNT = 4
        private const val STATE_FILE = "state.np2"
        private const val THUMBNAIL_FILE = "thumbnail.png"
    }
}
