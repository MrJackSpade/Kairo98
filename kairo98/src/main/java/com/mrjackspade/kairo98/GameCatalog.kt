package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.CatalogFieldLayers
import com.mrjackspade.kairo.frontend.GameKey
import com.mrjackspade.kairo.frontend.GameKeyMigration
import com.mrjackspade.kairo.frontend.GameMetadataOverrides
import com.mrjackspade.kairo.frontend.LibraryItem
import com.mrjackspade.kairo.frontend.LocalCatalogFile
import com.mrjackspade.kairo.frontend.CatalogArtworkStore
import com.mrjackspade.kairo.frontend.ArtworkOverridePath
import com.mrjackspade.kairo.frontend.CatalogSnapshotStore
import com.mrjackspade.kairo.frontend.LibraryCatalog
import com.mrjackspade.kairo.frontend.LibraryGame

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Versioned, data-only metadata. Catalog records are named by content ID; the user's own
 * overrides belong to a game's key. Nothing in this file is executed by Android.
 */
class GameCatalog(private val context: Context) : LibraryCatalog {
    data class StartupInput(val key: Char, val enter: Boolean, val screenHashes: Set<Long>)
    data class StartupOption(val id: String, val label: String, val inputs: List<StartupInput>)
    data class StartupChoice(val id: String, val title: String, val screenHashes: Set<Long>,
                             val options: List<StartupOption>)
    data class DiskSwap(val id: String, val drive: Int, val contentId: String,
                        val screenHashes: Set<Long>, val key: String, val enter: Boolean)
    val artworkStore = CatalogArtworkStore(context, ::validImageUrl)
    data class ArtworkSource(val path: String, val url: String)
    data class Game(
        val contentId: String,
        override val title: String,
        override val description: String?,
        val heart: Boolean?,
        override val boxArt: String?,
        override val preview: String?,
        val boxArtUrl: String?,
        val previewUrl: String?,
        val boxArtCatalogPath: String?,
        val previewCatalogPath: String?,
        val baseClockTenthsMHz: Int?,
        val gdcClockTenthsMHz: Int?,
        val cpuMultiple: Int?,
        val controllerProfile: String?,
        val controllerBindings: String?,
        val inputMode: String?,
        val inputTouch: String?,
        val inputSecondary: String?,
        val requiredBootFloppyId: String?,
        val initialFloppyBId: String?,
        /** The disc this game needs in its CD-ROM drive. */
        val cdId: String?,
        /** For a CD: the hard disk or floppy it boots from. */
        val bootDiskId: String?,
        /** Launch data stays as stored until a launch reads it; malformed data fails that launch. */
        private val launch: JSONObject?,
        private val launchHashesCurrent: Boolean,
        private val startupChoiceData: org.json.JSONArray?,
        private val diskSwapData: org.json.JSONArray?,
        val launchTimeoutMs: Int,
        val overriddenFields: Set<String>,
        val controllerDefaultBindings: String? = null
    ) : LibraryGame {
        override val tags: List<String> get() = if (heart == true) listOf("♥") else emptyList()

        val launchCommands: List<String> by lazy {
            launch?.optJSONArray("commands")?.let { array -> (0 until array.length()).map(array::getString) }
                ?: launch?.optString("text")?.takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
        }
        val launchCommand: String? get() = launchCommands.joinToString("; ").takeIf { launchCommands.isNotEmpty() }
        /** Screen hashes apply only when they were stored with these commands, one group per command. */
        val launchScreenHashes: List<Set<Long>> by lazy {
            launch?.optJSONArray("screenHashes")?.takeIf { launchHashesCurrent && it.length() == launchCommands.size }
                ?.let { groups -> (0 until groups.length()).map { parseHashes(groups.getJSONArray(it)) } }
                ?: emptyList()
        }
        val startupChoices: List<StartupChoice> by lazy {
            val array = startupChoiceData ?: return@lazy emptyList()
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val options = item.getJSONArray("options")
                StartupChoice(item.getString("id"), item.getString("title"),
                    parseHashes(item.getJSONArray("screenHashes")),
                    (0 until options.length()).map { optionIndex ->
                        val option = options.getJSONObject(optionIndex)
                        val steps = option.optJSONArray("steps")
                        val inputs = if (steps == null) listOf(StartupInput(
                            option.getString("key")[0], option.getBoolean("enter"),
                            parseHashes(item.getJSONArray("screenHashes"))))
                        else (0 until steps.length()).map { stepIndex ->
                            val step = steps.getJSONObject(stepIndex)
                            StartupInput(step.getString("key")[0], step.getBoolean("enter"),
                                parseHashes(step.getJSONArray("screenHashes")))
                        }
                        StartupOption(option.getString("id"), option.getString("label"), inputs)
                    })
            }
        }
        val diskSwaps: List<DiskSwap> by lazy {
            val array = diskSwapData ?: return@lazy emptyList()
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DiskSwap(item.getString("id"), item.getInt("drive"),
                    item.getString("contentId"), parseHashes(item.getJSONArray("screenHashes")),
                    item.getString("key"), item.getBoolean("enter"))
            }
        }
    }

    override val installedCatalogs by lazy {
        com.mrjackspade.kairo.frontend.InstalledCatalogs(context, "pc98", { root ->
            requireUpdateFormat(root, false)
        }) { synchronized(this) { fallbackRecords.clear(); updatedFallbackIds.clear(); invalidate() } }
    }
    private val parts = com.mrjackspade.kairo.frontend.CatalogParts(context)
    private val base = readAsset("catalog/base-v1.json")
    // Hash matches and downloaded name matches do not need the bundled fallback index.
    private val nameIndex by lazy { readAsset("catalog/name-index-v1.json") }
    private val shardNames = readShardNames()
    private val shardCache = object : android.util.LruCache<String, JSONObject>(8) {}
    private val fallbackRecords = HashMap<String, JSONObject>()
    // A game's merged record and resolved metadata are reused until a catalog, override or
    // artwork change; settings pages read them for every row and every focus move.
    /** [owner] is the game whose overrides apply; null for its catalog record alone. */
    private data class MemoKey(val contentId: String, val owner: GameKey?, val fileName: String?)
    private val layeredMemo = HashMap<MemoKey, CatalogFieldLayers.Result>()
    private val resolvedMemo = HashMap<MemoKey, Game>()
    private fun invalidate() {
        layeredMemo.clear()
        resolvedMemo.clear()
    }

    /** Only this game's overrides changed. */
    private fun invalidate(owner: GameKey) {
        layeredMemo.keys.removeAll { it.owner == owner }
        resolvedMemo.keys.removeAll { it.owner == owner }
    }
    private val knownFileNames = HashMap<String, String>()
    private val updatedFallbackIds = HashSet<String>()
    private val additionsFile = File(context.filesDir, "user-catalog-v1.json")
    private val overridesFile = File(context.filesDir, "overrides-v1.json")
    private val snapshot = CatalogSnapshotStore(context, "core-update-v3.json",
        UPDATE_URL, METADATA_URL, MAX_LOCAL_JSON) { file ->
        requireUpdateFormat(JSONObject(file.readText()))
    }
    private var update = readUpdate()
    private var additions = readLocal(additionsFile)
    private val overrides = GameMetadataOverrides(overridesFile, MAX_LOCAL_JSON.toInt(),
        schemaEnvelope = true)

    @Synchronized fun reloadAdditions() {
        additions = readLocal(additionsFile)
        invalidate()
    }

    /** Call from a worker thread. An invalid or interrupted download leaves the current catalog intact. */
    fun downloadUpdate(task: com.mrjackspade.kairo.frontend.CatalogUpdateTask = com.mrjackspade.kairo.frontend.CatalogUpdateTask()): Boolean {
        if (!snapshot.download(task)) return false
        synchronized(this) {
            update = readUpdate()
            fallbackRecords.clear()
            updatedFallbackIds.clear()
            invalidate()
        }
        return true
    }

    @Synchronized fun sourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", item.key, item.displayName).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** Where a setting would come from without the user's own change, for its Default option. */
    @Synchronized fun defaultSourceOf(item: LibraryItem, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(matchId(item) ?: "", null, item.displayName).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    /** The game without the user's own changes, so each Default option can show its value. */
    @Synchronized fun resolveDefault(item: LibraryItem): Game = resolve(matchId(item) ?: "", null, item.displayName)

    private fun layered(contentId: String, owner: GameKey?, fileName: String? = null): CatalogFieldLayers.Result {
        if (fileName != null && contentId.isNotEmpty()) knownFileNames[contentId] = fileName
        val key = MemoKey(contentId, owner, fileName ?: knownFileNames[contentId])
        return layeredMemo.getOrPut(key) { layeredUncached(key) }
    }

    private fun layeredUncached(key: MemoKey): CatalogFieldLayers.Result {
        val contentId = key.contentId
        val fileName = key.fileName
        val baseRecord = base.optJSONObject("games")?.optJSONObject(contentId)
        val shardRecord = shardFor(contentId)?.optJSONObject("games")?.optJSONObject(contentId)
        val updateRecord = update.optJSONObject("games")?.optJSONObject(contentId)
        var fallback = fallbackRecords[contentId]
        if (baseRecord == null && shardRecord == null && updateRecord == null && fileName != null) {
            lookupByName(fileName)?.let { (record, fromUpdate) ->
                fallback = record
                if (contentId.isNotEmpty()) {
                    fallbackRecords[contentId] = record
                    if (fromUpdate) updatedFallbackIds.add(contentId) else updatedFallbackIds.remove(contentId)
                }
            }
        } else if (baseRecord != null || shardRecord != null || updateRecord != null) fallback = null
        val name = fileName
        val userRecord = additions.optJSONObject("games")?.optJSONObject(contentId)
        val overrideRecord = key.owner?.let(overrides::record)
        val installed = installedCatalogs.catalogs()
        // A disk record names its game's shared record. Each layer adds its own copy of that
        // record beneath its disk records, so the disk's own fields override it in every layer.
        val game = (listOf(overrideRecord, userRecord) +
            installed.asReversed().map { it.data.getJSONObject("games").optJSONObject(contentId) } +
            listOf(updateRecord, shardRecord, baseRecord))
            .firstNotNullOfOrNull { record -> record?.optString("game")?.takeIf { it.isNotEmpty() } }
        fun shared(games: JSONObject?) = game?.let { games?.optJSONObject("games")?.optJSONObject(it) }
        return CatalogFieldLayers.merge(listOf(
            CatalogFieldLayers.Source("Shipped catalog", shared(nameIndex)),
            CatalogFieldLayers.Source("Shipped catalog", baseRecord),
            CatalogFieldLayers.Source("Shipped catalog", shardRecord),
            CatalogFieldLayers.Source(if (contentId in updatedFallbackIds) "Updated catalog"
                else "Shipped catalog", fallback),
            CatalogFieldLayers.Source("Updated catalog", shared(update.optJSONObject("nameIndex"))),
            CatalogFieldLayers.Source("Updated catalog", updateRecord),
            CatalogFieldLayers.Source("Shipped catalog", shared(parts.additional("name-index-v1.json"))),
            CatalogFieldLayers.Source("Shipped catalog", bundledAdditional(contentId, name)),
            *installedSources(installed, contentId, name, ::shared).toTypedArray(),
            CatalogFieldLayers.Source("User catalog", userRecord),
            CatalogFieldLayers.Source("User override", overrideRecord)
        ).map { source -> source.copy(record = Pc98ArtworkReferences.record(source.record)) },
            { path -> path.size == 1 && path[0] in OBJECT_FIELDS },
            { path, record -> if (path == listOf("launch")) when {
                record.has("text") -> setOf("commands")
                record.has("commands") -> setOf("text")
                else -> emptySet()
            } else emptySet() })
    }

    @Synchronized override fun resolve(item: LibraryItem): Game =
        resolve(matchId(item) ?: "", item.key, item.displayName)

    /** A catalog record alone, as for a disk a game names that is not a library entry. */
    @Synchronized fun resolve(contentId: String, fileName: String): Game = resolve(contentId, null, fileName)

    private fun resolve(contentId: String, owner: GameKey?, fileName: String): Game =
        resolvedMemo.getOrPut(MemoKey(contentId, owner, fileName)) {
            resolveUncached(contentId, owner, fileName)
        }

    // An unedited game needs no lookup, so startup reads no shards for it.
    @Synchronized override fun matchId(item: LibraryItem): String? {
        val current = item.contentId ?: return item.catalogId
        val first = item.catalogId ?: return current
        return if (current == first || known(current)) current else first
    }

    /** Whether any catalog has a record for [contentId]. */
    private fun known(contentId: String): Boolean {
        fun has(source: JSONObject?) = source?.optJSONObject("games")?.has(contentId) == true
        return has(base) || has(shardFor(contentId)) || has(update) || has(additions) ||
            has(parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")) ||
            installedCatalogs.sources(contentId).any { it.record != null }
    }

    private fun resolveUncached(contentId: String, owner: GameKey?, fileName: String): Game {
        val layers = layered(contentId, owner, fileName)
        val merged = layers.record
        val user = owner?.let(overrides::record)
        val title = merged.optString("title").takeIf { it.isNotBlank() }
            ?: fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val artwork = merged.optJSONObject("artwork")
        val machine = merged.optJSONObject("machine")
        val controller = merged.optJSONObject("controller")
        val input = merged.optJSONObject("input")
        val media = merged.optJSONArray("media")
        val launch = merged.optJSONObject("launch")
        val commandSource = maxOf(layers.sourceIndexOf("launch", "text") ?: -1,
            layers.sourceIndexOf("launch", "commands") ?: -1)
        val hashSource = layers.sourceIndexOf("launch", "screenHashes") ?: -1
        // Paths and URLs are checked where an image is opened or downloaded, for that game alone.
        val boxArtPath = artwork?.optString("boxArt")?.takeIf { it.isNotEmpty() }
        val previewPath = artwork?.optString("preview")?.takeIf { it.isNotEmpty() }
        val boxArtUrl = artwork?.optString("boxArtUrl")?.takeIf { it.isNotEmpty() }
        val previewUrl = artwork?.optString("previewUrl")?.takeIf { it.isNotEmpty() }
        return Game(
            contentId, title.ifBlank { fileName },
            merged.optString("description").takeIf { it.isNotBlank() },
            merged.opt("heart") as? Boolean,
            boxArtPath, previewPath, boxArtUrl, previewUrl, boxArtPath, previewPath,
            machine?.optInt("baseClockTenthsMHz")?.takeIf { it == 20 || it == 25 },
            machine?.optInt("gdcClockTenthsMHz")?.takeIf { it == 25 || it == 50 },
            machine?.optInt("cpuMultiple")?.takeIf { it in CPU_MULTIPLES },
            controller?.optString("profile")?.takeIf { it.length in 1..64 },
            controller?.optJSONArray("bindings")?.toString(),
            input?.optString("mode")?.takeIf { it in INPUT_MODES },
            input?.optString("touch")?.takeIf { it in INPUT_TOUCH },
            input?.optString("secondary")?.takeIf { it in INPUT_SECONDARY },
            mediaId(media, "bootFloppy"), mediaId(media, "floppyB"),
            mediaId(media, "cd"), mediaId(media, "bootDisk"),
            launch, hashSource >= commandSource,
            merged.optJSONArray("startupChoices"),
            merged.optJSONArray("diskSwaps"),
            launch?.optInt("timeoutMs", 30000)?.coerceIn(1000, 120000) ?: 30000,
            user?.keys()?.asSequence()?.toSet() ?: emptySet(),
            controllerDefaultBindings = (controller?.optJSONObject("defaults")
                ?: controller?.optJSONArray("bindings")?.takeIf {
                    it.length() > 0 || controller.optString("profile") == "custom-v1"
                }?.let { JSONObject().put("withoutSticks", it) })?.toString())
    }

    private fun mediaId(media: org.json.JSONArray?, role: String): String? = media?.let { items ->
        (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .firstOrNull { it.optString("role") == role }
            ?.optString("contentId")?.takeIf { it.isNotEmpty() }
    }

    override fun openArtwork(path: String): InputStream = installedCatalogs.openArtwork(path) ?: artworkStore.open(path)

    fun missingArtworkFor(entries: List<LibraryEntry>): List<ArtworkSource> {
        return entries.asSequence().filter { it.playable }
            .flatMap { entry ->
                val game = resolve(entry)
                listOfNotNull(
                    game.boxArtCatalogPath?.takeIf { game.boxArtUrl != null &&
                        sourceOf(entry, "artwork", "boxArt") ==
                            sourceOf(entry, "artwork", "boxArtUrl") }
                        ?.let { ArtworkSource(it, game.boxArtUrl!!) },
                    game.previewCatalogPath?.takeIf { game.previewUrl != null &&
                        sourceOf(entry, "artwork", "preview") ==
                            sourceOf(entry, "artwork", "previewUrl") }
                        ?.let { ArtworkSource(it, game.previewUrl!!) }).asSequence()
            }
            .distinctBy { it.path }
            .filter { installedCatalogs.artwork(it.path) == null && artworkStore.availablePath(it.path) == null }
            .toList()
    }

    fun downloadArtwork(source: ArtworkSource, cancelled: AtomicBoolean) =
        artworkStore.download(source.path, source.url, cancelled)

    @Synchronized fun setOverride(game: GameKey, field: String, value: Any) {
        require(field in FIELDS) { "Unsupported field" }
        require(field !in OBJECT_FIELDS) { "Use subfield updates for nested settings" }
        require(validField(field, value)) { "Invalid $field" }
        overrides.set(game, field, value)
        invalidate(game)
    }

    /** Overrides from before games had keys, for [GameKeyMigration]. */
    fun legacyOverrides() = GameKeyMigration.OverrideStore(overrides) { synchronized(this) { invalidate() } }

    /** Hide a known support disk without removing it from media lookup or disk swaps. */
    @Synchronized override fun hiddenFromLibrary(item: LibraryItem): Boolean {
        val contentId = matchId(item) ?: return false
        fun hidden(source: JSONObject?): Boolean? = source?.optJSONObject("games")
            ?.optJSONObject(contentId)?.opt("hidden") as? Boolean
        return CatalogFieldLayers.hidden(hidden(base), hidden(shardFor(contentId)),
            hidden(update), *installedCatalogs.sources(contentId).map { it.record?.opt("hidden") }.toTypedArray(),
            hidden(additions), overrides.record(item.key)?.opt("hidden"))
    }

    /** Read for every library entry, so it takes the field from the highest layer instead of merging records. */
    @Synchronized override fun setDisks(item: LibraryItem): List<String> {
        val contentId = matchId(item) ?: return emptyList()
        fun disks(record: JSONObject?) = record?.optJSONArray("setDisks")
        fun game(source: JSONObject?) = source?.optJSONObject("games")?.optJSONObject(contentId)
        val array = disks(overrides.record(item.key)) ?: disks(game(additions))
            ?: installedCatalogs.sources(contentId).asReversed().firstNotNullOfOrNull { disks(it.record) }
            ?: disks(game(parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")))
            ?: disks(game(update)) ?: disks(game(shardFor(contentId))) ?: disks(game(base))
            ?: return emptyList()
        return (0 until array.length()).map(array::getString)
    }

    @Synchronized fun updateOverrideSubfields(game: GameKey, field: String,
                                              changes: Map<String, Any?>) {
        require(field in OBJECT_FIELDS) { "Unsupported setting" }
        if (changes.isEmpty()) return
        for ((subfield, value) in changes) {
            require(subfield in OBJECT_FIELDS.getValue(field)) { "Unsupported setting" }
            require(value == null || if (field == "launch") when (subfield) {
                "type" -> value == "guestCommand"
                "text" -> value is String && validCommand(value)
                "timeoutMs" -> value is Int && value in 1000..120000
                "ready" -> value == "dosPrompt"
                else -> false
            } else validField(field, JSONObject().put(subfield, value))) {
                "Invalid $field.$subfield"
            }
        }
        overrides.updateSubfields(game, field, changes)
        invalidate(game)
    }

    fun setOverrideSubfield(game: GameKey, field: String, subfield: String, value: Any) =
        updateOverrideSubfields(game, field, mapOf(subfield to value))

    @Synchronized fun resetOverride(game: GameKey, field: String? = null) {
        require(field == null || field in FIELDS)
        overrides.clear(game, field)
        invalidate(game)
    }

    fun resetOverrideSubfield(game: GameKey, field: String, subfield: String) =
        updateOverrideSubfields(game, field, mapOf(subfield to null))

    fun setArtworkOverride(game: GameKey, kind: String, path: String) {
        require(kind in ART_PATH_FIELDS && validArtPath(path))
        setOverrideSubfield(game, "artwork", kind, path)
    }

    fun resetArtworkOverride(game: GameKey, kind: String) {
        require(kind in ART_PATH_FIELDS)
        resetOverrideSubfield(game, "artwork", kind)
    }

    private fun validField(field: String, value: Any): Boolean = when (field) {
        "title" -> value is String && validTitle(value)
        "heart" -> value is Boolean
        "hidden" -> value is Boolean
        "description" -> value is String && validDescription(value)
        "aliases" -> value is org.json.JSONArray && value.length() <= 64 &&
            (0 until value.length()).all { index ->
                (value.opt(index) as? String)?.let(::validTitle) == true
            }
        "artwork" -> value is JSONObject && runCatching {
            val art = Pc98ArtworkReferences.expand(value)
            art.keys().asSequence().all { it in ART_FIELDS } && ART_PATH_FIELDS.all {
                !art.has(it) || validArtPath(art.optString(it))
            } && ART_URL_FIELDS.all { !art.has(it) || validImageUrl(art.optString(it)) }
        }.getOrDefault(false)
        "machine" -> value is JSONObject &&
            value.keys().asSequence().all { it in OBJECT_FIELDS.getValue("machine") } &&
            (!value.has("baseClockTenthsMHz") ||
            (value.opt("baseClockTenthsMHz") is Int && value.optInt("baseClockTenthsMHz") in listOf(20, 25))) &&
            (!value.has("gdcClockTenthsMHz") ||
                (value.opt("gdcClockTenthsMHz") is Int && value.optInt("gdcClockTenthsMHz") in listOf(25, 50))) &&
            (!value.has("cpuMultiple") ||
                (value.opt("cpuMultiple") is Int && value.optInt("cpuMultiple") in CPU_MULTIPLES))
        "controller" -> value is JSONObject &&
            value.keys().asSequence().all { it in OBJECT_FIELDS.getValue("controller") } &&
            (!value.has("profile") ||
            (value.opt("profile") is String && value.optString("profile").length in 1..64)) &&
            (!value.has("bindings") || value.optJSONArray("bindings")?.let(ControllerBindings::valid) == true) &&
            (!value.has("defaults") || value.optJSONObject("defaults")?.let { defaults ->
                defaults.has("withoutSticks") && defaults.keys().asSequence().all {
                    it in setOf("withoutSticks", "withSticks") &&
                        defaults.optJSONArray(it)?.let(ControllerBindings::valid) == true
                }
            } == true)
        "input" -> value is JSONObject && value.length() > 0 &&
            value.keys().asSequence().all { it in INPUT_FIELDS } &&
            (!value.has("mode") || value.optString("mode") in INPUT_MODES) &&
            (!value.has("touch") || value.optString("touch") in INPUT_TOUCH) &&
            (!value.has("secondary") || value.optString("secondary") in INPUT_SECONDARY)
        "media" -> value is org.json.JSONArray && value.length() <= 16 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { item ->
                    MEDIA_ROLE.matches(item.optString("role")) &&
                        validId(item.optString("contentId")) &&
                        (item.optString("role") !in setOf("bootFloppy", "floppyB") ||
                            item.optString("contentId").startsWith("sha256-fd-v1:")) &&
                        (item.optString("role") != "cd" ||
                            item.optString("contentId").startsWith("sha256-cd-v1:")) &&
                        (item.optString("role") != "bootDisk" ||
                            !item.optString("contentId").startsWith("sha256-cd-v1:"))
                } == true
            } && SINGLE_MEDIA_ROLES.all { role ->
                (0 until value.length()).count {
                    value.optJSONObject(it)?.optString("role") == role
                } <= 1
            }
        "launch" -> value is JSONObject && value.length() > 0 &&
            value.keys().asSequence().all { it in OBJECT_FIELDS.getValue("launch") } &&
            (!value.has("type") || value.optString("type") == "guestCommand") &&
            !(value.has("text") && value.has("commands")) &&
            (!value.has("commands") || value.optJSONArray("commands")?.let { commands ->
                    commands.length() in 1..4 && (0 until commands.length()).all { index ->
                        (commands.opt(index) as? String)?.let(::validCommand) == true
                    }
                } == true) &&
            (!value.has("text") || validCommand(value.optString("text"))) &&
            (!value.has("ready") || value.optString("ready") == "dosPrompt") &&
            (!value.has("screenHashes") || value.optJSONArray("screenHashes")?.let { groups ->
                groups.length() in 1..4 && (0 until groups.length()).all { index ->
                    validHashes(groups.optJSONArray(index))
                }
            } == true) &&
            (!value.has("timeoutMs") ||
                (value.opt("timeoutMs") is Int && value.optInt("timeoutMs") in 1000..120000))
        "startupChoices" -> value is org.json.JSONArray && value.length() in 1..4 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { choice ->
                    validShortId(choice.optString("id")) && validLabel(choice.optString("title")) &&
                    validHashes(choice.optJSONArray("screenHashes")) &&
                    choice.optJSONArray("options")?.let { options ->
                        options.length() in 1..12 && (0 until options.length()).all { optionIndex ->
                            options.optJSONObject(optionIndex)?.let { option ->
                                validShortId(option.optString("id")) &&
                                validLabel(option.optString("label")) &&
                                (if (option.has("steps")) {
                                    !option.has("key") && !option.has("enter") &&
                                    option.optJSONArray("steps")?.let { steps ->
                                        steps.length() in 2..4 && (0 until steps.length()).all { stepIndex ->
                                            steps.optJSONObject(stepIndex)?.let { step ->
                                                step.length() == 3 &&
                                                step.optString("key").matches(Regex("[A-Za-z0-9]")) &&
                                                step.opt("enter") is Boolean &&
                                                validHashes(step.optJSONArray("screenHashes"))
                                            } == true
                                        }
                                    } == true
                                } else option.optString("key").matches(Regex("[A-Za-z0-9]")) &&
                                    option.opt("enter") is Boolean)
                            } == true
                        } && (0 until options.length()).map { options.getJSONObject(it).getString("id") }
                            .distinct().size == options.length()
                    } == true
                } == true
            } && (0 until value.length()).map { value.getJSONObject(it).getString("id") }
                .distinct().size == value.length()
        "diskSwaps" -> value is org.json.JSONArray && value.length() in 1..16 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { swap ->
                    swap.length() == 6 && listOf("id", "drive", "contentId", "screenHashes",
                        "key", "enter").all(swap::has) && validShortId(swap.optString("id")) &&
                        swap.opt("drive") is Int && swap.optInt("drive") in 0..1 &&
                        swap.optString("contentId").startsWith("sha256-fd-v1:") &&
                        validId(swap.optString("contentId")) &&
                        validHashes(swap.optJSONArray("screenHashes")) &&
                        (swap.opt("key") as? String)?.matches(Regex("[A-Za-z0-9]?")) == true &&
                        swap.opt("enter") is Boolean
                } == true
            } && (0 until value.length()).map { value.getJSONObject(it).getString("id") }
                .distinct().size == value.length() &&
            (0 until value.length()).flatMap { index ->
                value.getJSONObject(index).getJSONArray("screenHashes").let { hashes ->
                    (0 until hashes.length()).map(hashes::getString)
                }
            }.distinct().size == (0 until value.length()).sumOf { index ->
                value.getJSONObject(index).getJSONArray("screenHashes").length()
            }
        else -> false
    }

    private fun readAsset(path: String): JSONObject = parts.read(path.removePrefix("catalog/"))

    private fun normalizedName(fileName: String): String {
        val simple = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(BRACKETED, "")
            .replace(DISK_NUMBER, "")
            .replace(IMAGE_EXTENSION, "")
        val key = Normalizer.normalize(simple, Normalizer.Form.NFKC).lowercase()
            .filter(Char::isLetterOrDigit)
        return key
    }
    private fun bundledAdditional(contentId: String, fileName: String?): JSONObject? {
        parts.additional("shards/${contentId.substringAfter(':').take(2)}.json")
            .optJSONObject("games")?.optJSONObject(contentId)?.let { return it }
        val key = fileName?.let(::normalizedName) ?: return null
        val nameId = nameIndex.optJSONObject("names")?.optString(key) ?: return null
        return parts.additional("name-index-v1.json").optJSONObject("games")?.optJSONObject(nameId)
    }
    private fun installedSources(catalogs: List<com.mrjackspade.kairo.frontend.InstalledCatalogs.Catalog>,
                                 contentId: String, fileName: String?,
                                 shared: (JSONObject?) -> JSONObject?): List<CatalogFieldLayers.Source> =
        catalogs.flatMap { catalog ->
            val names = catalog.data.getJSONObject("nameIndex")
            val key = fileName?.let(::normalizedName)
            val nameId = key?.let { names.getJSONObject("names").optString(it) }
            listOf(CatalogFieldLayers.Source(catalog.name, nameId?.let { names.getJSONObject("games").optJSONObject(it) }),
                CatalogFieldLayers.Source(catalog.name, shared(names)),
                CatalogFieldLayers.Source(catalog.name, catalog.data.getJSONObject("games").optJSONObject(contentId)))
        }
    private fun lookupByName(fileName: String): Pair<JSONObject, Boolean>? {
        val key = normalizedName(fileName)
        if (key.length < 4) return null
        val updatedNames = update.optJSONObject("nameIndex")
        val updatedId = updatedNames?.optJSONObject("names")?.optString(key)
        updatedId?.let { updatedNames?.optJSONObject("games")?.optJSONObject(it) }
            ?.let { return it to true }
        val id = nameIndex.optJSONObject("names")?.optString(key) ?: return null
        return nameIndex.optJSONObject("games")?.optJSONObject(id)?.let { it to false }
    }

    private fun readShardNames(): Set<String> = try {
        val manifest = readAsset("catalog/manifest-v1.json")
        require(manifest.optInt("schemaVersion") == 1)
        val names = manifest.getJSONArray("shards")
        (0 until names.length()).map { names.getString(it) }.filter { it.matches(SHARD_NAME) }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun shardFor(contentId: String): JSONObject? {
        val name = contentId.substringAfter(':').take(2)
        if (name !in shardNames) return null
        shardCache.get(name)?.let { return it }
        return readAsset("catalog/shards/$name.json").also { shardCache.put(name, it) }
    }

    private fun readLocal(file: File): JSONObject =
        LocalCatalogFile.read(file, MAX_LOCAL_JSON.toInt()) ?: empty()

    private fun readUpdate(): JSONObject = try {
        // activeFile is checksum-verified and schema-validated for this APK by the shared store.
        // Repeating full validation here blocks every startup on the UI thread.
        snapshot.activeFile()?.let { JSONObject(it.readText(Charsets.UTF_8)) } ?: empty()
    } catch (_: Exception) { empty() }

    private fun requireUpdateFormat(root: JSONObject, core: Boolean = true) {
        require(root.optInt("schemaVersion") == 1)
        if (core) require(root.optInt("coreVersion") == 3)
        root.getJSONObject("games")
        val names = root.getJSONObject("nameIndex")
        require(names.optInt("schemaVersion") == 1)
        names.getJSONObject("games")
        names.getJSONObject("names")
        // Publishing audits all records. Resolution handles fields only as used.
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text).takeIf { it.optInt("schemaVersion") == 1 && it.optJSONObject("games") != null }
            ?: empty()
    } catch (_: Exception) { empty() }

    companion object {
        private const val MAX_ASSET_JSON = 64 * 1024 * 1024
        private val BRACKETED = Regex("\\[[^]]*]")
        private val DISK_NUMBER = Regex("\\((?:disk|disc|fd)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE)
        private val IMAGE_EXTENSION = Regex("\\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf|ccd)$",
            RegexOption.IGNORE_CASE)
        private val SHARD_NAME = Regex("[0-9a-f]{2}")
        private val SHORT_ID = Regex("[a-z0-9-]{1,40}")
        private const val MAX_LOCAL_JSON = 8L * 1024 * 1024
        private const val UPDATE_URL = "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/core-v3.json"
        private const val METADATA_URL = "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/core-v3.meta.json"
        private val FIELDS = setOf("title", "description", "aliases", "artwork", "machine", "controller", "input", "media", "launch", "startupChoices", "diskSwaps", "heart", "hidden")
        private val INPUT_MODES = setOf("auto", "keyboard", "mouse")
        private val INPUT_TOUCH = setOf("touchpad", "direct")
        private val INPUT_SECONDARY = setOf("keyboard", "touchpad")
        /** CPU clock multiples a game can ask for; the app default is 20. */
        val CPU_MULTIPLES = 1..20
        private val ART_PATH_FIELDS = setOf("boxArt", "preview")
        private val ART_URL_FIELDS = setOf("boxArtUrl", "previewUrl")
        private val ART_FIELDS = ART_PATH_FIELDS + ART_URL_FIELDS
        private val INPUT_FIELDS = setOf("mode", "touch", "secondary")
        private val OBJECT_FIELDS = mapOf(
            "artwork" to ART_FIELDS,
            "machine" to setOf("baseClockTenthsMHz", "gdcClockTenthsMHz", "cpuMultiple"),
            "controller" to setOf("profile", "bindings", "defaults"),
            "input" to INPUT_FIELDS,
            "launch" to setOf("type", "text", "commands", "ready", "timeoutMs", "screenHashes")
        )
        private val ID = Regex("sha256-(?:hdi|fd|cd)-v1:[0-9a-f]{64}")
        private val SINGLE_MEDIA_ROLES = listOf("bootFloppy", "floppyB", "cd", "bootDisk")
        private val MEDIA_ROLE = Regex("[A-Za-z0-9_-]{1,32}")
        fun validId(value: String) = ID.matches(value)
        private fun validTitle(value: String) = value.isNotBlank() && value.length <= 256
        private fun validDescription(value: String) = value.isNotBlank() && value.length <= 8000
        private fun validCommand(value: String) = value.isNotBlank() && value.length <= 128 &&
            value.all { it.code in 32..126 && (it.isLetterOrDigit() || it in " \\/._:-") }
        private fun validShortId(value: String) = value.matches(SHORT_ID)
        private fun validLabel(value: String) = value.isNotBlank() && value.length <= 100
        private fun validHashes(value: org.json.JSONArray?): Boolean = value != null &&
            value.length() in 1..16 && (0 until value.length()).all {
                (value.opt(it) as? String)?.matches(Regex("[0-9a-f]{16}")) == true
            }
        private fun parseHashes(value: org.json.JSONArray?): Set<Long> =
            if (value == null) emptySet() else (0 until value.length()).map {
                java.lang.Long.parseUnsignedLong(value.getString(it), 16)
            }.toSet()
        private fun validArtPath(value: String) = ArtworkOverridePath.valid(value, "art/")
        /** Artwork from sources without an image server of their own, served from this repository. */
        private const val SELF_HOSTED_ART = "/MrJackSpade/Kairo98/main/catalog/artwork/art/catalog/"
        fun validImageUrl(value: String): Boolean = try {
            if (value.length !in 1..512) false else URI(value).let { uri ->
                uri.scheme == "https" && (uri.host in setOf("images.launchbox-app.com", "gamesdb-images.launchbox.gg") ||
                    uri.host == "raw.githubusercontent.com" && uri.rawPath.startsWith(SELF_HOSTED_ART) &&
                    !uri.rawPath.contains("..")) &&
                    uri.port == -1 && uri.userInfo == null && uri.rawPath.isNotEmpty() &&
                    uri.rawQuery == null && uri.rawFragment == null
            }
        } catch (_: Exception) { false }
        private fun empty() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
    }
}
