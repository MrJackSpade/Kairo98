package com.mrjackspade.kairo98

import com.mrjackspade.kairo.frontend.CatalogFieldLayers
import com.mrjackspade.kairo.frontend.GameMetadataOverrides
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

/** Versioned, data-only metadata. Nothing in this file is executed by Android. */
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
        val launchCommand: String?,
        val launchCommands: List<String>,
        val launchScreenHashes: List<Set<Long>>,
        val startupChoices: List<StartupChoice>,
        val diskSwaps: List<DiskSwap>,
        val launchTimeoutMs: Int,
        val overriddenFields: Set<String>,
        val controllerDefaultBindings: String? = null
    ) : LibraryGame {
        override val tags: List<String> get() = if (heart == true) listOf("♥") else emptyList()
    }

    private val base = readAsset("catalog/base-v1.json")
    // Hash matches and downloaded name matches do not need the bundled fallback index.
    private val nameIndex by lazy { readAsset("catalog/name-index-v1.json") }
    private val shardNames = readShardNames()
    private val shardCache = object : android.util.LruCache<String, JSONObject>(8) {}
    private val fallbackRecords = HashMap<String, JSONObject>()
    private val updatedFallbackIds = HashSet<String>()
    private val additionsFile = File(context.filesDir, "user-catalog-v1.json")
    private val overridesFile = File(context.filesDir, "overrides-v1.json")
    private val snapshot = CatalogSnapshotStore(context, "catalog-update-v1.json",
        UPDATE_URL, METADATA_URL, MAX_LOCAL_JSON) { file ->
        require(parseUpdate(file.readBytes()) != null) { "Invalid catalog update" }
    }
    private var update = readUpdate()
    private var additions = readLocal(additionsFile)
    private val overrides = GameMetadataOverrides(overridesFile, MAX_LOCAL_JSON.toInt(),
        schemaEnvelope = true, validRecord = ::validLocalRecord)

    @Synchronized fun reloadAdditions() { additions = readLocal(additionsFile) }

    /** Call from a worker thread. An invalid or interrupted download leaves the current catalog intact. */
    fun downloadUpdate(task: com.mrjackspade.kairo.frontend.CatalogUpdateTask = com.mrjackspade.kairo.frontend.CatalogUpdateTask()): Boolean {
        if (!snapshot.download(task)) return false
        synchronized(this) {
            update = readUpdate()
            fallbackRecords.clear()
            updatedFallbackIds.clear()
        }
        return true
    }

    @Synchronized fun sourceOf(contentId: String, field: String, subfield: String? = null): String {
        val path = if (subfield == null) arrayOf(field) else arrayOf(field, subfield)
        return layered(contentId).sourceOf(*path)
            ?: if (field == "title") "Filename" else "App default"
    }

    private fun layered(contentId: String, fileName: String? = null): CatalogFieldLayers.Result {
        val baseRecord = base.optJSONObject("games")?.optJSONObject(contentId)
        val shardRecord = shardFor(contentId)?.optJSONObject("games")?.optJSONObject(contentId)
        val updateRecord = update.optJSONObject("games")?.optJSONObject(contentId)
        var fallback = fallbackRecords[contentId]
        if (baseRecord == null && shardRecord == null && updateRecord == null && fileName != null) {
            lookupByName(fileName)?.let { (record, fromUpdate) ->
                fallback = record
                if (validId(contentId)) {
                    fallbackRecords[contentId] = record
                    if (fromUpdate) updatedFallbackIds.add(contentId) else updatedFallbackIds.remove(contentId)
                }
            }
        } else if (baseRecord != null || shardRecord != null || updateRecord != null) fallback = null
        return CatalogFieldLayers.merge(listOf(
            CatalogFieldLayers.Source("Shipped catalog", baseRecord),
            CatalogFieldLayers.Source("Shipped catalog", shardRecord),
            CatalogFieldLayers.Source(if (contentId in updatedFallbackIds) "Updated catalog"
                else "Shipped catalog", fallback),
            CatalogFieldLayers.Source("Updated catalog", updateRecord),
            CatalogFieldLayers.Source("User catalog", additions.optJSONObject("games")?.optJSONObject(contentId)),
            CatalogFieldLayers.Source("User override", overrides.record(contentId))
        ), { path -> path.size == 1 && path[0] in OBJECT_FIELDS },
            { path, value -> when (path.size) {
                1 -> validField(path[0], value)
                2 -> path[1] in (OBJECT_FIELDS[path[0]] ?: emptySet()) &&
                    validField(path[0], JSONObject().put(path[1], value))
                else -> false
            } },
            { path, record -> path.size == 1 && validField(path[0], record) },
            { path, record -> if (path == listOf("launch")) when {
                record.has("text") -> setOf("commands")
                record.has("commands") -> setOf("text")
                else -> emptySet()
            } else emptySet() })
    }

    @Synchronized override fun resolve(contentId: String, fileName: String): Game {
        val layers = layered(contentId, fileName)
        val merged = layers.record
        val user = overrides.record(contentId)
        val title = merged.optString("title").takeIf { validTitle(it) }
            ?: fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val artwork = merged.optJSONObject("artwork")
        val machine = merged.optJSONObject("machine")
        val controller = merged.optJSONObject("controller")
        val input = merged.optJSONObject("input")
        val media = merged.optJSONArray("media")
        val launch = merged.optJSONObject("launch")
        val commands = launch?.takeIf { validField("launch", it) &&
            (it.has("commands") || it.has("text")) }?.let { value ->
            value.optJSONArray("commands")?.let { array ->
                (0 until array.length()).map(array::getString)
            } ?: listOf(value.getString("text"))
        } ?: emptyList()
        val commandSource = maxOf(layers.sourceIndexOf("launch", "text") ?: -1,
            layers.sourceIndexOf("launch", "commands") ?: -1)
        val hashSource = layers.sourceIndexOf("launch", "screenHashes") ?: -1
        val commandHashes = launch?.optJSONArray("screenHashes")?.takeIf { groups ->
            commands.isNotEmpty() && hashSource >= commandSource &&
                groups.length() == commands.size
        }?.let { groups ->
            (0 until groups.length()).map { index -> parseHashes(groups.optJSONArray(index)) }
        } ?: emptyList()
        val choices = merged.optJSONArray("startupChoices")?.takeIf {
            validField("startupChoices", it)
        }?.let { array ->
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
        } ?: emptyList()
        val diskSwaps = merged.optJSONArray("diskSwaps")?.takeIf {
            validField("diskSwaps", it)
        }?.let { array ->
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DiskSwap(item.getString("id"), item.getInt("drive"),
                    item.getString("contentId"), parseHashes(item.getJSONArray("screenHashes")),
                    item.getString("key"), item.getBoolean("enter"))
            }
        } ?: emptyList()
        val boxArtPath = artwork?.optString("boxArt")?.takeIf(::validArtPath)
        val previewPath = artwork?.optString("preview")?.takeIf(::validArtPath)
        val boxArtUrl = artwork?.optString("boxArtUrl")?.takeIf(::validImageUrl)
        val previewUrl = artwork?.optString("previewUrl")?.takeIf(::validImageUrl)
        return Game(
            contentId, title.ifBlank { fileName },
            merged.optString("description").takeIf(::validDescription),
            merged.opt("heart") as? Boolean,
            artworkStore.availablePath(boxArtPath),
            artworkStore.availablePath(previewPath),
            boxArtUrl, previewUrl, boxArtPath, previewPath,
            machine?.optInt("baseClockTenthsMHz")?.takeIf { it == 20 || it == 25 },
            machine?.optInt("gdcClockTenthsMHz")?.takeIf { it == 25 || it == 50 },
            machine?.optInt("cpuMultiple")?.takeIf { it in CPU_MULTIPLES },
            controller?.optString("profile")?.takeIf { it.length in 1..64 },
            controller?.optJSONArray("bindings")?.toString(),
            input?.optString("mode")?.takeIf { it in INPUT_MODES },
            input?.optString("touch")?.takeIf { it in INPUT_TOUCH },
            input?.optString("secondary")?.takeIf { it in INPUT_SECONDARY },
            media?.let { items ->
                (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                    .firstOrNull { it.optString("role") == "bootFloppy" }
                    ?.optString("contentId")?.takeIf(::validId)
            },
            media?.let { items ->
                (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                    .firstOrNull { it.optString("role") == "floppyB" }
                    ?.optString("contentId")?.takeIf(::validId)
            },
            commands.joinToString("; ").takeIf { commands.isNotEmpty() },
            commands,
            commandHashes,
            choices,
            diskSwaps,
            launch?.optInt("timeoutMs", 30000)?.coerceIn(1000, 120000) ?: 30000,
            user?.keys()?.asSequence()?.toSet() ?: emptySet(),
            controllerDefaultBindings = (controller?.optJSONObject("defaults")
                ?: controller?.optJSONArray("bindings")?.takeIf {
                    it.length() > 0 || controller.optString("profile") == "custom-v1"
                }?.let { JSONObject().put("withoutSticks", it) })?.toString())
    }

    override fun openArtwork(path: String): InputStream = artworkStore.open(path)

    fun missingArtworkFor(entries: List<LibraryEntry>): List<ArtworkSource> {
        return entries.asSequence().filter { it.playable }
            .flatMap { entry ->
                val id = entry.contentId ?: ""
                val game = resolve(id, entry.displayName)
                listOfNotNull(
                    game.boxArtCatalogPath?.takeIf { game.boxArtUrl != null &&
                        sourceOf(id, "artwork", "boxArt") ==
                            sourceOf(id, "artwork", "boxArtUrl") }
                        ?.let { ArtworkSource(it, game.boxArtUrl!!) },
                    game.previewCatalogPath?.takeIf { game.previewUrl != null &&
                        sourceOf(id, "artwork", "preview") ==
                            sourceOf(id, "artwork", "previewUrl") }
                        ?.let { ArtworkSource(it, game.previewUrl!!) }).asSequence()
            }
            .distinctBy { it.path }
            .filter { artworkStore.availablePath(it.path) == null }
            .toList()
    }

    fun downloadArtwork(source: ArtworkSource, cancelled: AtomicBoolean) =
        artworkStore.download(source.path, source.url, cancelled)

    @Synchronized fun setOverride(contentId: String, field: String, value: Any) {
        require(validId(contentId)) { "Invalid game ID" }
        require(field in FIELDS) { "Unsupported field" }
        require(field !in OBJECT_FIELDS) { "Use subfield updates for nested settings" }
        require(validField(field, value)) { "Invalid $field" }
        overrides.set(contentId, field, value)
    }

    /** Hide a known support disk without removing it from media lookup or disk swaps. */
    @Synchronized override fun hiddenFromLibrary(contentId: String): Boolean {
        if (!validId(contentId)) return false
        fun hidden(source: JSONObject?): Boolean? = source?.optJSONObject("games")
            ?.optJSONObject(contentId)?.opt("hidden") as? Boolean
        return CatalogFieldLayers.hidden(hidden(base), hidden(shardFor(contentId)),
            hidden(update), hidden(additions), overrides.record(contentId)?.opt("hidden"))
    }

    @Synchronized fun updateOverrideSubfields(contentId: String, field: String,
                                              changes: Map<String, Any?>) {
        require(validId(contentId)) { "Invalid game ID" }
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
        overrides.updateSubfields(contentId, field, changes)
    }

    fun setOverrideSubfield(contentId: String, field: String, subfield: String, value: Any) =
        updateOverrideSubfields(contentId, field, mapOf(subfield to value))

    @Synchronized fun resetOverride(contentId: String, field: String? = null) {
        require(validId(contentId)) { "Invalid game ID" }
        require(field == null || field in FIELDS)
        overrides.clear(contentId, field)
    }

    fun resetOverrideSubfield(contentId: String, field: String, subfield: String) =
        updateOverrideSubfields(contentId, field, mapOf(subfield to null))

    fun setArtworkOverride(contentId: String, kind: String, path: String) {
        require(kind in ART_PATH_FIELDS && validArtPath(path))
        setOverrideSubfield(contentId, "artwork", kind, path)
    }

    fun resetArtworkOverride(contentId: String, kind: String) {
        require(kind in ART_PATH_FIELDS)
        resetOverrideSubfield(contentId, "artwork", kind)
    }

    private fun validLocalRecord(contentId: String, record: JSONObject): Boolean =
        validId(contentId) && record.keys().asSequence().all {
            it in FIELDS && validField(it, record.opt(it) ?: return@all false)
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
        "artwork" -> value is JSONObject &&
            value.keys().asSequence().all { it in ART_FIELDS } && ART_PATH_FIELDS.all {
            !value.has(it) || validArtPath(value.optString(it))
        } && ART_URL_FIELDS.all { !value.has(it) || validImageUrl(value.optString(it)) }
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
                            item.optString("contentId").startsWith("sha256-fd-v1:"))
                } == true
            } && listOf("bootFloppy", "floppyB").all { role ->
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

    private fun readAsset(path: String): JSONObject = try {
        context.assets.open(path).use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_ASSET_JSON) { "Catalog is too large" }
            parse(bytes.toString(Charsets.UTF_8))
        }
    } catch (_: Exception) { empty() }

    private fun lookupByName(fileName: String): Pair<JSONObject, Boolean>? {
        val simple = fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\\((?:disk|disc|fd)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.(?:zip|hdi|fdi|d88|88d|d98|98d|nfd|fdd|dcp|dcu|hdm|xdf)$",
                RegexOption.IGNORE_CASE), "")
        val key = Normalizer.normalize(simple, Normalizer.Form.NFKC).lowercase()
            .filter(Char::isLetterOrDigit)
        if (key.length < 4) return null
        val updatedNames = update.optJSONObject("nameIndex")
        val updatedId = updatedNames?.optJSONObject("names")?.optString(key)
        updatedId?.let { updatedNames?.optJSONObject("games")?.optJSONObject(it) }
            ?.let { return it to true }
        val id = nameIndex.optJSONObject("names")?.optString(key) ?: return null
        return nameIndex.optJSONObject("games")?.optJSONObject(id)?.let { it to false }
    }

    private fun readShardNames(): Set<String> = try {
        val manifest = context.assets.open("catalog/manifest-v1.json").use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 1024 * 1024)
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        require(manifest.optInt("schemaVersion") == 1)
        val names = manifest.getJSONArray("shards")
        (0 until names.length()).map { names.getString(it) }.filter { it.matches(Regex("[0-9a-f]{2}")) }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun shardFor(contentId: String): JSONObject? {
        if (!validId(contentId)) return null
        val name = contentId.substringAfter(':').take(2)
        if (name !in shardNames) return null
        shardCache.get(name)?.let { return it }
        return readAsset("catalog/shards/$name.json").also { shardCache.put(name, it) }
    }

    private fun readLocal(file: File): JSONObject =
        LocalCatalogFile.read(file, MAX_LOCAL_JSON.toInt(), ::validLocalRecord) ?: empty()

    private fun readUpdate(): JSONObject = try {
        // activeFile is checksum-verified and schema-validated for this APK by the shared store.
        // Repeating full validation here blocks every startup on the UI thread.
        snapshot.activeFile()?.let { JSONObject(it.readText(Charsets.UTF_8)) } ?: empty()
    } catch (_: Exception) { empty() }

    private fun parseUpdate(bytes: ByteArray): JSONObject? {
        return try {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        if (root.optInt("schemaVersion") != 1 || root.length() != 3) return null
        val games = root.optJSONObject("games") ?: return null
        val names = root.optJSONObject("nameIndex") ?: return null
        if (names.optInt("schemaVersion") != 1 || names.length() != 3) return null
        val nameGames = names.optJSONObject("games") ?: return null
        val nameKeys = names.optJSONObject("names") ?: return null
        if (!validRecords(games, { validId(it) }) ||
            !validRecords(nameGames, { it.matches(Regex("[a-z0-9]+:[0-9]+")) })) return null
        for (key in nameKeys.keys()) {
            if (!key.matches(Regex("[\\p{L}\\p{N}]{4,128}"))) return null
            val id = nameKeys.opt(key) as? String ?: return null
            if (!nameGames.has(id)) return null
        }
        root
        } catch (_: Exception) { null }
    }

    private fun validRecords(records: JSONObject, validKey: (String) -> Boolean): Boolean {
        for (key in records.keys()) {
            if (!validKey(key)) return false
            val record = records.optJSONObject(key) ?: return false
            for (field in record.keys()) {
                if (field !in FIELDS || !validField(field, record.opt(field) ?: return false))
                    return false
            }
        }
        return true
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text).takeIf { it.optInt("schemaVersion") == 1 && it.optJSONObject("games") != null }
            ?: empty()
    } catch (_: Exception) { empty() }

    companion object {
        private const val MAX_ASSET_JSON = 64 * 1024 * 1024
        private const val MAX_LOCAL_JSON = 8L * 1024 * 1024
        private const val UPDATE_URL = "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/online-v1.json"
        private const val METADATA_URL = "https://raw.githubusercontent.com/MrJackSpade/Kairo98/main/catalog/online-v1.meta.json"
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
        private val ID = Regex("sha256-(?:hdi|fd)-v1:[0-9a-f]{64}")
        private val MEDIA_ROLE = Regex("[A-Za-z0-9_-]{1,32}")
        fun validId(value: String) = ID.matches(value)
        private fun validTitle(value: String) = value.isNotBlank() && value.length <= 256
        private fun validDescription(value: String) = value.isNotBlank() && value.length <= 8000
        private fun validCommand(value: String) = value.isNotBlank() && value.length <= 128 &&
            value.all { it.code in 32..126 && (it.isLetterOrDigit() || it in " \\/._:-") }
        private fun validShortId(value: String) = value.matches(Regex("[a-z0-9-]{1,40}"))
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
        fun validImageUrl(value: String): Boolean = try {
            if (value.length !in 1..512) false else URI(value).let { uri ->
                uri.scheme == "https" && uri.host in setOf("images.launchbox-app.com", "gamesdb-images.launchbox.gg") &&
                    uri.port == -1 && uri.userInfo == null && uri.rawPath.isNotEmpty() &&
                    uri.rawQuery == null && uri.rawFragment == null
            }
        } catch (_: Exception) { false }
        private fun empty() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
    }
}
