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
import com.mrjackspade.kairo.uitest.suites.MenuTests
import com.mrjackspade.kairo.uitest.suites.ControllerTests
import com.mrjackspade.kairo.uitest.suites.TouchTests
import com.mrjackspade.kairo.uitest.suites.LaunchTests
import com.mrjackspade.kairo.uitest.suites.LibraryManagementTests
import com.mrjackspade.kairo.uitest.suites.MediaTests
import com.mrjackspade.kairo.uitest.suites.SettingsTests
import com.mrjackspade.kairo.uitest.suites.StateTests
import org.junit.Test

/**
 * Kairo98 as the shared UI tests see it. Its test resources are two translated games, Acrojet and
 * The Black Onyx, and no BIOS: Kairo98's built-in BIOS and font start them.
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
    // PC-98 scan codes: the cursor keys, Return and Space (ControllerBindings).
    override val arrowKeys = listOf(0x3a, 0x3d, 0x3b, 0x3c)
    override val returnKey = 0x1c
    override val spaceKey = 0x34
    override val bootGameFile = "games/Acrojet [T-En by MrRichard999 v1.0].zip"
    override val otherGame = "The Black Onyx"
    // The Disks page's Floppy A takes The Black Onyx's disk from Other while Acrojet runs.
    override val mediaSwap = MediaSwap("Acrojet", "Floppy A", "Black Onyx", filler = "d88", page = "f-disks")
}

class Kairo98FirstRunTests : FirstRunTests() { override val app = Kairo98App }
class Kairo98LibraryTests : LibraryTests() { override val app = Kairo98App }
class Kairo98SearchTests : SearchTests() { override val app = Kairo98App }
class Kairo98GameTests : GameTests() { override val app = Kairo98App }
class Kairo98WordingTests : WordingTests() { override val app = Kairo98App }
class Kairo98MenuTests : MenuTests() { override val app = Kairo98App }
class Kairo98ControllerTests : ControllerTests() { override val app = Kairo98App }
class Kairo98TouchTests : TouchTests() { override val app = Kairo98App }
class Kairo98LaunchTests : LaunchTests() { override val app = Kairo98App }
class Kairo98LibraryManagementTests : LibraryManagementTests() { override val app = Kairo98App }
class Kairo98SettingsTests : SettingsTests() { override val app = Kairo98App }
class Kairo98StateTests : StateTests() { override val app = Kairo98App }
class Kairo98MediaTests : MediaTests() { override val app = Kairo98App }

/** The PC-98's own behavior: games start on the built-in BIOS and font. */
class Pc98Tests : KairoUiTest() {
    override val app = Kairo98App

    private fun openDisks(): com.mrjackspade.kairo.uitest.Element {
        screens.completeFirstRun().open(Kairo98App.bootGame).play()
        driver.waitFrames(120)
        screens.openGameMenu().find(By.item("f-disks")).tap()
        return driver.page("Disks").waitUntilShown()
    }

    @Test
    fun theDisksPageShowsWhatIsInEachDrive() {
        val disks = openDisks()
        val probe = driver.probe()
        val floppy = disks.find(By.item("floppy0")).find(By.id("value")).text()
        expect.equal("Floppy A's row", probe.details["media.Floppy A"].orEmpty().ifEmpty { "Empty" }, floppy)
    }

    @Test
    fun ejectingAFloppyEmptiesTheDrive() {
        val disks = openDisks()
        screens.choose(disks, "floppy0", "empty")
        driver.waitUntil("floppy A to be empty") { driver.probeNow().details["media.Floppy A"].orEmpty().isEmpty() }
        expect.equal("Floppy A's row after ejecting", "Empty", disks.find(By.item("floppy0")).find(By.id("value")).text())
    }

    @Test
    fun theBuiltInFontStandsInUntilOneIsImported() {
        screens.completeFirstRun()
        val machine = screens.openMachinePage()
        expect.equal("the font row with none imported", "Built-in", screens.biosRow(machine, "font.bmp").find(By.id("value")).text())
    }
}
