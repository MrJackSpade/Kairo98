package com.mrjackspade.kairo98


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import com.mrjackspade.kairo.frontend.CatalogGame
import com.mrjackspade.kairo.frontend.CatalogSetting
import com.mrjackspade.kairo.frontend.ChoiceOption
import com.mrjackspade.kairo.frontend.ControllerGuestSpec
import com.mrjackspade.kairo.frontend.ControllerLayout
import com.mrjackspade.kairo.frontend.DiskImageEntry
import com.mrjackspade.kairo.frontend.DiskImageLibrary
import com.mrjackspade.kairo.frontend.ExternalGameFile
import com.mrjackspade.kairo.frontend.FirmwareSet
import com.mrjackspade.kairo.frontend.GameKeyMigration
import com.mrjackspade.kairo.frontend.GameSettingsRows
import com.mrjackspade.kairo.frontend.GameSource
import com.mrjackspade.kairo.frontend.ImportedFilePicker
import com.mrjackspade.kairo.frontend.ImportedFiles
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import com.mrjackspade.kairo.frontend.LibraryStrings
import com.mrjackspade.kairo.frontend.MachineOption
import com.mrjackspade.kairo.frontend.MachineActivity
import com.mrjackspade.kairo.frontend.MachineFirstRun
import com.mrjackspade.kairo.frontend.MachineStatus
import com.mrjackspade.kairo.frontend.NativeMachine
import com.mrjackspade.kairo.frontend.StartupStep
import com.mrjackspade.kairo.frontend.R as FrontendR
import com.mrjackspade.kairo.frontend.ScanDepth
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.ShippedAssets
import com.mrjackspade.kairo.frontend.StateSlotStore
import com.mrjackspade.kairo.frontend.UiText
import com.mrjackspade.kairo.frontend.text
import java.io.File
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/** Kairo98: the PC-98 on Neko Project 21/W, in the shared Kairo frontend. */
class MainActivity : MachineActivity<DiskImageEntry>() {
    override val identity = Identity("KAIRO98", 0xffF91CB6.toInt(), "Kairo98", "PC-98", "kairo98", LibraryStrings("KAIRO98", "Kairo98"),
        UiText.res(R.string.credits, "Kairo98", "Neko Project 21/W", "ymfm", "MrJackSpade/Kairo98"),
        UiText.res(R.string.library_empty), UiText.res(FrontendR.string.kairo_library_expired),
        MachineFirstRun.Folder(UiText.res(FrontendR.string.kairo_folder_title), UiText.res(R.string.folder_description, "PC-98", "Kairo98"),
            UiText.res(FrontendR.string.kairo_folder_button)),
        UiText.res(FrontendR.string.kairo_saves_group), "MrJackSpade/Kairo98")
    override val machine = Machine(640, 400, 640.0 / 400.0, "state.np2")

    /** The 21/W machine (pc98_core.cpp); its lock keys are Caps and Kana. */
    override val bridge by lazy {
        NativeMachine("kairo98", Pc98KeyboardLayout.value.let { listOf(checkNotNull(it.capsCode), checkNotNull(it.kanaCode)) })
    }

    override val keyboard = Pc98KeyboardLayout.value
    override val maxKeyCode = 127
    override fun guestKey(androidKey: Int) = pc98ScanCode(androidKey)
    override val controllerSpec = ControllerGuestSpec("PC-98", (0..127).toList(),
        Pc98KeyNames::label, setOf(0x70, 0x71, 0x72, 0x73, 0x74, 0x7d),
        ControllerBindings.JOYSTICK.zip(listOf(FrontendR.string.kairo_up, FrontendR.string.kairo_down,
            FrontendR.string.kairo_left, FrontendR.string.kairo_right, R.string.joystick_button_1,
            R.string.joystick_button_2).map { UiText.res(it) }),
        UiText.res(R.string.joystick_help),
        ControllerGuestSpec.STANDARD_ACTIONS, Pc98KeyboardLayout.value)
    override val bindingsCodec = ControllerBindings.codec
    override val artworkDownloadsEnabled: Boolean get() = resources.getBoolean(R.bool.catalog_art_download_enabled)
    override val automationOwners = setOf("debug-auto-space")

    private val disks by lazy { Pc98Media.library(this) }
    override fun createCatalog() = LayeredGameCatalog(this, Pc98Catalog)
    override fun createLibrary(catalog: LayeredGameCatalog): DiskImageLibrary = disks

