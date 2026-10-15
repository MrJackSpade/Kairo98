package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.GuestKeyboardLayout
import com.mrjackspade.kairo.frontend.GuestKeyboardPage
import com.mrjackspade.kairo.frontend.KeyboardKey

/** PC-98 scan codes and legends supplied to the shared keyboard panel. */
internal object Pc98KeyboardLayout {
    /**
     * JIS kana legends by scan code: the kana, then its Shift form where the key has one.
     * Declared before [value], whose rows read it while the object initializes.
     */
    private val kana = mapOf(
        0x01 to ("ヌ" to null), 0x02 to ("フ" to null), 0x03 to ("ア" to "ァ"), 0x04 to ("ウ" to "ゥ"),
        0x05 to ("エ" to "ェ"), 0x06 to ("オ" to "ォ"), 0x07 to ("ヤ" to "ャ"), 0x08 to ("ユ" to "ュ"),
        0x09 to ("ヨ" to "ョ"), 0x0a to ("ワ" to "ヲ"), 0x0b to ("ホ" to null), 0x0c to ("ヘ" to null),
        0x0d to ("ー" to null),
        0x10 to ("タ" to null), 0x11 to ("テ" to null), 0x12 to ("イ" to "ィ"), 0x13 to ("ス" to null),
        0x14 to ("カ" to null), 0x15 to ("ン" to null), 0x16 to ("ナ" to null), 0x17 to ("ニ" to null),
        0x18 to ("ラ" to null), 0x19 to ("セ" to null), 0x1a to ("゛" to null), 0x1b to ("゜" to "「"),
        0x1d to ("チ" to null), 0x1e to ("ト" to null), 0x1f to ("シ" to null), 0x20 to ("ハ" to null),
        0x21 to ("キ" to null), 0x22 to ("ク" to null), 0x23 to ("マ" to null), 0x24 to ("ノ" to null),
        0x25 to ("リ" to null), 0x26 to ("レ" to null), 0x27 to ("ケ" to null), 0x28 to ("ム" to "」"),
        0x29 to ("ツ" to "ッ"), 0x2a to ("サ" to null), 0x2b to ("ソ" to null), 0x2c to ("ヒ" to null),
        0x2d to ("コ" to null), 0x2e to ("ミ" to null), 0x2f to ("モ" to null), 0x30 to ("ネ" to "、"),
        0x31 to ("ル" to "。"), 0x32 to ("メ" to "・"), 0x33 to ("ロ" to null))

    val value = GuestKeyboardLayout(
        brand = "KAIRO98",
        pages = listOf(
            GuestKeyboardPage("ABC", alphabetRows()),
            GuestKeyboardPage("?123", symbolRows(), pickerLabel = "Symbols"),
            GuestKeyboardPage("PC-98", pc98Rows())
        ),
        modifiers = setOf(0x70, 0x7d, 0x71, 0x72, 0x73, 0x74),
        shiftCodes = setOf(0x70, 0x7d),
        capsCode = 0x71,
        chordShiftCode = 0x70,
        kanaCode = 0x72
    )

    /** An alphabet key with its kana legends. */
    private fun key(label: String, code: Int, width: Float = 1f, shifted: String? = null,
                    chordShift: Boolean = false) = KeyboardKey(label, code, width, shifted, chordShift,
        kana[code]?.first, kana[code]?.second)

