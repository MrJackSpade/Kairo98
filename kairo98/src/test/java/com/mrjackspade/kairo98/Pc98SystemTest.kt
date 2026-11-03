package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.MachineOptionValues
import com.mrjackspade.kairo.frontend.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A game's System, the built-in BIOS or the player's (Kairo98#107, Kairo#112): the default follows
 * the player's imported bios.rom, a game's own choice wins, and a game on the player's BIOS without
 * one fails to start, naming the file, rather than running the built-in one.
 */
class Pc98SystemTest {
    @get:Rule val folder = TemporaryFolder()
    private val firmware get() = folder.root

    private fun system(own: String? = null, stored: String? = null) =
        MachineOptionValues.values(listOf(Pc98Catalog.system), { own }, { stored }) { _, _ -> Pc98Firmware.ownBiosInstalled(firmware) }
            .getValue(Pc98Catalog.system.id)

    private fun importBios() = File(firmware, "bios.rom").writeBytes(ByteArray(0x18000))

    @Test
    fun theDefaultIsThePlayersBiosOnceImported() {
        assertEquals("before an import", Pc98Catalog.BUILT_IN_BIOS, system())
        importBios()
        assertEquals("after an import", Pc98Catalog.OWN_BIOS, system())
    }

    @Test
    fun anAllGamesChoiceWinsOverTheImportedBios() {
        importBios()
        assertEquals(Pc98Catalog.BUILT_IN_BIOS, system(stored = Pc98Catalog.BUILT_IN_BIOS))
    }

    @Test
    fun aGamesOwnChoiceWinsOverTheAllGamesChoice() {
        assertEquals(Pc98Catalog.OWN_BIOS, system(own = Pc98Catalog.OWN_BIOS, stored = Pc98Catalog.BUILT_IN_BIOS))
        importBios()
        assertEquals(Pc98Catalog.BUILT_IN_BIOS, system(own = Pc98Catalog.BUILT_IN_BIOS, stored = Pc98Catalog.OWN_BIOS))
    }

    @Test
    fun aStartOnThePlayersBiosWithoutItFailsNamingTheFile() {
        assertEquals(UiText.res(R.string.own_bios_missing), Pc98Firmware.missingForStart(Pc98Catalog.OWN_BIOS, firmware))
        val message = Regex("""<string name="own_bios_missing">([^<]*)</string>""")
            .find(File("src/main/res/values/strings.xml").readText())!!.groupValues[1]
        assertEquals("the message names the file: $message", true, "bios.rom" in message)
        assertNull("the built-in BIOS needs nothing", Pc98Firmware.missingForStart(Pc98Catalog.BUILT_IN_BIOS, firmware))
        importBios()
        assertNull("with bios.rom imported", Pc98Firmware.missingForStart(Pc98Catalog.OWN_BIOS, firmware))
    }
}
