package com.mrjackspade.kairo98

import android.app.Activity
import com.mrjackspade.kairo.frontend.FirstRunScreen

/** PC-98 setup steps displayed by the shared first-run screen. */
internal class FirstRunSetup(
    activity: Activity,
    private val selectRomFolder: () -> Unit,
    private val skipRomFolder: () -> Unit,
    private val selectBios: () -> Unit,
    private val selectFont: () -> Unit,
    private val selectRhythm: () -> Unit,
    private val finish: () -> Unit,
    private val hasBios: () -> Boolean,
    private val hasFont: () -> Boolean,
    private val hasRhythm: () -> Boolean
) : FirstRunScreen(activity) {
    enum class Step { ROM_FOLDER, FIRMWARE }

    private var step = Step.ROM_FOLDER
    private var busy: String? = null
    val isChoosingRomFolder: Boolean get() = isOpen && step == Step.ROM_FOLDER

    fun show(next: Step) {
        step = next
        busy = null
        render()
    }

    fun setBusy(message: String?) {
        busy = message
        if (isOpen && step == Step.FIRMWARE) render()
    }

    fun refreshFirmware() {
        if (isOpen && step == Step.FIRMWARE) render()
    }

    private fun render() {
        val folder = step == Step.ROM_FOLDER
        val actions = if (folder) listOf(
            FirstRunScreen.Action("Select ROM folder", "Find games on this device", primary = true,
                onClick = selectRomFolder),
            FirstRunScreen.Action("Skip for now", "You can choose one from the library later",
                onClick = skipRomFolder)
        ) else listOf(
            FirstRunScreen.Action("Import BIOS ROM", if (hasBios()) "Imported" else "Not set",
                enabled = busy == null, onClick = selectBios),
            FirstRunScreen.Action("Import Font BMP", if (hasFont()) "Imported" else "Using generated font",
                enabled = busy == null, onClick = selectFont),
            FirstRunScreen.Action("Import YM2608 rhythm ROM", if (hasRhythm()) "Imported" else "Not set",
                enabled = busy == null, onClick = selectRhythm),
            FirstRunScreen.Action(if (hasBios() || hasFont() || hasRhythm()) "Continue to library" else "Skip for now",
                "Open the game library", primary = true, enabled = busy == null, onClick = finish)
        )
        super.show(FirstRunScreen.Page("KAIRO98", if (folder) "SETUP  ·  1 OF 2" else "SETUP  ·  2 OF 2",
            if (folder) "Choose a ROM folder" else "Optional firmware and font",
            if (folder) "Select the folder containing your PC-98 games. Kairo98 will scan its disk images and ZIP files."
            else "Import them now, or add them later from Library → Machine.",
            actions, busy)) {
            if (busy == null) {
                if (folder) skipRomFolder() else finish()
            }
        }
    }
}
