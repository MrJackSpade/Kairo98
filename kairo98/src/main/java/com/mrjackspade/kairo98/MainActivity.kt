package com.mrjackspade.kairo98


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Surface
import com.mrjackspade.kairo.frontend.ButtonTone
import com.mrjackspade.kairo.frontend.CatalogGame
import com.mrjackspade.kairo.frontend.CatalogSetting
import com.mrjackspade.kairo.frontend.ChoiceGroup
import com.mrjackspade.kairo.frontend.ChoiceOption
import com.mrjackspade.kairo.frontend.ChoicePage
import com.mrjackspade.kairo.frontend.ChoiceSpec
import com.mrjackspade.kairo.frontend.ControllerGuestSpec
import com.mrjackspade.kairo.frontend.ControllerLayout
import com.mrjackspade.kairo.frontend.DiskImageEntry
import com.mrjackspade.kairo.frontend.DiskImageLibrary
import com.mrjackspade.kairo.frontend.DocumentPicker
import com.mrjackspade.kairo.frontend.ExternalGameFile
import com.mrjackspade.kairo.frontend.FirmwareSet
import com.mrjackspade.kairo.frontend.GameDisks
import com.mrjackspade.kairo.frontend.GameKeyMigration
import com.mrjackspade.kairo.frontend.GameNotice
import com.mrjackspade.kairo.frontend.GameSettingsRows
import com.mrjackspade.kairo.frontend.GameSource
import com.mrjackspade.kairo.frontend.GameValue
import com.mrjackspade.kairo.frontend.GroupsPage
import com.mrjackspade.kairo.frontend.ImportedFilePicker
import com.mrjackspade.kairo.frontend.ImportedFiles
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import com.mrjackspade.kairo.frontend.LibraryStrings
import com.mrjackspade.kairo.frontend.MachineActivity
import com.mrjackspade.kairo.frontend.MachineBridge
import com.mrjackspade.kairo.frontend.MachineFirstRun
import com.mrjackspade.kairo.frontend.R as FrontendR
import com.mrjackspade.kairo.frontend.RowGroup
import com.mrjackspade.kairo.frontend.ScanDepth
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.ShippedAssets
import com.mrjackspade.kairo.frontend.StateSlotStore
import com.mrjackspade.kairo.frontend.TrayButton
import com.mrjackspade.kairo.frontend.TraySpec
import com.mrjackspade.kairo.frontend.UiText
import com.mrjackspade.kairo.frontend.ValueTone
import com.mrjackspade.kairo.frontend.sameSource
import com.mrjackspade.kairo.frontend.text
import java.io.File
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/** Kairo98: the PC-98 on Neko Project 21/W, in the shared Kairo frontend. */
class MainActivity : MachineActivity<DiskImageEntry>() {
    private external fun nativeStart(path: String?, fontPath: String, biosDir: String,
                                     mhzTimesTen: Int, gdcMhzTimesTen: Int,
                                     cpuMultiple: Int, floppy: Boolean, bootFloppyPath: String?,
                                     secondFloppyPath: String?, cdPath: String?): Boolean
    private external fun nativeFloppy(drive: Int, path: String?): Boolean
    private external fun nativeCd(path: String?): Boolean
    /** Floppy drives A and B and the CD-ROM drive's images, "" for an empty one; null when no machine runs. */
    private external fun nativeMediaPaths(): Array<String>?
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeSkipMemoryCheck(skip: Boolean)
    private external fun nativeLoadedLocks(): Int
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeMouseWarp(x: Int, y: Int)
    private external fun nativeMouseWarpDone(): Boolean
    private external fun nativeSaveState(dir: String): Int
    private external fun nativeLoadState(dir: String, from: Array<String>, to: Array<String>): Int
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeInputTelemetry(): LongArray
    private external fun nativeStatus(): String
    private external fun nativeDosPromptReady(): Boolean
    /** Types these steps with the shared typer (startup_typer.h), replacing any still to type; none stops it. */
    private external fun nativeStartupStart(texts: Array<String>, enters: BooleanArray, screens: Array<LongArray>,
                                            timeoutsMs: IntArray)
    /** Types [text] at once, after the steps still to type. */
    private external fun nativeStartupKeys(text: String, enter: Boolean)
    /** Waits for the step the typer stalled on again. */
    private external fun nativeStartupResume()
    /** The typer's state (STARTUP_*) and the step it stalled on, or -1. */
    private external fun nativeStartupStatus(): IntArray
    private external fun nativeStartupError(): String
    private external fun nativeSetScreenHashSampling(enabled: Boolean)
    private external fun nativeScreenHashSnapshot(): LongArray
    private external fun nativeSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nativeSetShaderPreset(path: String?)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)

    override val identity = Identity("KAIRO98", 0xffF91CB6.toInt(), "Kairo98", "PC-98", "kairo98", LibraryStrings("KAIRO98", "Kairo98"),
        UiText.res(R.string.credits, "Kairo98", "Neko Project 21/W", "ymfm", "MrJackSpade/Kairo98"),
        UiText.res(R.string.library_empty), UiText.res(FrontendR.string.kairo_library_expired),
        MachineFirstRun.Folder(UiText.res(FrontendR.string.kairo_folder_title), UiText.res(R.string.folder_description, "PC-98", "Kairo98"),
            UiText.res(FrontendR.string.kairo_folder_button)),
        UiText.res(FrontendR.string.kairo_saves_group), "MrJackSpade/Kairo98")
    override val machine = Machine(640, 400, 640.0 / 400.0, "state.np2")

    /** The 21/W machine. Its status line is "<state>[: error] | frames N | CS:IP | audio | xruns | config | profile". */
    override val bridge = object : MachineBridge {
        override fun stop() = nativeStop()
        override fun pause(paused: Boolean) = nativePause(paused)
        override fun reset() {
            inputRouter.resetLocks()
            nativeReset()
        }
        override fun key(code: Int, down: Boolean) = nativeKey(code, down)
        override fun joystick(control: Int, down: Boolean) = nativeJoystick(control, down)
        override fun mouseMove(dx: Int, dy: Int) = nativeMouseMove(dx, dy)
        override fun mouseButton(button: Int, down: Boolean) = nativeMouseButton(button, down)
        override fun saveState(directory: String) = nativeSaveState(directory)
        override fun loadState(directory: String, moved: Map<String, String>): Int {
            val entries = moved.entries.toList()
            return nativeLoadState(directory, entries.map { it.key }.toTypedArray(), entries.map { it.value }.toTypedArray())
        }
        override fun lockedKeys(): Set<Int> {
            val locks = nativeLoadedLocks()
            val layout = Pc98KeyboardLayout.value
            return setOfNotNull(layout.capsCode.takeIf { locks and 1 != 0 }, layout.kanaCode.takeIf { locks and 2 != 0 })
        }
        override fun status(): String {
            val parts = nativeStatus().split(" | ")
            fun part(index: Int, prefix: String) = parts.getOrNull(index)?.removePrefix(prefix)?.trim() ?: ""
            return listOf(parts[0].substringBefore(':').trim(), parts[0].substringAfter(':', "").trim(),
                part(1, "frames"), part(3, "audio"), part(4, "xruns"), parts.getOrNull(5).orEmpty()).joinToString("|")
        }
        override fun setSurface(surface: Surface?) = nativeSetSurface(surface, 0, 0)
        override fun setShaderPreset(path: String?) = nativeSetShaderPreset(path)
        override fun setMuted(muted: Boolean) = nativeSetMuted(muted)
        override fun setFastForward(enabled: Boolean) = nativeSetFastForward(enabled)
        override fun inputTelemetry(): LongArray = nativeInputTelemetry()
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

    /** When the catalog does not settle it: the one hard disk, else the one boot disk; otherwise the player chooses. */
    override fun preferredExternal(playable: List<DiskImageEntry>) = playable.filter { !it.isFloppy }.singleOrNull()
        ?: playable.filter { it.displayName.contains("boot", true) }.singleOrNull()

    // Debug launches over adb ------------------------------------------------------------------------

    private var traceScreenHashes = false
    private var skipDebugChoices = false
    private var skipDebugCommands = false
    private var debugStartupOption: String? = null
    private var debugAdvanceSeconds = 0
    private var debugAdvanceIntervalMs = 100
    private var debugAutoAdvance: DebugAutoAdvance? = null
    private var pendingDebugGame: String? = null
    private var pendingDebugLaunch: String? = null
    private var lastTraceSerial = 0L

    private val traceHashes = object : Runnable {
        override fun run() {
            if (traceScreenHashes && !libraryVisible) {
                val sample = nativeScreenHashSnapshot()
                if (sample.size == 2 && sample[0] != 0L && sample[0] != lastTraceSerial) {
                    lastTraceSerial = sample[0]
                    File(filesDir, "startup-hash-trace.txt").appendText(
                        "${android.os.SystemClock.elapsedRealtime()} ${sample[0]} " +
                            "${java.lang.Long.toUnsignedString(sample[1], 16).padStart(16, '0')} " +
                            "${nativeDosPromptReady()} ${nativeStatus()}\n")
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            traceScreenHashes = intent.getBooleanExtra("kairo98.traceScreenHashes", false)
            skipDebugChoices = intent.getBooleanExtra("kairo98.skipStartupChoices", false)
            skipDebugCommands = intent.getBooleanExtra("kairo98.skipLaunchCommands", false)
            debugStartupOption = intent.getStringExtra("kairo98.startupOption")
            debugAdvanceSeconds = intent.getIntExtra("kairo98.autoAdvanceSeconds", 0).coerceIn(0, 3600)
            debugAdvanceIntervalMs = intent.getIntExtra("kairo98.autoAdvanceIntervalMs", 100).coerceIn(75, 10000)
            pendingDebugLaunch = intent.getStringExtra("kairo98.launchGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.launchGame")
            pendingDebugGame = intent.getStringExtra("kairo98.selectGame64")
                ?.let(::decodeDebugGameQuery) ?: intent.getStringExtra("kairo98.selectGame")
        }
        clock = preferences.getInt("base_clock", 25).let { if (it == 20) 20 else 25 }
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (pendingDebugLaunch != null || pendingDebugGame != null) libraryScreen.entriesPublished = ::selectPendingDebugGame
        if (traceScreenHashes) {
            File(filesDir, "startup-hash-trace.txt").writeText("")
            nativeSetScreenHashSampling(true)
            handler.post(traceHashes)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(traceHashes)
        super.onDestroy()
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

    /** The clock all games use unless they have their own: 25 for 2.5 MHz, 20 for 2 MHz. */
    private var clock = 25
    /** What each drive holds, for the Disks page. */
    private var currentIsFloppy = false
    private val mountedFloppies = arrayOfNulls<String>(2)
    private val manualFloppies = arrayOfNulls<File>(2)
    /** The disc in the CD-ROM drive, by file name. */
    private var mountedCd: String? = null
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
    override val machineOptions = listOf(Pc98Catalog.font(::fontChoices, ::fontActions))
    private fun fontChoices() = fontFiles.items().map { ChoiceOption(it.id, it.label) }
    private fun fontActions(select: (String) -> Unit) = fontImport.actions(select)
    private var selectedStartup = emptyList<Pair<StartupChoice, StartupOption>>()
    /** Media a launch prepared, which the session takes once the game starts. */
    private class PreparedLaunch(val bootFloppy: DiskImageEntry?, val floppyB: DiskImageEntry?,
                                 val swapSources: Map<String, DiskImageEntry>, val fontSource: UiText?,
                                 val cd: DiskImageEntry?,
                                 /** What started: the launched entry, or a CD's boot disk. */
                                 val boot: DiskImageEntry)
    @Volatile private var prepared: PreparedLaunch? = null
    @Volatile private var commandCancelled = AtomicBoolean(false)
    @Volatile private var floppyBusy = false
    private val samplingLock = Any()
    private var samplingUsers = 0

    override fun cancelAutomation() {
        commandCancelled.set(true)
        stopStartupTyping()
        debugAutoAdvance?.cancel()
        debugAutoAdvance = null
    }

    override fun launch(entry: DiskImageEntry, fromFrontend: Boolean) {
        val game = catalog.resolve(entry)
        // Launching is where catalog launch data is used, so this is where bad data fails.
        try {
            game.launchCommands
            game.launchScreenHashes
            game.startupChoices
            game.diskSwaps
        } catch (failure: RuntimeException) {
            toast(getString(R.string.launch_data_unreadable, game.title, failure.message))
            return
        }
        debugStartupOption?.let { requested ->
            val selected = game.startupChoices.mapNotNull { choice ->
                choice.options.firstOrNull { it.id == requested }?.let { choice to it }
            }
            if (selected.size == game.startupChoices.size) {
                startWithChoices(entry, game, selected, fromFrontend)
                return
            }
            File(filesDir, "performance-auto.txt").writeText("state\terror\tstartup option $requested unavailable\n")
            return
        }
        if (game.startupChoices.isNotEmpty() && !skipDebugChoices) chooseStartupOptions(entry, game, 0, emptyList(), fromFrontend)
        else startWithChoices(entry, game, emptyList(), fromFrontend)
    }

    private fun startWithChoices(entry: DiskImageEntry, game: CatalogGame,
                                 choices: List<Pair<StartupChoice, StartupOption>>, fromFrontend: Boolean) {
        selectedStartup = choices
        startSession(entry, game, fromFrontend)
    }

    /** One choice page per startup choice; Play manually starts without the rest. Back cancels. */
    private fun chooseStartupOptions(entry: DiskImageEntry, game: CatalogGame, index: Int,
                                     selected: List<Pair<StartupChoice, StartupOption>>,
                                     fromFrontend: Boolean, replace: Boolean = false) {
        if (index == game.startupChoices.size) {
            startWithChoices(entry, game, selected, fromFrontend)
            return
        }
        val choice = game.startupChoices[index]
        lateinit var page: ChoicePage
        page = ChoicePage(ChoiceSpec(choice.title, { game.title }, {
            listOf(ChoiceGroup(null, choice.options.map { ChoiceOption(it.id, it.label) },
                getString(R.string.startup_choice_foot, identity.appName)))
        }, { null }, { id ->
            val option = choice.options.first { it.id == id }
            pages.postDelayed({
                // Back during the pause cancels, as it does on the page.
                if (pages.top === page) chooseStartupOptions(entry, game, index + 1,
                    selected + (choice to option), fromFrontend, replace = true)
            }, 150)
        }, actions = {
            listOf(SettingsRow.action("manual", getString(R.string.play_manually)) { startWithChoices(entry, game, selected, fromFrontend) })
        }, stay = true, cycles = false))
        if (replace) pages.replace(page) else pages.push(page)
    }

    override fun startGame(entry: DiskImageEntry, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit) {
        val external = entry !in libraryEntries
        val sources = launchEntries
        val cd = cdFor(entry, game.cdId, sources)
        val boot = if (entry.isCd) cdBootDiskFor(this, entry, game.bootDiskId, sources) else entry
        val media = bootMediaFor(this, boot, sources, game.requiredBootFloppyId)
        val swapSources = game.diskSwaps.associate { rule ->
            rule.contentId to requiredFloppyFor(rule.contentId, entry, sources)
        }
        val floppyB = game.initialFloppyBId?.let { id -> requiredFloppyFor(id, entry, sources) }
        val primary = media?.hardDisk ?: boot
        nativeSkipMemoryCheck(preferences.getBoolean(SKIP_MEMORY_CHECK, true))
        val disk = disks.prepare(primary, cancelled, progress)
        val bootFloppy = media?.bootFloppy?.let { disks.prepare(it, cancelled, progress) }
        val secondFloppy = floppyB?.let { disks.prepare(it, cancelled, progress) }
        val disc = cd?.let { disks.prepare(it, cancelled, progress) }
        val (font, fontSource) = chooseFont(optionValue(game, machineOptions.single()), primary, external, cancelled)
        check(startMachine(disk.absolutePath, font, game.baseClockTenthsMHz ?: clock, game.gdcClockTenthsMHz ?: 50,
            game.cpuMultiple ?: DEFAULT_CPU_MULTIPLE, primary.isFloppy, bootFloppy?.absolutePath,
            secondFloppy?.absolutePath, disc?.absolutePath)) { getString(R.string.start_failed) }
        awaitMachineReady()?.let(::error)
        prepared = PreparedLaunch(media?.bootFloppy, floppyB, swapSources, fontSource, cd, boot)
    }

    private fun awaitMachineReady(): String? {
        repeat(200) {
            val state = nativeStatus()
            if (state.startsWith("Running") || state.startsWith("Paused")) return null
            if (state.startsWith("Error")) return state.substringBefore(" | ")
            Thread.sleep(50)
        }
        return getString(R.string.start_timed_out)
    }

    override fun gameStarted(entry: DiskImageEntry, game: CatalogGame) {
        val launched = checkNotNull(prepared) { "The launch prepared no media" }
        prepared = null
        gameFontSource = launched.fontSource
        val booted = launched.boot
        currentIsFloppy = launched.bootFloppy == null && booted.isFloppy
        mountedFloppies[0] = launched.bootFloppy?.displayName ?: if (booted.isFloppy) booted.displayName else null
        mountedFloppies[1] = launched.floppyB?.displayName
        manualFloppies.fill(null)
        mountedCd = launched.cd?.displayName
        scheduleStartupQueue(game, selectedStartup)
        scheduleDiskSwaps(game.diskSwaps, launched.swapSources)
    }

    override fun sessionEnded() {
        super.sessionEnded()
        prepared = null
        gameFontSource = null
        currentIsFloppy = false
        mountedFloppies.fill(null)
        manualFloppies.fill(null)
        mountedCd = null
        selectedStartup = emptyList()
    }

    /** Native media replacement may require restarting from the original disks. */
    override fun loadRejected(code: Int) {
        if (code in 4..6) currentEntry?.let { entry ->
            userPaused = false
            closeMenu()
            launch(entry, sessionFromFrontend)
        }
    }

    override fun stateError(code: Int) = when (code) {
        1 -> getString(R.string.state_error_not_running)
        2 -> getString(R.string.state_error_missing)
        3 -> getString(R.string.state_error_storage)
        4 -> getString(R.string.state_error_disk_copy)
        5 -> getString(R.string.state_error_unreadable)
        6 -> getString(R.string.state_error_no_response)
        7 -> getString(R.string.state_error_path_too_long)
        else -> getString(R.string.state_error_code, code)
    }

    override fun sessionDetails() = listOfNotNull(when {
        currentEntry == null -> null
        currentIsFloppy -> getString(FrontendR.string.kairo_floppy_disk)
        else -> getString(FrontendR.string.kairo_hard_disk)
    })

    override fun diagnosticsRows(running: Boolean): List<SettingsRow> {
        val state = getString(FrontendR.string.kairo_diagnostics_state)
        if (!running) return listOf(SettingsRow.info("state", state) { getString(FrontendR.string.kairo_diagnostics_stopped) })
        fun part(index: Int, prefix: String = "") =
            nativeStatus().split(" | ").getOrNull(index)?.removePrefix(prefix)?.trim() ?: ""
        return listOf(
            SettingsRow.info("state", state) { part(0) },
            SettingsRow.info("frames", getString(FrontendR.string.kairo_diagnostics_frames)) { part(1, "frames") },
            SettingsRow.info("csip", "CS:IP") { part(2, "CS:IP") },
            SettingsRow.info("audio", getString(FrontendR.string.kairo_diagnostics_audio)) { part(3, "audio") },
            SettingsRow.info("xruns", getString(FrontendR.string.kairo_diagnostics_xruns)) { part(4, "xruns") },
            SettingsRow.info("audio-config", getString(FrontendR.string.kairo_diagnostics_audio_config)) { part(5) },
            SettingsRow.info("audio-profile", getString(R.string.diagnostics_audio_profile)) { part(6) })
    }

    // Startup automation: typed commands and choices on recognized screens, and disk swaps ------------

    /** A step for the typer, and what the notice calls it when the typer stalls on it. */
    private class StartupStep(val label: String, val text: String, val enter: Boolean,
                              /** The screens it waits for; none waits for the DOS prompt. */
                              val screens: Set<Long>, val timeoutMs: Int)

    private fun scheduleStartupQueue(game: CatalogGame?, choices: List<Pair<StartupChoice, StartupOption>>) {
        commandCancelled.set(true)
        if (game == null) return
        val steps = (if (skipDebugCommands) emptyList() else game.launchCommands).mapIndexed { index, command ->
            StartupStep(getString(R.string.startup_step_command, index + 1, command), command, true,
                game.launchScreenHashes.getOrNull(index) ?: emptySet(), game.launchTimeoutMs)
        } + choices.flatMap { (choice, option) ->
            option.inputs.mapIndexed { index, input ->
                StartupStep(getString(R.string.startup_step_choice, choice.title, option.label, index + 1, option.inputs.size),
                    input.key.toString(), input.enter, input.screenHashes, 120000)
            }
        }
        val generation = sessionGeneration
        if (steps.isEmpty()) {
            scheduleDebugAutoAdvance(generation)
            return
        }
        nativeStartupStart(steps.map { it.text }.toTypedArray(), BooleanArray(steps.size) { steps[it].enter },
            steps.map { it.screens.toLongArray() }.toTypedArray(), IntArray(steps.size) { steps[it].timeoutMs })
        val cancelled = AtomicBoolean(false)
        commandCancelled = cancelled
        watchStartup(steps, cancelled, generation)
    }

    private fun stopStartupTyping() = nativeStartupStart(emptyArray(), BooleanArray(0), emptyArray(), IntArray(0))

    /** Follows the typer until it is done, stalls on a step (a notice offers Retry) or cannot type one. */
    private fun watchStartup(steps: List<StartupStep>, cancelled: AtomicBoolean, generation: Int) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (cancelled.get() || !current(generation)) return
                val (state, stalled) = nativeStartupStatus()
                when (state) {
                    STARTUP_TYPING -> handler.postDelayed(this, 100)
                    STARTUP_IDLE -> scheduleDebugAutoAdvance(generation)
                    STARTUP_STALLED -> showStartupTimeout(steps, steps[stalled], cancelled, generation)
                    STARTUP_FAILED -> toast(nativeStartupError())
                    else -> error("Unknown startup typer state $state")
                }
            }
        }, 100)
    }

    private fun acquireScreenHashes() = synchronized(samplingLock) {
        if (samplingUsers++ == 0 && !traceScreenHashes) nativeSetScreenHashSampling(true)
    }

    private fun releaseScreenHashes() = synchronized(samplingLock) {
        if (--samplingUsers == 0 && !traceScreenHashes) nativeSetScreenHashSampling(false)
    }

    private fun scheduleDebugAutoAdvance(generation: Int) {
        if (debugAdvanceSeconds <= 0) return
        handler.postDelayed({
            if (!current(generation) || libraryVisible) return@postDelayed
            debugAutoAdvance?.cancel()
            debugAutoAdvance = DebugAutoAdvance(inputRouter, ::nativeStatus,
                File(filesDir, "performance-auto.txt"), debugAdvanceSeconds, debugAdvanceIntervalMs).also { it.start() }
        }, 2000)
    }

    private fun scheduleDiskSwaps(rules: List<DiskSwap>, sources: Map<String, DiskImageEntry>) {
        if (rules.isEmpty()) return
        val generation = sessionGeneration
        acquireScreenHashes()
        Thread {
            var failedRule: DiskSwap? = null
            try {
                DiskSwapMatcher(::nativeScreenHashSnapshot, { nativeStatus().startsWith("Running") },
                    { !current(generation) },
                    { rule ->
                        failedRule = rule
                        performDiskSwap(rule, sources.getValue(rule.contentId), generation)
                    }).run(rules)
            } catch (error: Exception) {
                runOnUiThread { if (current(generation)) showDiskSwapFailure(failedRule, error, rules, sources) }
            } finally {
                releaseScreenHashes()
            }
        }.start()
    }

    @Synchronized private fun beginFloppyChange(): Boolean {
        if (floppyBusy) return false
        floppyBusy = true
        return true
    }

    @Synchronized private fun endFloppyChange() { floppyBusy = false }

    /** The information notice for a swap in progress, replaced as its progress text changes. */
    private var swapping: GameNotice? = null

    private fun performDiskSwap(rule: DiskSwap, entry: DiskImageEntry, generation: Int) {
        while (!beginFloppyChange()) {
            if (!current(generation)) return
            Thread.sleep(100)
        }
        val notice = GameNotice(getString(R.string.disk_swapping, "${'A' + rule.drive}", entry.displayName), "")
        try {
            runOnUiThread { if (current(generation)) overlay.showNotice(notice) }
            val disk = disks.prepare(entry, AtomicBoolean(false)) { message ->
                runOnUiThread {
                    if (current(generation) && overlay.showing(notice))
                        overlay.showNotice(GameNotice(notice.title, message).also { swapping = it })
                }
            }
            if (!current(generation)) return
            if (!nativeFloppy(rule.drive, disk.absolutePath)) error(getString(R.string.floppy_mount_failed))
            runOnUiThread {
                if (current(generation)) {
                    manualFloppies[rule.drive]?.delete()
                    manualFloppies[rule.drive] = null
                    mountedFloppies[rule.drive] = entry.displayName
                }
            }
            if (rule.key.isNotEmpty() || rule.enter) {
                Thread.sleep(150)
                if (current(generation)) nativeStartupKeys(rule.key, rule.enter)
            }
        } finally {
            endFloppyChange()
            runOnUiThread {
                if (overlay.showing(notice) || swapping?.let(overlay::showing) == true) overlay.hideNotice()
                swapping = null
            }
        }
    }

    /** The game keeps running, so the prompt docks at the top instead of blocking it. */
    private fun showDiskSwapFailure(rule: DiskSwap?, error: Exception, rules: List<DiskSwap>,
                                    sources: Map<String, DiskImageEntry>) {
        val why = error.message ?: getString(R.string.unknown_error)
        overlay.showNotice(GameNotice(getString(R.string.disk_swap_failed),
            rule?.let { getString(R.string.disk_swap_failed_drive_message, "${'A' + it.drive}", why) }
                ?: getString(R.string.disk_swap_failed_message, why),
            listOf(TrayButton(getString(R.string.continue_manually)),
                TrayButton(getString(R.string.choose_floppy)) {
                    openMenu()
                    pages.push(disksPage())
                    pages.push(ChoicePage(floppyChoice(rule?.drive ?: 0), "floppy${rule?.drive ?: 0}"))
                },
                TrayButton(getString(R.string.retry), ButtonTone.PRIMARY) { scheduleDiskSwaps(rules, sources) })))
    }

    private fun showStartupTimeout(steps: List<StartupStep>, step: StartupStep, cancelled: AtomicBoolean, generation: Int) {
        overlay.showNotice(GameNotice(getString(R.string.startup_not_recognized),
            getString(R.string.startup_waiting, step.label),
            listOf(TrayButton(getString(FrontendR.string.kairo_action_restart)) { openMenu("f-restart", confirm = true) },
                TrayButton(getString(R.string.open_keyboard)) { showKeyboard() },
                TrayButton(getString(R.string.retry), ButtonTone.PRIMARY) {
                    nativeStartupResume()
                    watchStartup(steps, cancelled, generation)
                })))
    }

    // Direct tap: the mouse warps to the touched point before a click ----------------------------------

    /** The view is exactly the scaled frame, so the point already accounts for scaling and crop. */
    override fun warpMouse(x: Int, y: Int) = nativeMouseWarp(x, y)

    override fun mouseWarpDone(): Boolean = nativeMouseWarpDone()

    // Disks ------------------------------------------------------------------------------------------

    override fun machineRows() = listOf(SettingsRow.nav("f-disks", getString(FrontendR.string.kairo_media_disks)) {
        pages.push(disksPage())
    })

    /** Disks: the hard disk, which asks first because it ends the game, both floppy drives, and the CD-ROM. */
    private fun disksPage() = GroupsPage(getString(FrontendR.string.kairo_media_disks), { currentGame?.title }, {
        listOf(RowGroup(getString(FrontendR.string.kairo_media_drives_group), listOf(
            SettingsRow.action("hdd", getString(FrontendR.string.kairo_hard_disk),
                value = { if (currentIsFloppy) empty() else currentEntry?.displayName ?: empty() },
                tone = { if (currentEntry == null || currentIsFloppy) ValueTone.EMPTY else ValueTone.CURRENT },
                tray = {
                    val choose = getString(FrontendR.string.kairo_choose_file)
                    if (currentEntry == null) TraySpec(buttons = listOf(TrayButton(choose, ButtonTone.PRIMARY) { chooseHdi() }))
                    else TraySpec(getString(R.string.hard_disk_change_warning),
                        listOf(TrayButton(getString(FrontendR.string.kairo_cancel)),
                            TrayButton(choose, ButtonTone.PRIMARY) { chooseHdi() }), focus = 0)
                }),
            floppyRow(0), floppyRow(1), cdRow())))
    })

    private fun empty() = getString(FrontendR.string.kairo_empty)

    private fun cdRow() = SettingsRow.choice("cd", getString(FrontendR.string.kairo_cd_rom), cdChoice(),
        value = { mountedCd ?: empty() },
        tone = { if (mountedCd == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !floppyBusy },
        disabledReason = { getString(if (floppyBusy) FrontendR.string.kairo_media_wait else R.string.cd_start_first) })

    /** Empty ejects; otherwise the game's catalog disc and the CDs in its ZIP or folder. */
    private fun cdChoice(): ChoiceSpec {
        fun candidates(): List<DiskImageEntry> {
            val current = currentEntry ?: return emptyList()
            val named = currentGame?.cdId
            return libraryEntries.filter { entry ->
                entry.playable && entry.isCd && (catalog.matchId(entry) == named || sameSource(current, entry))
            }.distinctBy { it.contentId }
        }
        return ChoiceSpec(getString(FrontendR.string.kairo_cd_rom), { currentGame?.title }, {
            val found = candidates()
            listOfNotNull(ChoiceGroup(null, listOf(ChoiceOption("empty", UiText.res(FrontendR.string.kairo_empty))),
                if (found.isEmpty()) getString(R.string.cd_none_for_game) else null),
                found.takeIf { it.isNotEmpty() }?.let { items ->
                    ChoiceGroup(getString(R.string.for_this_game), items.map { ChoiceOption(it.id, it.displayName) })
                })
        }, { candidates().firstOrNull { it.displayName == mountedCd }?.id
            ?: if (mountedCd == null) "empty" else null }, { id ->
            if (id == "empty") changeCd(null)
            else candidates().firstOrNull { it.id == id }?.let(::changeCd)
        }, cycles = false)
    }

    private fun changeCd(entry: DiskImageEntry?) {
        if (!beginFloppyChange()) return
        toast(if (entry == null) getString(R.string.cd_ejecting) else getString(R.string.disk_preparing, entry.displayName))
        pages.refresh()
        Thread {
            val result = try {
                val disc = entry?.let { disks.prepare(it, AtomicBoolean(false)) { } }
                if (!nativeCd(disc?.absolutePath)) error(getString(R.string.cd_mount_failed))
                null
            } catch (error: Exception) { error.message ?: getString(R.string.cd_change_failed) }
            runOnUiThread {
                endFloppyChange()
                if (result == null) mountedCd = entry?.displayName
                toast(result ?: getString(FrontendR.string.kairo_media_changed, getString(FrontendR.string.kairo_cd_rom),
                    entry?.displayName ?: empty()))
                pages.refresh()
            }
        }.start()
    }

    /** The floppies and CD, by drive, for the UI tests (KairoActivity.gameProbe). */
    override fun probeDetails(): Map<String, String> = super.probeDetails() + mapOf(
        "media.Floppy A" to (mountedFloppies[0] ?: ""), "media.Floppy B" to (mountedFloppies[1] ?: ""),
        "media.CD-ROM" to (mountedCd ?: ""))

    private fun floppyLabel(drive: Int) = getString(R.string.floppy_drive, "${'A' + drive}")

    /**
     * A loaded state's drives, as np2 holds them now, for the Disks page (Kairo98#103): each image by
     * its library disk, or its file name for one from outside the library.
     */
    override fun stateLoaded() {
        val paths = nativeMediaPaths() ?: return
        fun name(path: String) = path.takeIf { it.isNotEmpty() }
            ?.let { disks.entryForWorkingImage(it, libraryEntries)?.displayName ?: File(it).name }
        mountedFloppies[0] = name(paths[0])
        mountedFloppies[1] = name(paths[1])
        mountedCd = name(paths[2])
    }

    private fun floppyRow(drive: Int) = SettingsRow.choice("floppy$drive", floppyLabel(drive), floppyChoice(drive),
        value = { mountedFloppies[drive] ?: empty() },
        tone = { if (mountedFloppies[drive] == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !floppyBusy },
        disabledReason = { getString(if (floppyBusy) R.string.floppy_wait else R.string.floppy_start_first) })

    /**
     * Empty ejects; then the game's own disks (the shared GameDisks choice), Other for every floppy
     * in the library, or a file from the device.
     */
    private fun floppyChoice(drive: Int): ChoiceSpec {
        fun takes(entry: DiskImageEntry) = entry.playable && entry.isFloppy
        fun own(): List<DiskImageEntry> {
            val current = currentEntry ?: return emptyList()
            val inDrives = libraryEntries.filter { it.isFloppy && it.displayName in mountedFloppies }
            return GameDisks.own(current, libraryEntries, inDrives, catalog.setDisks(current).toSet(), catalog::matchId, ::takes)
        }
        return GameDisks.choice(this, floppyLabel(drive), { currentGame?.title }, ::own, { libraryEntries.filter(::takes) }, {
            own().firstOrNull { it.displayName == mountedFloppies[drive] }?.id
                ?: if (mountedFloppies[drive] == null) GameDisks.EMPTY else null
        }, { entry -> changeFloppy(drive, entry) }) {
            listOf(SettingsRow.action("choose", getString(FrontendR.string.kairo_choose_file)) {
                DocumentPicker.open(this, if (drive == 0) FLOPPY_A_REQUEST else FLOPPY_B_REQUEST, failed = ::toast)
            })
        }
    }

    private fun changeFloppy(drive: Int, entry: DiskImageEntry?) {
        if (!beginFloppyChange()) return
        toast(if (entry == null) getString(R.string.floppy_ejecting, "${'A' + drive}")
            else getString(R.string.disk_preparing, entry.displayName))
        pages.refresh()
        Thread {
            val result = try {
                val disk = entry?.let { disks.prepare(it, AtomicBoolean(false)) { } }
                if (!nativeFloppy(drive, disk?.absolutePath)) error(getString(R.string.floppy_mount_failed))
                null
            } catch (error: Exception) { error.message ?: getString(R.string.floppy_change_failed) }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = null
                    mountedFloppies[drive] = entry?.displayName
                }
                toast(result ?: getString(FrontendR.string.kairo_media_changed, floppyLabel(drive), entry?.displayName ?: empty()))
                pages.refresh()
            }
        }.start()
    }

    /** A disk picked on the device starts as a game outside the library, once it has been read. */
    private fun chooseHdi() {
        closeMenu()
        DocumentPicker.open(this, HDI_REQUEST, failed = ::toast)
    }

    private fun importFloppyFile(drive: Int, uri: Uri, name: String) {
        if (!beginFloppyChange()) return
        toast(getString(R.string.floppy_importing, name))
        pages.refresh()
        Thread {
            val disk = File(cacheDir, "manual-floppy-$drive-${System.nanoTime()}${Pc98Media.suffix(name)}")
            val result = try {
                FirmwareSet.importFile(this, uri, disk, getString(R.string.floppy), maxBytes = 64L * 1024 * 1024)
                if (!nativeFloppy(drive, disk.absolutePath)) error(getString(R.string.floppy_mount_failed))
                null
            } catch (error: Exception) { error.message ?: getString(R.string.floppy_import_failed) }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = disk
                    mountedFloppies[drive] = name
                } else disk.delete()
                toast(result ?: getString(FrontendR.string.kairo_media_changed, floppyLabel(drive), name))
                pages.refresh()
            }
        }.start()
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

    /** Machine: the base clock, then firmware, which only changes with no game running. */
    override fun machinePage() = GroupsPage(getString(FrontendR.string.kairo_machine), { GameSource.ALL_GAMES }, {
        val baseClock = getString(R.string.base_clock)
        val skipMemoryCheck = getString(FrontendR.string.kairo_option_skip_memory_check)
        listOf(
            RowGroup(getString(R.string.clock_group), listOf(SettingsRow.choice("base-clock", baseClock, ChoiceSpec(baseClock,
                { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, listOf(mhz("25", "2.5"), mhz("20", "2")))) },
                { clock.toString() }, select = { value -> setBaseClock(value.toInt()) }))),
                getString(R.string.base_clock_foot)),
            RowGroup(getString(R.string.boot_group), listOf(SettingsRow.choice(SKIP_MEMORY_CHECK, skipMemoryCheck, ChoiceSpec(
                skipMemoryCheck, { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, listOf(
                    ChoiceOption("on", UiText.res(FrontendR.string.kairo_on)),
                    ChoiceOption("off", UiText.res(FrontendR.string.kairo_off))))) },
                { if (preferences.getBoolean(SKIP_MEMORY_CHECK, true)) "on" else "off" },
                select = { value -> preferences.edit().putBoolean(SKIP_MEMORY_CHECK, value == "on").apply() }))))
        ) + firmwareGroups()
    })

    /** A clock choice: [id] in tenths of a MHz, shown as [mhz] MHz. */
    private fun mhz(id: String, mhz: String) = ChoiceOption(id, UiText.res(FrontendR.string.kairo_clock_mhz, mhz))

    private fun setBaseClock(selected: Int) {
        if (clock == selected) return
        clock = selected
        preferences.edit().putInt("base_clock", clock).apply()
        if (currentEntry == null) return
        if (currentGame?.baseClockTenthsMHz == null) {
            inputRouter.resetLocks()
            nativeClock(clock)
            toast(getString(R.string.clock_reset))
        } else toast(getString(R.string.clock_saved_game_own))
    }

    /** The rows of Kairo98's own catalog settings (Pc98Catalog.settings). */
    override fun customSettingRow(entry: DiskImageEntry, setting: CatalogSetting.Custom,
                                  enabled: () -> Boolean): SettingsRow? {
        val id = entry.key.takeIf { entry.contentId != null }
        val noHash = { GameSettingsRows.noHash(this) }
        val scope = { titleOf(entry) }
        fun game() = catalog.resolve(entry)
        return when (setting.id) {
            Pc98Catalog.BASE_CLOCK -> GameSettingsRows.choice(this, "clock", getString(R.string.machine_clock), scope,
                listOf(mhz("20", "2"), mhz("25", "2.5")),
                machineValue(entry, "machine", "baseClockTenthsMHz", { it.baseClockTenthsMHz?.toString() },
                    { clock.toString() }, GameSource.ALL_GAMES) { it.toInt() }, enabled, noHash)
            Pc98Catalog.GDC_CLOCK -> GameSettingsRows.choice(this, "gdc", getString(R.string.gdc_clock), scope,
                listOf(mhz("25", "2.5"), mhz("50", "5")),
                machineValue(entry, "machine", "gdcClockTenthsMHz", { it.gdcClockTenthsMHz?.toString() },
                    { "50" }, GameSource.BUILT_IN) { it.toInt() }, enabled, noHash)
            Pc98Catalog.CPU -> {
                val base = { if ((game().baseClockTenthsMHz ?: clock) == 20) 1.9968 else 2.4576 }
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
            Pc98Catalog.CHOICES -> automationRow(entry, "startupChoices", R.string.startup_choices,
                R.string.startup_choices_on, enabled)
            Pc98Catalog.SWAPS -> automationRow(entry, "diskSwaps", R.string.disk_swaps, R.string.disk_swaps_on, enabled)
            else -> error("Kairo98 has no row for its catalog setting ${setting.id}")
        }
    }

    /**
     * On or Off for the catalog's startup automation in [field], for a game the catalog gives some;
     * Off keeps an empty list as the game's own.
     */
    private fun automationRow(entry: DiskImageEntry, field: String, label: Int, on: Int,
                              enabled: () -> Boolean): SettingsRow? {
        val id = entry.key.takeIf { entry.contentId != null }
        fun custom() = id != null && catalog.sourceOf(entry, field) == "User override"
        if ((catalog.resolveDefault(entry).record.optJSONArray(field)?.length() ?: 0) == 0 && !custom()) return null
        val value = GameValue({ if (custom()) OFF else null }, { ON },
            { GameSource.label(catalog.defaultSourceOf(entry, field)) },
            { picked -> saveGameSetting(entry) {
                if (picked == OFF) catalog.setOverride(id!!, field, JSONArray()) else catalog.resetOverride(id!!, field)
            } })
        return GameSettingsRows.choice(this, field, getString(label), { titleOf(entry) },
            listOf(ChoiceOption(ON, UiText.res(on)), ChoiceOption(OFF, UiText.res(FrontendR.string.kairo_off))), value,
            enabled, { GameSettingsRows.noHash(this) })
    }

    override fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (fontImport.handle(requestCode, resultCode, data)) return true
        // Firmware picks belong to the shared firmware rows.
        if (requestCode !in HDI_REQUEST..FLOPPY_B_REQUEST) return super.handleProductResult(requestCode, resultCode, data)
        val uri = DocumentPicker.selectedUri(resultCode, data) ?: return true
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (error: Exception) {
            toast(error.message ?: getString(R.string.file_unreadable))
            return true
        }
        val floppy = requestCode != HDI_REQUEST
        if (name == null || !Pc98Media.supported(name) || Pc98Media.isCd(name) ||
            (floppy && !Pc98Media.isFloppy(name))) {
            toast(getString(if (floppy) R.string.select_floppy_image else R.string.select_disk_image))
        } else if (floppy) importFloppyFile(if (requestCode == FLOPPY_A_REQUEST) 0 else 1, uri, name)
        else openExternalFile(uri)
        return true
    }

    /** Every machine start: the PC-98 keyboard starts with Caps and Kana off. */
    private fun startMachine(path: String, fontPath: String, mhzTimesTen: Int, gdcMhzTimesTen: Int, cpuMultiple: Int,
                             floppy: Boolean, bootFloppyPath: String?, secondFloppyPath: String?, cdPath: String?): Boolean {
        inputRouter.resetLocks()
        return nativeStart(path, fontPath, firmwareDir().absolutePath, mhzTimesTen, gdcMhzTimesTen, cpuMultiple, floppy,
            bootFloppyPath, secondFloppyPath, cdPath)
    }

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
        /** The all-games Skip memory check preference, on unless the player turns it off. */
        private const val SKIP_MEMORY_CHECK = "skipMemoryCheck"
        /** The native typer's states (native_bridge.cpp StartupState). */
        private const val STARTUP_IDLE = 0
        private const val STARTUP_TYPING = 1
        private const val STARTUP_STALLED = 2
        private const val STARTUP_FAILED = 3
        /** The core's standard CPU clock multiple, about 49 MHz on the 2.5 MHz base clock. */
        const val DEFAULT_CPU_MULTIPLE = 20
        private const val ON = "on"
        private const val OFF = "off"
        /** Bundled FONT.BMP built by tools/generate_font_bmp.py from redistributable fonts. */
        private const val BUNDLED_FONT = "kairo98-font.bmp"
        private const val HDI_REQUEST = FIRST_PRODUCT_REQUEST
        private const val FLOPPY_A_REQUEST = FIRST_PRODUCT_REQUEST + 1
        private const val FLOPPY_B_REQUEST = FIRST_PRODUCT_REQUEST + 2
        private const val FONT_IMPORT_REQUEST = FIRST_PRODUCT_REQUEST + 3
        private const val BIOS_ROM_BYTES = 0x18000L
        private const val RHYTHM_ROM_BYTES = 0x2000L
        init { System.loadLibrary("kairo98") }
    }
}