    // Saved data from before games had keys ----------------------------------------------------------

    /** Save states, settings and working disks from before games had keys move to their games as the library loads. */
    private val gameKeyMigration by lazy {
        GameKeyMigration(preferences, listOf(
            catalog.legacyOverrides(),
            GameKeyMigration.SaveStore({ saveFolder.location }, StateSlotStore.KINDS.values.toSet(), cacheDir,
                File(filesDir, "game-key-migration-v1.json")) { conflicts ->
                saveFolder.resolveMigrationConflicts(conflicts) { key ->
                    claiming.firstOrNull { it.key == key }?.let(::titleOf) ?: key.path
                }
            }), listOf(Pc98Media.legacyWorkingImages(disks), LegacyLastPlayed()))
    }

    override val keyMigrationPending: Boolean get() = gameKeyMigration.pending

    private inner class LegacyLastPlayed : GameKeyMigration.EntryStore<DiskImageEntry> {
        override fun has(claim: GameKeyMigration.Claim<DiskImageEntry>) =
            preferences.getString("last_played_entry", null) == claim.entryId
        override fun move(claim: GameKeyMigration.Claim<DiskImageEntry>) {
            check(preferences.edit().putString("last_played_entry", claim.key.path).commit()) {
                "Could not remember the last played game"
            }
        }
        // A last-played game no longer in the library is not saved data.
        override fun isEmpty() = true
    }

    /** The games a claim is moving data to; the library list is not published until it ends. */
    @Volatile private var claiming: List<DiskImageEntry> = emptyList()

    /** Moves saved data from before games had keys to [entries]; an entry whose data did not move shows why. */
    private fun claimSavedData(entries: List<DiskImageEntry>): List<DiskImageEntry> {
        if (!gameKeyMigration.pending) return entries
        claiming = entries
        val errors = gameKeyMigration.claim(entries.map { GameKeyMigration.Claim(it, Pc98Media.legacyEntryId(it)) })
        return if (errors.isEmpty()) entries else entries.map { entry ->
            errors[entry.key]?.let { entry.copy(error = text(it)) } ?: entry
        }
    }

