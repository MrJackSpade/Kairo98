package com.mrjackspade.kairo98

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Generates the PC-98 JIS glyph overlay from fonts installed on this device. */
internal object Pc98FontCache {
    private val shiftJis = Charset.forName("Shift_JIS")
    private val magic = byteArrayOf(75, 57, 56, 70, 78, 84, 49, 0)

    @Synchronized fun ensure(directory: File) {
        val cache = File(directory, "android-font.bin")
        if (cache.isFile && cache.length() >= 46L) return
        val partial = File(directory, "android-font.bin.part")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 16f
        }
        val bounds = Rect()
        val pixels = IntArray(16 * 16)
        val records = ArrayList<ByteArray>(7600)
        try {
            fun addGlyph(row: Int, cell: Int, character: String, width: Int) {
                if (!paint.hasGlyph(character)) return
                paint.getTextBounds(character, 0, character.length, bounds)
                if (bounds.isEmpty) return
                bitmap.eraseColor(Color.WHITE)
                canvas.save()
                val scale = minOf(1f, width.toFloat() / bounds.width(), 16f / bounds.height())
                canvas.translate((width - bounds.width() * scale) / 2f, (16 - bounds.height() * scale) / 2f)
                canvas.scale(scale, scale)
                canvas.drawText(character, -bounds.left.toFloat(), -bounds.top.toFloat(), paint)
                canvas.restore()
                bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16)
                val record = ByteArray(34)
                record[0] = row.toByte()
                record[1] = cell.toByte()
                var nonblank = false
                for (y in 0 until 16) {
                    for (x in 0 until width) {
                        if ((pixels[y * 16 + x] and 0xffffff) != 0xffffff) {
                            val slot = 2 + y * 2 + x / 8
                            record[slot] = (record[slot].toInt() or (0x80 ushr (x % 8))).toByte()
                            nonblank = true
                        }
                    }
                }
                if (nonblank) records.add(record)
            }
            for (row in 0x21..0x7e) {
                for (cell in 0x21..0x7e) {
                    if (row == 0x29) continue
                    val character = unicode(row, cell) ?: continue
                    addGlyph(row, cell, character, if (row == 0x29 || row == 0x2a) 8 else 16)
                }
            }
            for (code in 0xa1..0xdf) {
                addGlyph(0, code, byteArrayOf(code.toByte()).toString(shiftJis), 8)
            }
            DataOutputStream(partial.outputStream().buffered()).use { output ->
                output.write(magic)
                output.writeInt(records.size)
                records.forEach(output::write)
            }
            Files.move(partial.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            bitmap.recycle()
            partial.delete()
        }
    }

    private fun unicode(row: Int, cell: Int): String? {
        if (row == 0x2a && cell <= 0x5f) {
            return byteArrayOf((0xa1 + cell - 0x21).toByte()).toString(shiftJis)
        }
        val lead = (row + 1) / 2 + if (row <= 0x5e) 0x70 else 0xb0
        var trail = if (row % 2 == 1) cell + 0x1f else cell + 0x7e
        if (row % 2 == 1 && trail >= 0x7f) trail++
        val value = byteArrayOf(lead.toByte(), trail.toByte()).toString(shiftJis)
        return value.takeIf { it.length == 1 && it[0] != '\ufffd' }
    }
}
