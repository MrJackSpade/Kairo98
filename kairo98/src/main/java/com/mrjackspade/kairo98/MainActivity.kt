package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.ButtonTone
import com.mrjackspade.kairo.frontend.CatalogGame
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
import com.mrjackspade.kairo.frontend.FolderStateSlots
import com.mrjackspade.kairo.frontend.GameKeyMigration
import com.mrjackspade.kairo.frontend.GameNotice
import com.mrjackspade.kairo.frontend.GameSettingsRows
import com.mrjackspade.kairo.frontend.GameSource
import com.mrjackspade.kairo.frontend.GroupsPage
import com.mrjackspade.kairo.frontend.LayeredGameCatalog
import com.mrjackspade.kairo.frontend.LibraryStrings
import com.mrjackspade.kairo.frontend.MachineActivity
import com.mrjackspade.kairo.frontend.MachineBridge
import com.mrjackspade.kairo.frontend.MachineFirstRun
import com.mrjackspade.kairo.frontend.RowGroup
import com.mrjackspade.kairo.frontend.SettingsRow
import com.mrjackspade.kairo.frontend.TrayButton
import com.mrjackspade.kairo.frontend.TraySpec
import com.mrjackspade.kairo.frontend.ValueTone
import com.mrjackspade.kairo.frontend.sameSource

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Surface
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/** Kairo98: the PC-98 on Neko Project 21/W, in the shared Kairo frontend. */
class MainActivity : MachineActivity<DiskImageEntry>() {
    private external fun nativeStart(path: String?, fontPath: String, biosDir: String,
                                     mhzTimesTen: Int, gdcMhzTimesTen: Int,
                                     cpuMultiple: Int, floppy: Boolean, bootFloppyPath: String?,
                                     secondFloppyPath: String?, cdPath: String?): Boolean
    private external fun nativeFloppy(drive: Int, path: String?): Boolean
    private external fun nativeCd(path: String?): Boolean
    private external fun nativeStop()
    private external fun nativePause(paused: Boolean)
    private external fun nativeReset()
    private external fun nativeClock(mhzTimesTen: Int)
    private external fun nativeLoadedLocks(): Int
    private external fun nativeKey(scanCode: Int, down: Boolean)
    private external fun nativeMouseMove(dx: Int, dy: Int)
    private external fun nativeMouseButton(button: Int, down: Boolean)
    private external fun nativeMouseWarp(x: Int, y: Int)
    private external fun nativeMouseWarpDone(): Boolean
    private external fun nativeSaveState(dir: String): Int
    private external fun nativeLoadState(dir: String): Int
    private external fun nativeJoystick(control: Int, down: Boolean)
    private external fun nativeInputTelemetry(): LongArray
    private external fun nativeStatus(): String
    private external fun nativeDosPromptReady(): Boolean
    private external fun nativeSetScreenHashSampling(enabled: Boolean)
    private external fun nativeScreenHashSnapshot(): LongArray
    private external fun nativeSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nativeSetShaderPreset(path: String?)
    private external fun nativeSetMuted(muted: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)