    // The alphabet page follows the PC-98 rows, so every kana legend has its key.
    private fun alphabetRows(): List<List<KeyboardKey>> = listOf(
        listOf(KeyboardKey("Esc", 0x00, 1.3f)) +
            listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "0")
                .mapIndexed { index, shifted -> key("${(index + 1) % 10}", index + 1, shifted = shifted) } +
            listOf(key("-", 0x0b, shifted = "="), key("^", 0x0c, shifted = "`"), key("\\", 0x0d, shifted = "|"),
                KeyboardKey("Back", 0x0e, 1.5f)),
        listOf(KeyboardKey("Tab", 0x0f, 1.3f)) +
            "qwertyuiop".mapIndexed { index, char -> key("$char", 0x10 + index) } +
            listOf(key("@", 0x1a, shifted = "~"), key("[", 0x1b, shifted = "{"),
                KeyboardKey("Enter", 0x1c, 1.5f)),
        listOf(KeyboardKey("Caps", 0x71, 1.4f)) +
            "asdfghjkl".mapIndexed { index, char -> key("$char", 0x1d + index) } +
            listOf(key(";", 0x26, shifted = "+"), key(":", 0x27, shifted = "*"),
                key("]", 0x28, shifted = "}")),
        listOf(KeyboardKey("Shift", 0x70, 1.5f)) +
            "zxcvbnm".mapIndexed { index, char -> key("$char", 0x29 + index) } +
            listOf(key(",", 0x30, shifted = "<"), key(".", 0x31, shifted = ">"),
                key("/", 0x32, shifted = "?"), key("_", 0x33, chordShift = true),
                KeyboardKey("Shift", 0x7d, 1.5f)),
        listOf(KeyboardKey("Ctrl", 0x74), KeyboardKey("Graph", 0x73), KeyboardKey("Nfer", 0x51),
            KeyboardKey("Xfer", 0x35), KeyboardKey("Space", 0x34, 4f), KeyboardKey("Kana", 0x72),
            KeyboardKey("Ins", 0x38), KeyboardKey("Del", 0x39))
    )

    private fun symbolRows(): List<List<KeyboardKey>> = listOf(
        listOf(KeyboardKey("Esc", 0x00, 1.3f)) +
            (1..9).map { KeyboardKey("$it", it) } + KeyboardKey("0", 0x0a) + KeyboardKey("Back", 0x0e, 1.5f),
        listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")")
            .mapIndexed { index, label -> KeyboardKey(label, index + 1, chordShift = true) } +
            KeyboardKey("Enter", 0x1c, 1.5f),
        listOf(KeyboardKey("-", 0x0b), KeyboardKey("^", 0x0c), KeyboardKey("\\", 0x0d),
            KeyboardKey("@", 0x1a), KeyboardKey("[", 0x1b), KeyboardKey("]", 0x28), KeyboardKey(";", 0x26),
            KeyboardKey(":", 0x27), KeyboardKey(",", 0x30), KeyboardKey(".", 0x31), KeyboardKey("/", 0x32)),
        listOf(KeyboardKey("=", 0x0b, chordShift = true), KeyboardKey("`", 0x0c, chordShift = true),
            KeyboardKey("|", 0x0d, chordShift = true), KeyboardKey("~", 0x1a, chordShift = true),
            KeyboardKey("{", 0x1b, chordShift = true), KeyboardKey("}", 0x28, chordShift = true),
            KeyboardKey("+", 0x26, chordShift = true), KeyboardKey("*", 0x27, chordShift = true),
            KeyboardKey("<", 0x30, chordShift = true), KeyboardKey(">", 0x31, chordShift = true),
            KeyboardKey("?", 0x32, chordShift = true), KeyboardKey("_", 0x33, chordShift = true)),
        listOf(KeyboardKey("Tab", 0x0f), KeyboardKey("Space", 0x34, 5f), KeyboardKey("Enter", 0x1c, 1.5f),
            KeyboardKey("Ins", 0x38), KeyboardKey("Del", 0x39))
    )

    private fun pc98Rows(): List<List<KeyboardKey>> = listOf(
        listOf(KeyboardKey("Stop", 0x60, 1.4f), KeyboardKey("Copy", 0x61, 1.4f)) +
            (0..9).map { KeyboardKey("F${it + 1}", 0x62 + it) },
        (0..4).map { KeyboardKey("VF${it + 1}", 0x52 + it) } +
            listOf(KeyboardKey("Home", 0x3e), KeyboardKey("Help", 0x3f), KeyboardKey("Roll↑", 0x36),
                KeyboardKey("Roll↓", 0x37), KeyboardKey("Ins", 0x38), KeyboardKey("Del", 0x39)),
        listOf(KeyboardKey("7", 0x42), KeyboardKey("8", 0x43), KeyboardKey("9", 0x44),
            KeyboardKey("/", 0x41), KeyboardKey("*", 0x45), KeyboardKey("-", 0x40)),
        listOf(KeyboardKey("4", 0x46), KeyboardKey("5", 0x47), KeyboardKey("6", 0x48),
            KeyboardKey("+", 0x49), KeyboardKey("=", 0x4d), KeyboardKey("↑", 0x3a)),
        listOf(KeyboardKey("1", 0x4a), KeyboardKey("2", 0x4b), KeyboardKey("3", 0x4c),
            KeyboardKey("0", 0x4e), KeyboardKey(",", 0x4f), KeyboardKey(".", 0x50),
            KeyboardKey("←", 0x3b), KeyboardKey("↓", 0x3d), KeyboardKey("→", 0x3c))
    )

}
