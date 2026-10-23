package com.mrjackspade.kairo98.uitest

import com.mrjackspade.kairo.uitest.AppUnderTest
import com.mrjackspade.kairo.uitest.AppUnderTest.BiosFile
import com.mrjackspade.kairo.uitest.By
import com.mrjackspade.kairo.uitest.KairoUiTest
import com.mrjackspade.kairo.uitest.suites.FirstRunTests
import com.mrjackspade.kairo.uitest.suites.GameTests
import com.mrjackspade.kairo.uitest.suites.LibraryTests
import com.mrjackspade.kairo.uitest.suites.SearchTests
import com.mrjackspade.kairo.uitest.suites.WordingTests
import org.junit.Test

/**
 * Kairo98 as the shared UI tests see it. Its test resources are two translated games, Acrojet and
 * Alice's Cottage II, and no BIOS: Kairo98's built-in BIOS and font start them.
 */
object Kairo98App : AppUnderTest() {
    override val name = "Kairo98"
    override val brand = "KAIRO98"
    override val biosTitle = "PC-98 BIOS"
    override val biosFiles = listOf(BiosFile("bios.rom", false), BiosFile("font.bmp", false),
        BiosFile("ym2608_adpcm_rom.bin", false))
    override val biosResource: String? = null
    override val bootGame = "Acrojet"
    override val searchTerm = "Acrojet"
}

class Kairo98FirstRunTests : FirstRunTests() { override val app = Kairo98App }
class Kairo98LibraryTests : LibraryTests() { override val app = Kairo98App }
class Kairo98SearchTests : SearchTests() { override val app = Kairo98App }
class Kairo98GameTests : GameTests() { override val app = Kairo98App }
class Kairo98WordingTests : WordingTests() { override val app = Kairo98App }

/** The PC-98's own behavior: games start on the built-in BIOS and font. */
class Pc98Tests : KairoUiTest() {
    override val app = Kairo98App

    @Test
    fun theBuiltInFontStandsInUntilOneIsImported() {
        screens.completeFirstRun()
        val machine = screens.openMachinePage()
        expect.equal("the font row with none imported", "Built-in", screens.biosRow(machine, "font.bmp").find(By.id("value")).text())
    }
}
