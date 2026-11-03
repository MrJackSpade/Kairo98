package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.FirmwareSet
import com.mrjackspade.kairo.frontend.R as FrontendR
import com.mrjackspade.kairo.frontend.UiText
import java.io.File

/** The PC-98's optional firmware, on the shared firmware rows; Kairo98 has a replacement for each. */
object Pc98Firmware {
    private const val BIOS_ROM_BYTES = 0x18000L
    private const val RHYTHM_ROM_BYTES = 0x2000L

    /**
     * The set in [directory]: [validateFont] checks an imported font, [fontSource] says which font
     * the running game uses.
     */
    fun set(directory: File, validateFont: (File) -> Unit, fontSource: () -> UiText?): FirmwareSet {
        fun description(name: String, what: UiText) = UiText.res(FrontendR.string.kairo_firmware_part, name, what)
        return FirmwareSet(directory, listOf(
            FirmwareSet.Part("bios.rom", UiText.fixed("BIOS"),
                description("bios.rom", UiText.res(R.string.firmware_bios_description, "PC-98", "Kairo98")),
                setOf("bios.rom"), false, setOf(BIOS_ROM_BYTES)),
            FirmwareSet.Part("font.bmp", UiText.res(R.string.option_font),
                description("font.bmp", UiText.res(R.string.firmware_font_description, "Kairo98")),
                setOf("font.bmp"), false, maxBytes = Pc98FontBitmap.MAX_BYTES, validate = validateFont,
                status = fontSource, missing = UiText.res(R.string.font_built_in),
                removeLabel = UiText.res(R.string.font_use_built_in)),
            FirmwareSet.Part("ym2608_adpcm_rom.bin", UiText.res(R.string.firmware_rhythm),
                description("ym2608_adpcm_rom.bin", UiText.res(R.string.firmware_rhythm_description)),
                setOf("ym2608_adpcm_rom.bin"), false, setOf(RHYTHM_ROM_BYTES))))
    }
}