    override fun cachedLibrary(tree: Uri) = claimSavedData(super.cachedLibrary(tree))
    override fun scanLibrary(tree: Uri, depth: ScanDepth, cancelled: AtomicBoolean, progress: (String) -> Unit) =
        claimSavedData(super.scanLibrary(tree, depth, cancelled, progress))
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean) =
        claimSavedData(super.inspectExternal(file, cancelled)).onEach { it.error?.let(::error) }

    /** When the catalog does not settle it: the one hard disk; otherwise the player chooses. */
    override fun preferredExternal(playable: List<DiskImageEntry>) = playable.filter { !it.isFloppy }.singleOrNull()

    // Debug launches over adb ------------------------------------------------------------------------

    private var skipDebugChoices = false
    private var skipDebugCommands = false
    private var debugOption: String? = null
    private var debugAdvanceSeconds = 0
    private var debugAdvanceIntervalMs = 100
    private var debugAutoAdvance: DebugAutoAdvance? = null
    private var pendingDebugGame: String? = null
    private var pendingDebugLaunch: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            skipDebugChoices = intent.getBooleanExtra("kairo98.skipStartupChoices", false)
            skipDebugCommands = intent.getBooleanExtra("kairo98.skipLaunchCommands", false)
            debugOption = intent.getStringExtra("kairo98.startupOption")
            debugAdvanceSeconds = intent.getIntExtra("kairo98.autoAdvanceSeconds", 0).coerceIn(0, 3600)
            debugAdvanceIntervalMs = intent.getIntExtra("kairo98.autoAdvanceIntervalMs", 100).coerceIn(75, 10000)
            pendingDebugLaunch = intent.getStringExtra("kairo98.launchGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.launchGame")
            pendingDebugGame = intent.getStringExtra("kairo98.selectGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.selectGame")
        }
        moveMachinePreferences()
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (pendingDebugLaunch != null || pendingDebugGame != null) libraryScreen.entriesPublished = ::selectPendingDebugGame
    }

    private fun decodeDebugGameQuery(encoded: String): String? = runCatching {
        String(android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
            Charsets.UTF_8)
    }.getOrNull()

    /** A scan finished; the list it found is published next, and is the last chance for a pending game. */
    private var debugScanDone = false

    override fun libraryLoaded(entries: List<DiskImageEntry>) {
        debugScanDone = true
    }

    /** The library list on screen changed: the cached list, then each scan's. */
    private fun selectPendingDebugGame() {
        pendingDebugLaunch?.let { query ->
            val entry = libraryScreen.findGameForDebugLaunch(query)
            if (entry != null) {
                pendingDebugLaunch = null
                android.util.Log.i("Kairo98", "ADB launching ${entry.displayName} [$query]")
                launch(entry, false)
                return
            }
            if (debugScanDone) {
                android.util.Log.w("Kairo98", "ADB game not found or ambiguous: $query")
                pendingDebugLaunch = null
            }
        }
        val query = pendingDebugGame ?: return
        if (libraryScreen.selectGame(query)) {
            pendingDebugGame = null
            android.util.Log.i("Kairo98", "Selected library game: $query")
        }
    }

    // Launching --------------------------------------------------------------------------------------

    /**
     * A player's all-games base clock and Skip memory check, from before they were machine options
     * (Kairo98#105), moved once to the options' own keys.
     */
    private fun moveMachinePreferences() {
        val edit = preferences.edit()
        if (preferences.contains("base_clock")) {
            edit.putInt(Pc98Catalog.baseClock.id, if (preferences.getInt("base_clock", 25) == 20) 20 else 25)
            edit.remove("base_clock")
        }
        (preferences.all[MachineOption.SKIP_MEMORY_CHECK] as? Boolean)?.let { skip ->
            edit.putString(MachineOption.SKIP_MEMORY_CHECK, if (skip) "on" else "off")
        }
        edit.apply()
    }
    /** Whether the running game booted from a floppy rather than a hard disk. */
    private var currentIsFloppy = false
    /** Where the running game's FONT.BMP comes from when it is not the player's font.bmp (chooseFont). */
    private var gameFontSource: UiText? = null
    private val gameFonts by lazy { GameFontLocator(this, File(filesDir, "game-font.bmp")) }

    /** The font.bmp files the player imported, for a game to pick by name (Pc98Catalog.font). */
    private val fontFiles by lazy {
        ImportedFiles(File(filesDir, "fonts"), "font", ".bmp", Pc98FontBitmap.MAX_BYTES,
            getString(R.string.font_too_large), { Pc98FontBitmap.validate(this, it) }) { file, name ->
            ImportedFiles.Described(name ?: file.name)
        }
    }
    private val fontImport by lazy {
        ImportedFilePicker(this, fontFiles, FONT_IMPORT_REQUEST, "font-import", getString(R.string.font_import),
            getString(R.string.option_font), ::toast)
    }

    // Font lists the player's fonts too, and imports more (Pc98Catalog.font).
    private val fontOption = Pc98Catalog.font(::fontChoices, ::fontActions)
    override val machineOptions = listOf(Pc98Catalog.system, Pc98Catalog.baseClock, Pc98Catalog.skipMemoryCheck, fontOption)

    /** Your BIOS: the player's bios.rom. A player who imported one keeps starting on it (optionDefault). */
    override fun ownFirmwareInstalled(option: MachineOption, values: Map<String, String>): Boolean {
        check(option.id == Pc98Catalog.system.id) { "${option.id} has no firmware of the player's" }
        return biosFile().isFile
    }
    private fun fontChoices() = fontFiles.items().map { ChoiceOption(it.id, it.label) }
    private fun fontActions(select: (String) -> Unit) = fontImport.actions(select)
    /** What a launch prepared, which the session takes once the game starts. */
    private class PreparedLaunch(val fontSource: UiText?,
                                 /** Whether it booted from a floppy rather than a hard disk. */
                                 val floppy: Boolean)
    @Volatile private var prepared: PreparedLaunch? = null

    /** The PC-98's drives, in the core's slot order (pc98_core.cpp). */
    override val mediaSlots = listOf(
        MediaSlot<DiskImageEntry>(UiText.res(R.string.floppy_drive, "A")) { it.isFloppy },
        MediaSlot(UiText.res(R.string.floppy_drive, "B")) { it.isFloppy },
        MediaSlot(UiText.res(FrontendR.string.kairo_cd_rom)) { it.isCd },
        // A new hard disk starts the machine again from it.
        MediaSlot(UiText.res(FrontendR.string.kairo_hard_disk)) { !it.isFloppy && !it.isCd })

    override fun cancelAutomation() {
        super.cancelAutomation()
        debugAutoAdvance?.cancel()
        debugAutoAdvance = null
    }

    /** Launching is where catalog launch data is used, so this is where bad data fails. */
    override fun launch(entry: DiskImageEntry, fromFrontend: Boolean) {
        val game = catalog.resolve(entry)
        try {
            game.launchCommands
            game.launchScreenHashes
        } catch (failure: RuntimeException) {
            toast(getString(FrontendR.string.kairo_launch_data_unreadable, game.title, failure.message))
            return
        }
        super.launch(entry, fromFrontend)
    }

    override val debugStartupOption: String? get() = debugOption
    override val skipStartupChoices: Boolean get() = skipDebugChoices
    /** The debug harness reads why its launch did not start from performance-auto.txt. */
    override fun startupOptionMissing(game: CatalogGame, requested: String) {
        File(filesDir, "performance-auto.txt").writeText("state\terror\tstartup option $requested unavailable\n")
    }

    /** The game's DOS commands, each typed at the prompt or on the screens it waits for. */
    override fun launchSteps(game: CatalogGame) = (if (skipDebugCommands) emptyList() else game.launchCommands)
        .mapIndexed { index, command ->
            LabeledStep(getString(R.string.startup_step_command, index + 1, command),
                StartupStep(command, true, game.launchScreenHashes.getOrNull(index) ?: emptySet(), game.launchTimeoutMs))
        }

    override fun startupTyped(generation: Int) = scheduleDebugAutoAdvance(generation)

    override fun startGame(entry: DiskImageEntry, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit) {
        // By ID: a collection's picked game is a copy of its library entry.
        val external = libraryEntries.none { it.id == entry.id }
        val sources = launchEntries
        val cd = cdFor(entry, game.cdId, sources)
        val boot = if (entry.isCd) cdBootDiskFor(this, entry, game.bootDiskId, sources) else entry
        val media = bootMediaFor(this, boot, sources, game.requiredBootFloppyId)
        val floppyB = game.initialFloppyBId?.let { id -> requiredFloppyFor(id, entry, sources) }
        val primary = media?.hardDisk ?: boot
        val ownBios = optionValue(game, Pc98Catalog.system) == Pc98Catalog.OWN_BIOS
        check(!ownBios || biosFile().isFile) { getString(R.string.own_bios_missing) }
        val (font, fontSource) = chooseFont(optionValue(game, fontOption), primary, external, cancelled)
        // In the core's slot order (pc98_core.cpp): floppy A, floppy B, the CD-ROM and the hard disk.
        val slots = listOf(if (primary.isFloppy) primary else media?.bootFloppy, floppyB, cd,
            primary.takeUnless { it.isFloppy })
        val images = slots.map { disk -> disk?.let { disks.prepare(it, cancelled, progress).absolutePath } }
        check(bridge.start(firmwareDir().absolutePath, filesDir.absolutePath, images, mapOf(
            "font" to font,
            "baseClock" to optionValue(game, Pc98Catalog.baseClock),
            "gdcClock" to (game.gdcClockTenthsMHz ?: 50).toString(),
            "cpuMultiple" to (game.cpuMultiple ?: DEFAULT_CPU_MULTIPLE).toString(),
            "skipMemoryCheck" to optionValue(game, Pc98Catalog.skipMemoryCheck),
            "ownBios" to if (ownBios) "on" else "off"))) { getString(R.string.start_failed) }
        awaitRunning()
        mediaStarted(slots)
        prepared = PreparedLaunch(fontSource, primary.isFloppy)
    }

    // The DOS prompt of the game's own boot: the same media, with its launch commands not typed
    // (MachineActivity.startAutomation types none at the prompt), as Kairo#89 asks.
    override val commandLine = true

    override fun startAtPrompt(entry: DiskImageEntry, game: CatalogGame, cancelled: AtomicBoolean,
                               progress: (String) -> Unit) = startGame(entry, game, cancelled, progress)

    override fun gameStarted(entry: DiskImageEntry, game: CatalogGame) {
        val launched = checkNotNull(prepared) { "The launch prepared no media" }
        prepared = null
        gameFontSource = launched.fontSource
        currentIsFloppy = launched.floppy
    }

    override fun sessionEnded() {
        super.sessionEnded()
        prepared = null
        gameFontSource = null
        currentIsFloppy = false
    }

    override fun sessionDetails() = listOfNotNull(when {
        currentEntry == null -> null
        currentIsFloppy -> getString(FrontendR.string.kairo_floppy_disk)
        else -> getString(FrontendR.string.kairo_hard_disk)
    })

    /** The shared rows, and where the guest's CPU is. */
    override fun diagnosticsRows(running: Boolean): List<SettingsRow> = super.diagnosticsRows(running) +
        listOfNotNull(SettingsRow.info("csip", "CS:IP") { MachineStatus(bridge.status()).core["csip"].orEmpty() }
            .takeIf { running })

    // Debug runs -------------------------------------------------------------------------------------

    private fun scheduleDebugAutoAdvance(generation: Int) {
        if (debugAdvanceSeconds <= 0) return
        handler.postDelayed({
            if (!current(generation) || libraryVisible) return@postDelayed
            debugAutoAdvance?.cancel()
            debugAutoAdvance = DebugAutoAdvance(inputRouter, bridge::status,
                File(filesDir, "performance-auto.txt"), debugAdvanceSeconds, debugAdvanceIntervalMs).also { it.start() }
        }, 2000)
    }

    // Machine and firmware ---------------------------------------------------------------------------


    /** The PC-98's optional firmware, on the shared firmware rows; Kairo98 has a replacement for each. */
    override val firmwareSets by lazy {
        fun description(name: String, what: UiText) = UiText.res(FrontendR.string.kairo_firmware_part, name, what)
        listOf(UiText.res(FrontendR.string.kairo_firmware_title, "PC-98") to FirmwareSet(firmwareDir(), listOf(
            FirmwareSet.Part("bios.rom", UiText.fixed("BIOS"),
                description("bios.rom", UiText.res(R.string.firmware_bios_description, "PC-98", "Kairo98")),
                setOf("bios.rom"), false, setOf(BIOS_ROM_BYTES)),
            FirmwareSet.Part("font.bmp", UiText.res(R.string.option_font),
                description("font.bmp", UiText.res(R.string.firmware_font_description, "Kairo98")),
                setOf("font.bmp"), false, maxBytes = Pc98FontBitmap.MAX_BYTES, validate = { Pc98FontBitmap.validate(this, it) },
                status = { gameFontSource }, missing = UiText.res(R.string.font_built_in),
                removeLabel = UiText.res(R.string.font_use_built_in)),
            FirmwareSet.Part("ym2608_adpcm_rom.bin", UiText.res(R.string.firmware_rhythm),
                description("ym2608_adpcm_rom.bin", UiText.res(R.string.firmware_rhythm_description)),
                setOf("ym2608_adpcm_rom.bin"), false, setOf(RHYTHM_ROM_BYTES)))))
    }

    /** A clock choice: [id] in tenths of a MHz, shown as [mhz] MHz. */
    private fun mhz(id: String, mhz: String) = ChoiceOption(id, UiText.res(FrontendR.string.kairo_clock_mhz, mhz))

    /** The rows of Kairo98's own catalog settings (Pc98Catalog.settings). */
    override fun customSettingRow(entry: DiskImageEntry, setting: CatalogSetting.Custom,
                                  enabled: () -> Boolean): SettingsRow? {
        val id = entry.key.takeIf { entry.contentId != null }
        val noHash = { GameSettingsRows.noHash(this) }
        val scope = { titleOf(entry) }
        fun game() = catalog.resolve(entry)
        return when (setting.id) {
            Pc98Catalog.GDC_CLOCK -> GameSettingsRows.choice(this, "gdc", getString(R.string.gdc_clock), scope,
                listOf(mhz("25", "2.5"), mhz("50", "5")),
                machineValue(entry, "machine", "gdcClockTenthsMHz", { it.gdcClockTenthsMHz?.toString() },
                    { "50" }, GameSource.BUILT_IN) { it.toInt() }, enabled, noHash)
            Pc98Catalog.CPU -> {
                val base = { if (optionValue(game(), Pc98Catalog.baseClock) == "20") 1.9968 else 2.4576 }
                val cpu = machineValue(entry, "machine", "cpuMultiple", { it.cpuMultiple?.toString() },
                    { DEFAULT_CPU_MULTIPLE.toString() }, GameSource.BUILT_IN) { it.toInt() }
                GameSettingsRows.choice(this, "cpu", getString(R.string.cpu_speed), scope,
                    Pc98Catalog.CPU_MULTIPLES.map {
                        ChoiceOption("$it", UiText.res(R.string.cpu_mhz, base() * it), UiText.res(R.string.cpu_multiple, it))
                    },
                    cpu, enabled, noHash,
                    defaultLabel = { cpu.fallback().toInt().let { getString(R.string.cpu_mhz_multiple, base() * it, it) } })
            }
            // Saving no command types nothing, even where the catalog gives one; Reset returns to it.
            Pc98Catalog.COMMAND -> GameSettingsRows.text(this, "command", getString(R.string.startup_command),
                getString(R.string.startup_command_hint),
                { game().launchCommand }, {
                    id != null && (catalog.sourceOf(entry, "launch", "text") == "User override" ||
                        catalog.sourceOf(entry, "launch", "commands") == "User override")
                },
                { id?.let { catalog.sourceOf(entry, "launch") }?.takeIf { it != "App default" }?.let(GameSource::label) },
                { value -> saveGameSetting(entry) {
                    catalog.updateOverrideSubfields(id!!, "launch", if (value.isEmpty())
                        mapOf("text" to null, "commands" to JSONArray(), "screenHashes" to null)
                    else mapOf("text" to value, "commands" to null, "screenHashes" to null))
                } }, { saveGameSetting(entry) { catalog.resetOverride(id!!, "launch") } },
                enabled = enabled, disabledReason = noHash)
            else -> error("Kairo98 has no row for its catalog setting ${setting.id}")
        }
    }

    override fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        fontImport.handle(requestCode, resultCode, data) || super.handleProductResult(requestCode, resultCode, data)

    private fun pc98ScanCode(key: Int): Int? {
        if (key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) return key - KeyEvent.KEYCODE_1 + 1
        if (key == KeyEvent.KEYCODE_0) return 0x0a
        if (key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val letters = intArrayOf(0x1d, 0x2d, 0x2b, 0x1f, 0x12, 0x20, 0x21, 0x22,
                0x17, 0x23, 0x24, 0x25, 0x2f, 0x2e, 0x18, 0x19, 0x10, 0x13,
                0x1e, 0x14, 0x16, 0x2c, 0x11, 0x2a, 0x15, 0x29)
            return letters[key - KeyEvent.KEYCODE_A]
        }
        if (key in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F10) return 0x62 + key - KeyEvent.KEYCODE_F1
        return when (key) {
            KeyEvent.KEYCODE_ESCAPE -> 0x00
            KeyEvent.KEYCODE_DEL -> 0x0e
            KeyEvent.KEYCODE_TAB -> 0x0f
            KeyEvent.KEYCODE_ENTER -> 0x1c
            KeyEvent.KEYCODE_SPACE -> 0x34
            KeyEvent.KEYCODE_DPAD_UP -> 0x3a
            KeyEvent.KEYCODE_DPAD_LEFT -> 0x3b
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0x3c
            KeyEvent.KEYCODE_DPAD_DOWN -> 0x3d
            KeyEvent.KEYCODE_INSERT -> 0x38
            KeyEvent.KEYCODE_FORWARD_DEL -> 0x39
            KeyEvent.KEYCODE_PAGE_UP -> 0x36
            KeyEvent.KEYCODE_PAGE_DOWN -> 0x37
            KeyEvent.KEYCODE_MOVE_HOME -> 0x3e
            KeyEvent.KEYCODE_MOVE_END -> 0x3f
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0x73
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x70
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0x74
            // US punctuation by position on the PC-98's JIS rows; JIS keyboards send Yen and Ro.
            KeyEvent.KEYCODE_MINUS -> 0x0b
            KeyEvent.KEYCODE_EQUALS -> 0x0c
            KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_YEN -> 0x0d
            KeyEvent.KEYCODE_LEFT_BRACKET -> 0x1a
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x1b
            KeyEvent.KEYCODE_SEMICOLON -> 0x26
            KeyEvent.KEYCODE_APOSTROPHE -> 0x27
            KeyEvent.KEYCODE_COMMA -> 0x30
            KeyEvent.KEYCODE_PERIOD -> 0x31
            KeyEvent.KEYCODE_SLASH -> 0x32
            KeyEvent.KEYCODE_RO -> 0x33
            KeyEvent.KEYCODE_CAPS_LOCK -> 0x71
            KeyEvent.KEYCODE_KATAKANA_HIRAGANA -> 0x72
            KeyEvent.KEYCODE_MUHENKAN -> 0x51
            KeyEvent.KEYCODE_HENKAN -> 0x35
            KeyEvent.KEYCODE_NUMPAD_7 -> 0x42
            KeyEvent.KEYCODE_NUMPAD_8 -> 0x43
            KeyEvent.KEYCODE_NUMPAD_9 -> 0x44
            KeyEvent.KEYCODE_NUMPAD_4 -> 0x46
            KeyEvent.KEYCODE_NUMPAD_5 -> 0x47
            KeyEvent.KEYCODE_NUMPAD_6 -> 0x48
            KeyEvent.KEYCODE_NUMPAD_1 -> 0x4a
            KeyEvent.KEYCODE_NUMPAD_2 -> 0x4b
            KeyEvent.KEYCODE_NUMPAD_3 -> 0x4c
            KeyEvent.KEYCODE_NUMPAD_0 -> 0x4e
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 0x40
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 0x41
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 0x45
            KeyEvent.KEYCODE_NUMPAD_ADD -> 0x49
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> 0x4d
            KeyEvent.KEYCODE_NUMPAD_COMMA -> 0x4f
            KeyEvent.KEYCODE_NUMPAD_DOT -> 0x50
            KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x1c
            else -> null
        }
    }

    /**
     * The FONT.BMP a game starts with, by its Font setting (Pc98Catalog.font, [choice]), and what
     * the firmware row shows it came from while the game runs: null for the player's font.bmp.
     */
    private fun chooseFont(choice: String, primary: DiskImageEntry, external: Boolean,
                           cancelled: AtomicBoolean): Pair<String, UiText?> = when (choice) {
        Pc98Catalog.FONT_GAME_FOLDER -> gameFonts.prepare(primary, if (external) null else tree,
            disks.takeIf { primary.zipEntry != null }, cancelled)?.let { it.absolutePath to UiText.res(R.string.font_game_folder) }
            ?: (prepareFont() to null)
        Pc98Catalog.FONT_FIRMWARE -> prepareFont() to null
        Pc98Catalog.FONT_BUILT_IN -> bundledFont() to UiText.res(R.string.font_built_in)
        else -> checkNotNull(fontFiles.file(choice)) {
            getString(R.string.font_missing)
        }.absolutePath to UiText.res(R.string.font_game_setting)
    }

    /** The imported FONT.BMP, or the bundled Kairo98 font when none is imported. */
    private fun prepareFont(): String {
        val bitmap = fontBitmapFile()
        if (bitmap.isFile) return bitmap.absolutePath
        return bundledFont()
    }

    /** The bundled Kairo98 font, written out of the app's assets when it is not already. */
    private fun bundledFont(): String {
        val bundled = File(filesDir, BUNDLED_FONT)
        ShippedAssets.install(this, "font/$BUNDLED_FONT", bundled)
        File(filesDir, "android-font.bin").delete()
        return bundled.absolutePath
    }

    private fun firmwareDir() = File(filesDir, "firmware")
    private fun biosFile() = File(firmwareDir(), "bios.rom")
    private fun fontBitmapFile() = File(firmwareDir(), "font.bmp")
    private fun rhythmRomFile() = File(firmwareDir(), "ym2608_adpcm_rom.bin")

    companion object {
        /** The core's standard CPU clock multiple, about 49 MHz on the 2.5 MHz base clock. */
        const val DEFAULT_CPU_MULTIPLE = 20
        /** Bundled FONT.BMP built by tools/generate_font_bmp.py from redistributable fonts. */
        private const val BUNDLED_FONT = "kairo98-font.bmp"
        private const val FONT_IMPORT_REQUEST = FIRST_PRODUCT_REQUEST
        private const val BIOS_ROM_BYTES = 0x18000L
        private const val RHYTHM_ROM_BYTES = 0x2000L
    }
}
