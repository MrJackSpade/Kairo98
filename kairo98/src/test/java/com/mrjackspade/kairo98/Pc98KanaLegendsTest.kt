package com.mrjackspade.kairo98

import com.mrjackspade.kairo.KanaLegendsTest

/** The PC-98 keyboard's kana legends (Kairo#126). */
class Pc98KanaLegendsTest : KanaLegendsTest() {
    override val layouts = listOf(Pc98KeyboardLayout.value)
}
