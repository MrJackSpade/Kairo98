package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.ButtonTone
import com.mrjackspade.kairo.frontend.FooterButton
import com.mrjackspade.kairo.frontend.PageStack
import com.mrjackspade.kairo.frontend.RowGroup
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.SetupPage
import com.mrjackspade.kairo.frontend.TrayButton
import com.mrjackspade.kairo.frontend.TraySpec
import com.mrjackspade.kairo.frontend.ValueTone

/** PC-98 setup steps on the shared setup pages: the ROM folder, then optional firmware. */
internal class FirstRunSetup(
    private val pages: PageStack,
    private val selectRomFolder: () -> Unit,
    private val skipRomFolder: (Boolean) -> Unit,
    private val selectBios: () -> Unit,
    private val selectFont: () -> Unit,
    private val selectRhythm: () -> Unit,
    private val finish: () -> Unit,
    private val hasBios: () -> Boolean,
    private val hasFont: () -> Boolean,
    private val hasRhythm: () -> Boolean,
    private val biosBusy: () -> Boolean,
    private val fontBusy: () -> Boolean,
    private val rhythmBusy: () -> Boolean
) {
    enum class Step { ROM_FOLDER, FIRMWARE }

    private var step = Step.ROM_FOLDER
    private var page: SetupPage? = null
    private var folder: SetupPage? = null
    val isOpen: Boolean get() = page?.inStack == true
    val isChoosingRomFolder: Boolean get() = isOpen && step == Step.ROM_FOLDER

    /** [folderChosen] says whether step 1 picked a folder, for the library afterwards. */
    fun show(next: Step, folderChosen: Boolean = false) {
        step = next
        if (next == Step.ROM_FOLDER) {
            // Back from step 2 returns to the step 1 page underneath, if there is one.
            folder?.takeIf { it.inStack }?.let { existing ->
                pages.popTo(existing)
                page = existing
                return
            }
            val created = folderPage()
            folder = created
            if (page?.inStack == true) pages.replace(created) else pages.push(created)
            page = created
            return
        }
        val created = firmwarePage()
        page = created
        pages.push(created)
    }

    fun close() {
        page = null
        folder = null
        pages.closeAll()
    }

    fun refreshFirmware() {
        if (isOpen && step == Step.FIRMWARE) pages.refresh()
    }

    private fun folderPage() = SetupPage("KAIRO98", "STEP 1 OF 2", "Choose a ROM folder",
        "Select the folder containing your PC-98 games. Kairo98 will scan its disk images and ZIP files.",
        "You can choose or change it later in Menu › Manage library.",
        footerButtons = {
            listOf(FooterButton("skip", { "Skip for now" }) { skipRomFolder(false) },
                FooterButton("choose", { "Choose ROM folder…" }, ButtonTone.PRIMARY, run = selectRomFolder))
        }, onBack = { skipRomFolder(false) }, startFocus = "choose")

    private fun firmwarePage(): SetupPage {
        val busy = { biosBusy() || fontBusy() || rhythmBusy() }
        fun row(id: String, label: String, installed: () -> Boolean, importing: () -> Boolean,
                on: String, off: String, choose: () -> Unit) = SettingsRow.action(id, label,
            value = { if (importing()) "Importing…" else if (installed()) on else off },
            tone = { if (installed()) ValueTone.CURRENT else ValueTone.SUMMARY },
            busy = importing, enabled = { !busy() }, disabledReason = { "Wait for the current import to finish" },
            tray = { TraySpec(buttons = listOf(TrayButton(if (installed()) "Replace…" else "Choose file…",
                ButtonTone.PRIMARY) { choose() })) })
        return SetupPage("KAIRO98", "STEP 2 OF 2", "Optional firmware and font",
            "Import them now, or add them later in Menu › Machine.", content = {
                listOf(RowGroup("FIRMWARE", listOf(
                    row("bios", "BIOS ROM", hasBios, biosBusy, "Installed", "Not installed", selectBios),
                    row("font", "Font BMP", hasFont, fontBusy, "Imported", "Built-in", selectFont),
                    row("rhythm", "YM2608 rhythm ROM", hasRhythm, rhythmBusy, "Installed", "Not installed", selectRhythm)),
                    "The BIOS ROM must be exactly 96 KiB and the rhythm ROM exactly 8 KiB. Kairo98 works without them."))
            }, footerButtons = {
                listOf(FooterButton("done", { "Continue to library" }, ButtonTone.PRIMARY, { !busy() },
                    "Wait for the current import to finish") { finish() })
            }, onBack = { if (!busy()) show(Step.ROM_FOLDER) })
    }
}
