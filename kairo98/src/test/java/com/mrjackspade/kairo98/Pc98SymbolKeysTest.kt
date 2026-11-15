package com.mrjackspade.kairo98

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A hardware keyboard's symbols reach the PC-98 as the characters typed on its JIS layout, not as
 * the key in the same place (Kairo#136): US Shift+; is :, which the PC-98 types on its own : key.
 */
class Pc98SymbolKeysTest {
    private val layout = Pc98KeyboardLayout.value
    private val shift = 0x70

    @Test fun colonIsTheColonKeyWithoutShift() {
        assertEquals(listOf(0x27), layout.symbolKeys(':', emptySet()))
    }

    @Test fun starIsShiftAndTheColonKey() {
        assertEquals(listOf(shift, 0x27), layout.symbolKeys('*', emptySet()))
    }

    @Test fun plusIsShiftAndTheSemicolonKey() {
        assertEquals(listOf(shift, 0x26), layout.symbolKeys('+', emptySet()))
    }

    @Test fun lettersKeepTheirPlace() {
        assertNull(layout.symbolKeys('c', emptySet()))
    }
}
