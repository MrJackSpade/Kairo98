package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.R as FrontendR
import com.mrjackspade.kairo.frontend.CatalogGame
import com.mrjackspade.kairo.frontend.CatalogSetting
import com.mrjackspade.kairo.frontend.ChoiceOption
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import com.mrjackspade.kairo.frontend.MachineOption
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.StartupAutomation
import com.mrjackspade.kairo.frontend.UiText
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kairo98's catalog records beyond the common fields: the machine's clocks (`machine`), a DOS
 * command typed at startup with the screens it waits for (`launch`), startup choices, automatic
 * disk swaps, and the media a game needs besides itself (`media` roles bootFloppy, floppyB, cd and
 * bootDisk). Nothing in a catalog is executed by Android. An empty `launch.commands`, `startupChoices`
 * or `diskSwaps` (the shared StartupAutomation) is a player's setting that turns that automation off for the game.
 */
object Pc98Catalog : LayeredGameCatalog.Schema {
    override val platform = "pc98"
    override val catalogUrl = "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/"
    override val selfHostedArt = "/MrJackSpade/Kairo98/main/catalog/artwork/art/catalog/"
    override val contentId = Regex("sha256-(?:hdi|fd|cd)-v1:[0-9a-f]{64}")
    /** The PC-98 keyboard's Esc and Enter (Pc98KeyboardLayout). */
    override val escapeKeys = setOf(0x00)
    override val enterKeys = setOf(0x1c)
    override val mediaExtensions = Regex("\\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf|ccd)$",
        RegexOption.IGNORE_CASE)
    /** A game's font: a FONT.BMP beside the game, else the player's font.bmp, else the built-in one. */
    const val FONT_GAME_FOLDER = "folder"
    /** The player's font.bmp from the Machine page, else the built-in one. */
    const val FONT_FIRMWARE = "firmware"
    const val FONT_BUILT_IN = "builtin"
    /** A font.bmp the player imported for games to pick by name (ImportedFiles, "font:<hash>"). */
    val FONT_ID = Regex("font:[0-9a-f]{16}")

    val fixedFonts = listOf(
        ChoiceOption(FONT_GAME_FOLDER, UiText.res(R.string.font_game_folder)),
        ChoiceOption(FONT_FIRMWARE, UiText.res(R.string.font_yours)),
        ChoiceOption(FONT_BUILT_IN, UiText.res(R.string.font_built_in)))

    /**
     * The FONT.BMP a game starts with (`machine.font`, Kairo98#64): [fixedFonts], or one the
     * player imported, which [imported] lists; [actions] import more.
     */
    fun font(imported: () -> List<ChoiceOption> = { emptyList() },
             actions: (select: (String) -> Unit) -> List<SettingsRow> = { emptyList() }) =
        MachineOption("font", UiText.res(R.string.option_font), { fixedFonts + imported() }, FONT_GAME_FOLDER,
            accepts = { value -> fixedFonts.any { it.id == value } || FONT_ID.matches(value) }, actions = actions)

    /** The Font option as the catalog reads it; MainActivity lists the player's fonts too. */
    val font = font()

    /** The base clock in tenths of a MHz (`machine.baseClockTenthsMHz`): 2.5 MHz, or 2 MHz. */
    val baseClock = MachineOption("baseClockTenthsMHz", UiText.res(R.string.base_clock),
        listOf(ChoiceOption("25", UiText.res(FrontendR.string.kairo_clock_mhz, "2.5")),
            ChoiceOption("20", UiText.res(FrontendR.string.kairo_clock_mhz, "2"))), "25", numeric = true)
    val skipMemoryCheck = MachineOption.skipMemoryCheck()

    const val BUILT_IN_BIOS = "builtin"
    const val OWN_BIOS = "own"
    /** The BIOS a game starts on: the built-in one, or the player's bios.rom. */
    val system = MachineOption("system", UiText.res(FrontendR.string.kairo_option_system),
        listOf(ChoiceOption(BUILT_IN_BIOS, UiText.res(R.string.system_built_in_bios)),
            ChoiceOption(OWN_BIOS, UiText.res(R.string.system_own_bios))), BUILT_IN_BIOS, ownFirmware = OWN_BIOS)

    /** Each a row of the game's settings: MainActivity.customSettingRow builds the custom ones. */
    override val settings: List<CatalogSetting> = listOf(
        CatalogSetting.Choice(baseClock),
        CatalogSetting.Custom(GDC_CLOCK, "machine", setOf("gdcClockTenthsMHz")),
        CatalogSetting.Custom(CPU, "machine", setOf("cpuMultiple")),
        CatalogSetting.Choice(system),
        CatalogSetting.Choice(skipMemoryCheck),
        CatalogSetting.Choice(font),
        CatalogSetting.Custom(COMMAND, "launch", setOf("type", "text", "commands", "ready", "timeoutMs", "screenHashes")),
        CatalogSetting.StartupChoices,
        // Floppies, in drives A and B.
        CatalogSetting.DiskSwaps(2) { _, contentId -> contentId.startsWith("sha256-fd-v1:") },
        CatalogSetting.Drive("bootFloppy", UiText.res(R.string.boot_floppy)) { !it.startsWith(CD) },
        CatalogSetting.Drive("floppyB", UiText.res(FrontendR.string.kairo_drive_2)),
        CatalogSetting.Drive("cd", UiText.res(FrontendR.string.kairo_cd_rom)),
        CatalogSetting.Drive("bootDisk", UiText.res(R.string.boot_disk)) { it.startsWith(CD) })
    override val fields = CatalogSetting.fields(settings)
    override val directTouch = true

