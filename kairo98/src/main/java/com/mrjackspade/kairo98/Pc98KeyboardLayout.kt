package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.GuestKeyboardLayout
import com.mrjackspade.kairo.frontend.GuestKeyboardPage
import com.mrjackspade.kairo.frontend.KeyboardKey

/** PC-98 scan codes and legends supplied to the shared keyboard panel. */
internal object Pc98KeyboardLayout {
    val value = GuestKeyboardLayout(
        brand = "KAIRO98",
        pages = listOf(
            GuestKeyboardPage("ABC", alphabetRows()),
            GuestKeyboardPage("?123", symbolRows()),
            GuestKeyboardPage("PC-98", pc98Rows())
        ),
        modifiers = setOf(0x70, 0x7d, 0x71, 0x72, 0x73, 0x74),
        shiftCodes = setOf(0x70, 0x7d),
        capsCode = 0x71,
        chordShiftCode = 0x70
    )
    private fun alphabetRows(): List<List<KeyboardKey>> = listOf(
        listOf(KeyboardKey("Esc", 0x00, 1.3f)) +
            listOf("!", "\"", "#", "$", "%", "&", "'", "(", ")", "0")
                .mapIndexed { index, shifted -> KeyboardKey("${(index + 1) % 10}", index + 1, shifted = shifted) } +
            KeyboardKey("Back", 0x0e, 1.5f),
        listOf(KeyboardKey("Tab", 0x0f, 1.3f)) +
            "qwertyuiop".mapIndexed { index, char -> KeyboardKey("$char", 0x10 + index) } +
            listOf(KeyboardKey("@", 0x1a, shifted = "~"), KeyboardKey("[", 0x1b, shifted = "{"),
                KeyboardKey("Enter", 0x1c, 1.5f)),
        listOf(KeyboardKey("Caps", 0x71, 1.4f)) +
            "asdfghjkl".mapIndexed { index, char -> KeyboardKey("$char", 0x1d + index) } +
            listOf(KeyboardKey(";", 0x26, shifted = "+"), KeyboardKey(":", 0x27, shifted = "*"),
                KeyboardKey("]", 0x28, shifted = "}")),
        listOf(KeyboardKey("Shift", 0x70, 1.5f)) +
            "zxcvbnm".mapIndexed { index, char -> KeyboardKey("$char", 0x29 + index) } +
            listOf(KeyboardKey(",", 0x30, shifted = "<"), KeyboardKey(".", 0x31, shifted = ">"),
                KeyboardKey("/", 0x32, shifted = "?"), KeyboardKey("Shift", 0x7d, 1.5f)),
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
