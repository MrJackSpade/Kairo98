package com.mrjackspade.kairo98

import java.io.File
import java.io.RandomAccessFile

/** Checks the specific FONT.BMP layout accepted by the 21/W PC-98 font loader. */
internal object Pc98FontBitmap {
    const val MAX_BYTES = 1024 * 1024L
    private const val PIXEL_BYTES = 2048 * 2048 / 8L

    fun validate(file: File) {
        require(file.length() in 54L..MAX_BYTES) { "FONT.BMP must be a 2048x2048 monochrome BMP" }
        val header = ByteArray(54)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        fun word(offset: Int) = (header[offset].toInt() and 0xff) or
            ((header[offset + 1].toInt() and 0xff) shl 8)
        fun dword(offset: Int) = word(offset).toLong() or (word(offset + 2).toLong() shl 16)
        val pixelOffset = dword(10)
        require(header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte() &&
            dword(14) >= 40 && dword(18) == 2048L && dword(22) == 2048L &&
            word(26) == 1 && word(28) == 1 && dword(30) == 0L &&
            dword(34) == PIXEL_BYTES && pixelOffset >= 54 &&
            pixelOffset + PIXEL_BYTES <= file.length()) {
            "FONT.BMP must be a 2048x2048 monochrome BMP"
        }
    }
}