    override val identity = Identity("KAIRO98", 0xffF91CB6.toInt(), "Kairo98", "PC-98", "kairo98", LibraryStrings("KAIRO98", "Kairo98"),
        "Kairo98 uses Neko Project 21/W and ymfm. Source and provenance: github.com/MrJackSpade/Kairo98.",
        "Choose a games folder to find disk images and ZIP games",
        "Folder access expired. Select the games folder again.",
        MachineFirstRun.Folder("Choose a games folder", "Select the folder containing your PC-98 games. Kairo98 will " +
            "scan its disk images and ZIP files.", "Choose games folder…"),
        "SAVE STATES", "MrJackSpade/Kairo98")
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
        override fun loadState(directory: String) = nativeLoadState(directory)
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
        ControllerBindings.JOYSTICK.zip(listOf("Up", "Down", "Left", "Right", "Button 1", "Button 2")),
        "Uses the joystick input on the emulated sound board. Games must support joystick 1.",
        ControllerGuestSpec.STANDARD_ACTIONS, Pc98KeyboardLayout.value)
    override val bindingsCodec = ControllerBindings.codec
    override fun defaultBindings(layout: ControllerLayout) = ControllerBindings.defaults(layout)
    override val artworkDownloadsEnabled: Boolean get() = resources.getBoolean(R.bool.catalog_art_download_enabled)
    override val directTouchAvailable = true
    override val automationOwners = setOf("guest-command", "disk-swap", "debug-auto-space")

    private val disks by lazy { Pc98Media.library(this) }
    override fun createCatalog() = LayeredGameCatalog(this, Pc98Catalog)
    override fun createLibrary(catalog: LayeredGameCatalog): DiskImageLibrary = disks

    // Saved data from before games had keys ----------------------------------------------------------

    /** Save states, settings and working disks from before games had keys move to their games as the library loads. */
    private val gameKeyMigration by lazy {
        GameKeyMigration(preferences, listOf(
            catalog.legacyOverrides(),
            GameKeyMigration.SaveStore({ saveFolder.location }, FolderStateSlots.UNITS, cacheDir,
                File(filesDir, "game-key-migration-v1.json")) { conflicts ->
                saveFolder.resolveMigrationConflicts(conflicts) { key ->
                    claiming.firstOrNull { it.key == key }?.let(::titleOf) ?: key.path
                }
            }), listOf(Pc98Media.legacyWorkingImages(disks), LegacyLastPlayed()))
    }

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
            errors[entry.key]?.let { entry.copy(error = it) } ?: entry
        }
    }

    override fun cachedLibrary(tree: Uri) = claimSavedData(super.cachedLibrary(tree))
    override fun scanLibrary(tree: Uri, forceHash: Boolean, cancelled: AtomicBoolean, progress: (String) -> Unit) =
        claimSavedData(super.scanLibrary(tree, forceHash, cancelled, progress))
    override fun inspectExternal(file: ExternalGameFile, cancelled: AtomicBoolean) =
        claimSavedData(super.inspectExternal(file, cancelled)).onEach { it.error?.let(::error) }

    /** The one disk, else the one hard disk, else the one boot disk; otherwise the player chooses. */
    override fun preferredExternal(playable: List<DiskImageEntry>) = playable.singleOrNull()
        ?: playable.filter { !it.isFloppy }.singleOrNull()
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
    /** The running game uses a FONT.BMP from its own folder instead of the global one. */
    private var gameFontActive = false
    private val gameFonts by lazy { GameFontLocator(contentResolver, File(filesDir, "game-font.bmp")) }
    private var selectedStartup = emptyList<Pair<StartupChoice, StartupOption>>()
    /** Media a launch prepared, which the session takes once the game starts. */
    private class PreparedLaunch(val bootFloppy: DiskImageEntry?, val floppyB: DiskImageEntry?,
                                 val swapSources: Map<String, DiskImageEntry>, val gameFont: Boolean,
                                 val cd: DiskImageEntry?,
                                 /** What started: the launched entry, or a CD's boot disk. */
                                 val boot: DiskImageEntry)
    @Volatile private var prepared: PreparedLaunch? = null
    @Volatile private var commandCancelled = AtomicBoolean(false)
    @Volatile private var floppyBusy = false
    private val samplingLock = Any()
    private var samplingUsers = 0
    private val guestSequenceLock = Any()

    override fun cancelAutomation() {
        commandCancelled.set(true)
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
            toast("${game.title} can't start: its catalog launch data is unreadable (${failure.message})")
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
                "Kairo98 enters your choice when the game reaches this screen. Play manually leaves it to you."))
        }, { null }, { id ->
            val option = choice.options.first { it.id == id }
            pages.postDelayed({
                // Back during the pause cancels, as it does on the page.
                if (pages.top === page) chooseStartupOptions(entry, game, index + 1,
                    selected + (choice to option), fromFrontend, replace = true)
            }, 150)
        }, actions = {
            listOf(SettingsRow.action("manual", "Play manually") { startWithChoices(entry, game, selected, fromFrontend) })
        }, stay = true, cycles = false))
        if (replace) pages.replace(page) else pages.push(page)
    }

    override fun startGame(entry: DiskImageEntry, game: CatalogGame, cancelled: AtomicBoolean, progress: (String) -> Unit) {
        val external = entry !in libraryEntries
        val sources = libraryEntries + listOf(entry).filter { external }
        val cd = cdFor(entry, game.cdId, sources)
        val boot = if (entry.isCd) cdBootDiskFor(entry, game.bootDiskId, sources) else entry
        val media = bootMediaFor(boot, sources, game.requiredBootFloppyId)
        val swapSources = game.diskSwaps.associate { rule -> rule.contentId to requiredFloppyFor(rule.contentId, entry, sources) }
        val floppyB = game.initialFloppyBId?.let { id -> requiredFloppyFor(id, entry, sources) }
        val primary = media?.hardDisk ?: boot
        val disk = disks.prepare(primary, cancelled, progress)
        val bootFloppy = media?.bootFloppy?.let { disks.prepare(it, cancelled, progress) }
        val secondFloppy = floppyB?.let { disks.prepare(it, cancelled, progress) }
        val disc = cd?.let { disks.prepare(it, cancelled, progress) }
        val gameFont = gameFonts.prepare(primary, if (external) null else tree,
            primary.zipEntry?.let { disks.archive(primary, cancelled) }, cancelled)
        val font = gameFont?.absolutePath ?: prepareFont()
        check(startMachine(disk.absolutePath, font, game.baseClockTenthsMHz ?: clock, game.gdcClockTenthsMHz ?: 50,
            game.cpuMultiple ?: DEFAULT_CPU_MULTIPLE, primary.isFloppy, bootFloppy?.absolutePath,
            secondFloppy?.absolutePath, disc?.absolutePath)) { "Unable to start machine" }
        awaitMachineReady()?.let(::error)
        prepared = PreparedLaunch(media?.bootFloppy, floppyB, swapSources, gameFont != null, cd, boot)
    }

    private fun awaitMachineReady(): String? {
        repeat(200) {
            val state = nativeStatus()
            if (state.startsWith("Running") || state.startsWith("Paused")) return null
            if (state.startsWith("Error")) return state.substringBefore(" | ")
            Thread.sleep(50)
        }
        return "Machine startup timed out"
    }

    override fun gameStarted(entry: DiskImageEntry, game: CatalogGame) {
        val launched = checkNotNull(prepared) { "The launch prepared no media" }
        prepared = null
        gameFontActive = launched.gameFont
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
        gameFontActive = false
        currentIsFloppy = false
        mountedFloppies.fill(null)
        manualFloppies.fill(null)
        mountedCd = null
        selectedStartup = emptyList()
    }

    /** Native media replacement may require restarting from the original disks. */
    override fun loadRejected(code: Int) {
        if (code > 3) currentEntry?.let { entry ->
            userPaused = false
            closeMenu()
            launch(entry, sessionFromFrontend)
        }
    }

    override fun stateError(code: Int) = when (code) {
        1 -> "machine not running"
        2 -> "state is missing or from another version"
        3 -> "storage unavailable"
        4 -> "disk image copy failed"
        5 -> "state file unreadable"
        6 -> "machine did not respond"
        else -> "code $code"
    }

    override fun sessionDetails() = listOfNotNull(when {
        currentEntry == null -> null
        currentIsFloppy -> "Floppy disk"
        else -> "Hard disk"
    })

    override fun diagnosticsRows(running: Boolean): List<SettingsRow> {
        if (!running) return listOf(SettingsRow.info("state", "State") { "Stopped" })
        fun part(index: Int, prefix: String = "") =
            nativeStatus().split(" | ").getOrNull(index)?.removePrefix(prefix)?.trim() ?: ""
        return listOf(
            SettingsRow.info("state", "State") { part(0) },
            SettingsRow.info("frames", "Frames") { part(1, "frames") },
            SettingsRow.info("csip", "CS:IP") { part(2, "CS:IP") },
            SettingsRow.info("audio", "Audio") { part(3, "audio") },
            SettingsRow.info("xruns", "Xruns") { part(4, "xruns") },
            SettingsRow.info("audio-config", "Audio config") { part(5) },
            SettingsRow.info("audio-profile", "Audio profile") { part(6) })
    }

    // Startup automation: typed commands and choices on recognized screens, and disk swaps ------------

    private fun scheduleStartupQueue(game: CatalogGame?, choices: List<Pair<StartupChoice, StartupOption>>) {
        commandCancelled.set(true)
        if (game == null) return
        val steps = (if (skipDebugCommands) emptyList() else game.launchCommands).mapIndexed { index, command ->
            val hashes = game.launchScreenHashes.getOrNull(index) ?: emptySet()
            StartupHashMatcher.Step("Launch command ${index + 1}: $command", command, true,
                hashes, hashes.isEmpty(), game.launchTimeoutMs)
        } + choices.flatMap { (choice, option) ->
            option.inputs.mapIndexed { index, input ->
                StartupHashMatcher.Step("${choice.title}: ${option.label} (${index + 1}/${option.inputs.size})",
                    input.key.toString(), input.enter, input.screenHashes, false, 120000)
            }
        }
        scheduleStartupSteps(steps)
    }

    private fun acquireScreenHashes() = synchronized(samplingLock) {
        if (samplingUsers++ == 0 && !traceScreenHashes) nativeSetScreenHashSampling(true)
    }

    private fun releaseScreenHashes() = synchronized(samplingLock) {
        if (--samplingUsers == 0 && !traceScreenHashes) nativeSetScreenHashSampling(false)
    }

    private fun scheduleStartupSteps(steps: List<StartupHashMatcher.Step>) {
        val generation = sessionGeneration
        if (steps.isEmpty()) {
            scheduleDebugAutoAdvance(generation)
            return
        }
        val cancelled = AtomicBoolean(false)
        commandCancelled = cancelled
        acquireScreenHashes()
        Thread {
            try {
                val ready = StartupHashMatcher(::nativeScreenHashSnapshot, ::nativeDosPromptReady,
                    { nativeStatus().startsWith("Running") },
                    { cancelled.get() || !current(generation) },
                    { step -> sendStartupKeys(step, cancelled, generation) },
                    { step -> runOnUiThread {
                        if (!cancelled.get() && current(generation)) showStartupTimeout(steps, step)
                    } }).run(steps)
                if (ready && !cancelled.get() && current(generation)) scheduleDebugAutoAdvance(generation)
            } catch (error: Exception) {
                runOnUiThread {
                    if (!cancelled.get() && current(generation)) toast(error.message ?: "Startup input failed")
                }
            } finally {
                inputRouter.release("guest-command")
                releaseScreenHashes()
            }
        }.start()
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

    private fun sendStartupKeys(step: StartupHashMatcher.Step, cancelled: AtomicBoolean,
                                generation: Int, source: String = "guest-command") = synchronized(guestSequenceLock) {
        for (character in step.text) {
            if (cancelled.get() || !current(generation)) return
            val scans = guestCommandScans(character) ?: error("Unsupported startup character: $character")
            inputRouter.hold(source, scans)
            try { Thread.sleep(70) } finally { inputRouter.release(source) }
            Thread.sleep(70)
        }
        if (step.enter && !cancelled.get() && current(generation)) {
            inputRouter.hold(source, listOf(0x1c))
            try { Thread.sleep(70) } finally { inputRouter.release(source) }
        }
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
        val notice = GameNotice("Swapping Floppy ${'A' + rule.drive}: ${entry.displayName}", "")
        try {
            runOnUiThread { if (current(generation)) overlay.showNotice(notice) }
            val disk = disks.prepare(entry, AtomicBoolean(false)) { message ->
                runOnUiThread {
                    if (current(generation) && overlay.showing(notice))
                        overlay.showNotice(GameNotice(notice.title, message).also { swapping = it })
                }
            }
            if (!current(generation)) return
            if (!nativeFloppy(rule.drive, disk.absolutePath)) error("Floppy could not be mounted")
            runOnUiThread {
                if (current(generation)) {
                    manualFloppies[rule.drive]?.delete()
                    manualFloppies[rule.drive] = null
                    mountedFloppies[rule.drive] = entry.displayName
                }
            }
            if (rule.key.isNotEmpty() || rule.enter) {
                Thread.sleep(150)
                sendStartupKeys(StartupHashMatcher.Step(rule.id, rule.key, rule.enter, emptySet(), false, 0),
                    AtomicBoolean(false), generation, "disk-swap")
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
        overlay.showNotice(GameNotice("Automatic disk swap failed",
            "${rule?.let { "Floppy ${'A' + it.drive}: " } ?: ""}" +
                (error.message ?: "Unknown error") + ". The game is still running.",
            listOf(TrayButton("Continue manually"),
                TrayButton("Choose floppy") {
                    openMenu()
                    pages.push(disksPage())
                    pages.push(ChoicePage(floppyChoice(rule?.drive ?: 0), "floppy${rule?.drive ?: 0}"))
                },
                TrayButton("Retry", ButtonTone.PRIMARY) { scheduleDiskSwaps(rules, sources) })))
    }

    private fun showStartupTimeout(steps: List<StartupHashMatcher.Step>, step: StartupHashMatcher.Step) {
        overlay.showNotice(GameNotice("Startup screen not recognized",
            "Waiting for ${step.label}. The game is still running.",
            listOf(TrayButton("Restart") { openMenu("f-restart", confirm = true) },
                TrayButton("Open keyboard") { showKeyboard() },
                TrayButton("Retry", ButtonTone.PRIMARY) {
                    scheduleStartupSteps(steps.drop(steps.indexOf(step).coerceAtLeast(0)))
                })))
    }

    private fun guestCommandScans(character: Char): List<Int>? = when {
        character in 'a'..'z' -> pc98ScanCode(KeyEvent.KEYCODE_A + (character - 'a'))?.let(::listOf)
        character in 'A'..'Z' -> pc98ScanCode(KeyEvent.KEYCODE_A + (character - 'A'))?.let { listOf(0x70, it) }
        character in '0'..'9' -> pc98ScanCode(if (character == '0') KeyEvent.KEYCODE_0
            else KeyEvent.KEYCODE_1 + (character - '1'))?.let(::listOf)
        else -> (when (character) {
            ' ' -> 0x34
            '\\' -> 0x0d
            '/' -> 0x32
            '.' -> 0x31
            '-' -> 0x0b
            ':' -> 0x27
            '_' -> 0x33
            else -> null
        })?.let(::listOf)
    }

    // Direct tap: the mouse warps to the touched point before a click ----------------------------------

    private var pendingMouseWarp: Runnable? = null

    /** The view is exactly the scaled frame, so the point already accounts for scaling and crop. */
    override fun warpMouse(x: Int, y: Int) = nativeMouseWarp(x, y)

    override fun afterMouseWarp(action: () -> Unit) {
        releaseMouseTouch()
        if (nativeMouseWarpDone()) {
            action()
            return
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 2000
        val poll = object : Runnable {
            override fun run() {
                if (nativeMouseWarpDone()) {
                    pendingMouseWarp = null
                    action()
                } else if (android.os.SystemClock.uptimeMillis() < deadline) handler.postDelayed(this, 8)
                else pendingMouseWarp = null
            }
        }
        pendingMouseWarp = poll
        handler.postDelayed(poll, 8)
    }

    override fun releaseMouseTouch() {
        pendingMouseWarp?.let(handler::removeCallbacks)
        pendingMouseWarp = null
    }

    // Disks ------------------------------------------------------------------------------------------

    override fun machineRows() = listOf(SettingsRow.nav("f-disks", "Disks") { pages.push(disksPage()) })

    /** Disks: the hard disk, which asks first because it ends the game, both floppy drives, and the CD-ROM. */
    private fun disksPage() = GroupsPage("Disks", { currentGame?.title }, {
        listOf(RowGroup("DRIVES", listOf(
            SettingsRow.action("hdd", "Hard disk",
                value = { if (currentIsFloppy) "Empty" else currentEntry?.displayName ?: "Empty" },
                tone = { if (currentEntry == null || currentIsFloppy) ValueTone.EMPTY else ValueTone.CURRENT },
                tray = {
                    if (currentEntry == null) TraySpec(buttons = listOf(TrayButton("Choose file…", ButtonTone.PRIMARY) { chooseHdi() }))
                    else TraySpec("Starting another disk image ends this game. Progress since your last save is lost.",
                        listOf(TrayButton("Cancel"), TrayButton("Choose file…", ButtonTone.PRIMARY) { chooseHdi() }), focus = 0)
                }),
            floppyRow(0), floppyRow(1), cdRow())))
    })

    private fun cdRow() = SettingsRow.choice("cd", "CD-ROM", cdChoice(),
        value = { mountedCd ?: "Empty" },
        tone = { if (mountedCd == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !floppyBusy },
        disabledReason = { if (floppyBusy) "Wait for the disk change to finish" else "Start a game before inserting a CD" })

    /** Empty ejects; otherwise the game's catalog disc and the CDs in its ZIP or folder. */
    private fun cdChoice(): ChoiceSpec {
        fun candidates(): List<DiskImageEntry> {
            val current = currentEntry ?: return emptyList()
            val named = currentGame?.cdId
            return libraryEntries.filter { entry ->
                entry.playable && entry.isCd && (catalog.matchId(entry) == named || sameSource(current, entry))
            }.distinctBy { it.contentId }
        }
        return ChoiceSpec("CD-ROM", { currentGame?.title }, {
            val found = candidates()
            listOfNotNull(ChoiceGroup(null, listOf(ChoiceOption("empty", "Empty")),
                if (found.isEmpty()) "No CDs for this game in the library." else null),
                found.takeIf { it.isNotEmpty() }?.let { items ->
                    ChoiceGroup("FOR THIS GAME", items.map { ChoiceOption(it.id, it.displayName) })
                })
        }, { candidates().firstOrNull { it.displayName == mountedCd }?.id
            ?: if (mountedCd == null) "empty" else null }, { id ->
            if (id == "empty") changeCd(null)
            else candidates().firstOrNull { it.id == id }?.let(::changeCd)
        }, cycles = false)
    }

    private fun changeCd(entry: DiskImageEntry?) {
        if (!beginFloppyChange()) return
        toast(if (entry == null) "Ejecting CD…" else "Preparing ${entry.displayName}…")
        pages.refresh()
        Thread {
            val result = try {
                val disc = entry?.let { disks.prepare(it, AtomicBoolean(false)) { } }
                if (!nativeCd(disc?.absolutePath)) error("CD could not be mounted")
                null
            } catch (error: Exception) { error.message ?: "CD change failed" }
            runOnUiThread {
                endFloppyChange()
                if (result == null) mountedCd = entry?.displayName
                toast(result ?: "CD-ROM: ${entry?.displayName ?: "Empty"}")
                pages.refresh()
            }
        }.start()
    }

    /** The floppies and CD, by drive, for the UI tests (KairoActivity.gameProbe). */
    override fun probeDetails(): Map<String, String> = super.probeDetails() + mapOf(
        "media.Floppy A" to (mountedFloppies[0] ?: ""), "media.Floppy B" to (mountedFloppies[1] ?: ""),
        "media.CD-ROM" to (mountedCd ?: ""))

    private fun floppyRow(drive: Int) = SettingsRow.choice("floppy$drive", "Floppy ${'A' + drive}", floppyChoice(drive),
        value = { mountedFloppies[drive] ?: "Empty" },
        tone = { if (mountedFloppies[drive] == null) ValueTone.EMPTY else ValueTone.CURRENT },
        enabled = { machineRunning && !floppyBusy },
        disabledReason = { if (floppyBusy) "Wait for the floppy change to finish" else "Start a game before inserting a floppy" })

    /** Empty ejects; other disks from the same ZIP or folder; or a file from the device. */
    private fun floppyChoice(drive: Int): ChoiceSpec {
        fun candidates(): List<DiskImageEntry> {
            val current = currentEntry ?: return emptyList()
            return libraryEntries.filter { entry -> entry.playable && entry.isFloppy && sameSource(current, entry) }
        }
        return ChoiceSpec("Floppy ${'A' + drive}", { currentGame?.title }, {
            val found = candidates()
            listOfNotNull(ChoiceGroup(null, listOf(ChoiceOption("empty", "Empty")),
                if (found.isEmpty()) "No other disk images in this game’s folder." else null),
                found.takeIf { it.isNotEmpty() }?.let { items ->
                    ChoiceGroup("IN THIS GAME’S FOLDER", items.map { ChoiceOption(it.id, it.displayName) })
                })
        }, { candidates().firstOrNull { it.displayName == mountedFloppies[drive] }?.id
            ?: if (mountedFloppies[drive] == null) "empty" else null }, { id ->
            if (id == "empty") changeFloppy(drive, null)
            else candidates().firstOrNull { it.id == id }?.let { changeFloppy(drive, it) }
        }, actions = {
            listOf(SettingsRow.action("choose", "Choose file…") {
                DocumentPicker.open(this, if (drive == 0) FLOPPY_A_REQUEST else FLOPPY_B_REQUEST, failed = ::toast)
            })
        }, cycles = false)
    }

    private fun changeFloppy(drive: Int, entry: DiskImageEntry?) {
        if (!beginFloppyChange()) return
        toast(if (entry == null) "Ejecting floppy ${'A' + drive}…" else "Preparing ${entry.displayName}…")
        pages.refresh()
        Thread {
            val result = try {
                val disk = entry?.let { disks.prepare(it, AtomicBoolean(false)) { } }
                if (!nativeFloppy(drive, disk?.absolutePath)) error("Floppy could not be mounted")
                null
            } catch (error: Exception) { error.message ?: "Floppy change failed" }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = null
                    mountedFloppies[drive] = entry?.displayName
                }
                toast(result ?: "Floppy ${'A' + drive}: ${entry?.displayName ?: "Empty"}")
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
        toast("Importing $name…")
        pages.refresh()
        Thread {
            val disk = File(cacheDir, "manual-floppy-$drive-${System.nanoTime()}${Pc98Media.suffix(name)}")
            val result = try {
                FirmwareSet.importFile(contentResolver, uri, disk, "Floppy", maxBytes = 64L * 1024 * 1024)
                if (!nativeFloppy(drive, disk.absolutePath)) error("Floppy could not be mounted")
                null
            } catch (error: Exception) { error.message ?: "Floppy import failed" }
            runOnUiThread {
                endFloppyChange()
                if (result == null) {
                    manualFloppies[drive]?.delete()
                    manualFloppies[drive] = disk
                    mountedFloppies[drive] = name
                } else disk.delete()
                toast(result ?: "Floppy ${'A' + drive}: $name")
                pages.refresh()
            }
        }.start()
    }

    // Machine and firmware ---------------------------------------------------------------------------


    /** The PC-98's optional firmware, on the shared firmware rows; Kairo98 has a replacement for each. */
    override val firmwareSets by lazy {
        listOf("PC-98 BIOS" to FirmwareSet(firmwareDir(), listOf(
            FirmwareSet.Part("bios.rom", "BIOS", "bios.rom · the PC-98's own BIOS; Kairo98 has a built-in one",
                setOf("bios.rom"), false, setOf(BIOS_ROM_BYTES)),
            FirmwareSet.Part("font.bmp", "Font", "font.bmp · the text and kanji font; Kairo98 has a built-in one",
                setOf("font.bmp"), false, maxBytes = Pc98FontBitmap.MAX_BYTES, validate = Pc98FontBitmap::validate,
                status = { if (gameFontActive) "Game folder" else null }, missing = "Built-in",
                removeLabel = "Use built-in"),
            FirmwareSet.Part("ym2608_adpcm_rom.bin", "Rhythm samples",
                "ym2608_adpcm_rom.bin · the sound board's drum sounds", setOf("ym2608_adpcm_rom.bin"), false,
                setOf(RHYTHM_ROM_BYTES)))))
    }

    /** Machine: the base clock, then firmware, which only changes with no game running. */
    override fun machinePage() = GroupsPage("Machine", { GameSource.ALL_GAMES }, {
        listOf(
            RowGroup("CLOCK", listOf(SettingsRow.choice("base-clock", "Base clock", ChoiceSpec("Base clock",
                { GameSource.ALL_GAMES }, { listOf(ChoiceGroup(null, listOf(
                    ChoiceOption("25", "2.5 MHz"), ChoiceOption("20", "2 MHz")))) },
                { clock.toString() }, select = { value -> setBaseClock(value.toInt()) }))),
                "Changing the base clock restarts a running game. Games with their own clock keep it.")
        ) + firmwareGroups()
    })

    private fun setBaseClock(selected: Int) {
        if (clock == selected) return
        clock = selected
        preferences.edit().putInt("base_clock", clock).apply()
        if (currentEntry == null) return
        if (currentGame?.baseClockTenthsMHz == null) {
            inputRouter.resetLocks()
            nativeClock(clock)
            toast("Clock change resets the machine")
        } else toast("Global clock saved; this game uses its own clock")
    }

    override fun machineGroup(entry: DiskImageEntry, enabled: () -> Boolean): RowGroup {
        val id = entry.key.takeIf { entry.contentId != null }
        val noHash = { GameSettingsRows.NO_HASH }
        val scope = { titleOf(entry) }
        fun game() = catalog.resolve(entry)
        val base = { if ((game().baseClockTenthsMHz ?: clock) == 20) 1.9968 else 2.4576 }
        val multiples = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, DEFAULT_CPU_MULTIPLE)
        val cpu = machineValue(entry, "machine", "cpuMultiple", { it.cpuMultiple?.toString() },
            { DEFAULT_CPU_MULTIPLE.toString() }, GameSource.BUILT_IN) { it.toInt() }
        return RowGroup("MACHINE", listOf(
            GameSettingsRows.choice("clock", "Machine clock", scope,
                listOf(ChoiceOption("20", "2 MHz"), ChoiceOption("25", "2.5 MHz")),
                machineValue(entry, "machine", "baseClockTenthsMHz", { it.baseClockTenthsMHz?.toString() },
                    { clock.toString() }, GameSource.ALL_GAMES) { it.toInt() }, enabled, noHash),
            GameSettingsRows.choice("gdc", "GDC clock", scope,
                listOf(ChoiceOption("25", "2.5 MHz"), ChoiceOption("50", "5 MHz")),
                machineValue(entry, "machine", "gdcClockTenthsMHz", { it.gdcClockTenthsMHz?.toString() },
                    { "50" }, GameSource.BUILT_IN) { it.toInt() }, enabled, noHash),
            GameSettingsRows.choice("cpu", "CPU speed", scope,
                multiples.map { ChoiceOption("$it", "%.1f MHz".format(base() * it), "×$it") },
                cpu, enabled, noHash,
                defaultLabel = { cpu.fallback().toInt().let { "%.1f MHz (×%d)".format(base() * it, it) } }),
            GameSettingsRows.text("command", "Startup command", "DOS command, such as GAME",
                { game().launchCommand }, { id != null && catalog.sourceOf(entry, "launch", "text") == "User override" },
                { id?.let { catalog.sourceOf(entry, "launch") }?.takeIf { it != "App default" }?.let(GameSource::label) },
                { value -> saveGameSetting(entry) {
                    catalog.updateOverrideSubfields(id!!, "launch",
                        mapOf("text" to value.ifEmpty { null }, "commands" to null, "screenHashes" to null))
                } }, { saveGameSetting(entry) { catalog.resetOverride(id!!, "launch") } },
                enabled = enabled, disabledReason = noHash)),
            "Machine changes apply on the next launch or restart.")
    }

    override fun handleProductResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        // Firmware picks belong to the shared firmware rows.
        if (requestCode !in HDI_REQUEST..FLOPPY_B_REQUEST) return super.handleProductResult(requestCode, resultCode, data)
        val uri = DocumentPicker.selectedUri(resultCode, data) ?: return true
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (error: Exception) {
            toast(error.message ?: "Could not read selected file")
            return true
        }
        val floppy = requestCode != HDI_REQUEST
        if (name == null || !Pc98Media.supported(name) || Pc98Media.isCd(name) ||
            (floppy && !Pc98Media.isFloppy(name))) {
            toast(if (floppy) "Select a supported floppy image" else "Select a supported disk image")
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

    /** The imported FONT.BMP, or the bundled Kairo98 font when none is imported. */
    private fun prepareFont(): String {
        val bitmap = fontBitmapFile()
        if (bitmap.isFile) return bitmap.absolutePath
        val bundled = File(filesDir, BUNDLED_FONT)
        val data = assets.open("font/$BUNDLED_FONT").use { it.readBytes() }
        if (!bundled.isFile || bundled.length() != data.size.toLong() || !bundled.readBytes().contentEquals(data)) {
            val partial = File(filesDir, "$BUNDLED_FONT.part")
            try {
                partial.writeBytes(data)
                Files.move(partial.toPath(), bundled.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                partial.delete()
            }
        }
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
        private const val HDI_REQUEST = FIRST_PRODUCT_REQUEST
        private const val FLOPPY_A_REQUEST = FIRST_PRODUCT_REQUEST + 1
        private const val FLOPPY_B_REQUEST = FIRST_PRODUCT_REQUEST + 2
        private const val BIOS_ROM_BYTES = 0x18000L
        private const val RHYTHM_ROM_BYTES = 0x2000L
        init { System.loadLibrary("kairo98") }
    }
}