    override fun validMedia(role: String, contentId: String) = when (role) {
        "bootFloppy", "floppyB" -> contentId.startsWith("sha256-fd-v1:")
        "cd" -> contentId.startsWith(CD)
        "bootDisk" -> !contentId.startsWith(CD)
        else -> false
    }

    const val GDC_CLOCK = "gdc"
    const val CPU = "cpu"
    const val COMMAND = "command"
    private const val CD = "sha256-cd-v1:"

    override fun validBindings(bindings: JSONArray) = ControllerBindings.valid(bindings)

    /** A layer's launch command replaces the other form of command in the layers below. */
    override fun replacedSubfields(path: List<String>, record: JSONObject): Set<String> =
        if (path == listOf("launch")) when {
            record.has("text") -> setOf("commands")
            record.has("commands") -> setOf("text")
            else -> emptySet()
        } else emptySet()

    override fun validField(field: String, value: Any): Boolean = when (field) {
        "machine" -> value is JSONObject && value.keys().asSequence().all { key ->
            when (key) {
                "gdcClockTenthsMHz" -> value.opt(key) is Int && value.optInt(key) in listOf(25, 50)
                "cpuMultiple" -> value.opt(key) is Int && value.optInt(key) in CPU_MULTIPLES
                else -> listOf(system, baseClock, skipMemoryCheck, font).firstOrNull { it.id == key }?.valid(value.opt(key)) == true
            }
        }
        "launch" -> value is JSONObject && value.length() > 0 &&
            value.keys().asSequence().all { it in fields.getValue("launch")!! } &&
            (!value.has("type") || value.optString("type") == "guestCommand") &&
            !(value.has("text") && value.has("commands")) &&
            (!value.has("commands") || value.optJSONArray("commands")?.let { commands ->
                commands.length() in 0..4 && (0 until commands.length()).all { index ->
                    (commands.opt(index) as? String)?.let(::validCommand) == true
                }
            } == true) &&
            (!value.has("text") || validCommand(value.optString("text"))) &&
            (!value.has("ready") || value.optString("ready") == "dosPrompt") &&
            (!value.has("screenHashes") || value.optJSONArray("screenHashes")?.let { groups ->
                groups.length() in 1..4 && (0 until groups.length()).all { index ->
                    StartupAutomation.validHashes(groups.optJSONArray(index))
                }
            } == true) &&
            (!value.has("timeoutMs") || (value.opt("timeoutMs") is Int && value.optInt("timeoutMs") in 1000..120000))
        StartupAutomation.CHOICES, StartupAutomation.SWAPS -> CatalogSetting.valid(settings, contentId, field, value)
        else -> false
    }

    /** CPU clock multiples a game can ask for, each a CPU speed choice; the app default is 20. */
    val CPU_MULTIPLES = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 20)
    private fun validCommand(value: String) = value.isNotBlank() && value.length <= 128 &&
        value.all { it.code in 32..126 && (it.isLetterOrDigit() || it in " \\/._:-") }
}

val CatalogGame.gdcClockTenthsMHz: Int?
    get() = section("machine")?.optInt("gdcClockTenthsMHz")?.takeIf { it == 25 || it == 50 }
val CatalogGame.cpuMultiple: Int?
    get() = section("machine")?.optInt("cpuMultiple")?.takeIf { it in Pc98Catalog.CPU_MULTIPLES }
val CatalogGame.requiredBootFloppyId: String? get() = mediaId("bootFloppy")
val CatalogGame.initialFloppyBId: String? get() = mediaId("floppyB")
/** The disc this game needs in its CD-ROM drive. */
val CatalogGame.cdId: String? get() = mediaId("cd")
/** For a CD: the hard disk or floppy it boots from. */
val CatalogGame.bootDiskId: String? get() = mediaId("bootDisk")

/** Launch data stays as stored until a launch reads it; malformed data fails that launch. */
val CatalogGame.launchCommands: List<String>
    get() {
        val launch = section("launch")
        return launch?.optJSONArray("commands")?.let { array -> (0 until array.length()).map(array::getString) }
            ?: launch?.optString("text")?.takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
    }
val CatalogGame.launchCommand: String? get() = launchCommands.joinToString("; ").takeIf { launchCommands.isNotEmpty() }

/** Screen hashes apply only when they were stored with these commands, one group per command. */
val CatalogGame.launchScreenHashes: List<Set<Long>>
    get() {
        val commandSource = maxOf(sourceIndexOf("launch", "text") ?: -1, sourceIndexOf("launch", "commands") ?: -1)
        val hashSource = sourceIndexOf("launch", "screenHashes") ?: -1
        return section("launch")?.optJSONArray("screenHashes")
            ?.takeIf { hashSource >= commandSource && it.length() == launchCommands.size }
            ?.let { groups -> (0 until groups.length()).map { StartupAutomation.parseHashes(groups.getJSONArray(it)) } }
            ?: emptyList()
    }
val CatalogGame.launchTimeoutMs: Int
    get() = section("launch")?.optInt("timeoutMs", 30000)?.coerceIn(1000, 120000) ?: 30000
