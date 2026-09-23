package com.mrjackspade.kairo98

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Versioned, data-only metadata. Nothing in this file is executed by Android. */
class GameCatalog(private val context: Context) {
    data class Game(
        val contentId: String,
        val title: String,
        val boxArt: String?,
        val preview: String?,
        val baseClockTenthsMHz: Int?,
        val controllerProfile: String?,
        val controllerBindings: String?,
        val launchCommand: String?,
        val launchTimeoutMs: Int,
        val overriddenFields: Set<String>
    )

    private val base = readAsset("catalog/base-v1.json")
    private val shardNames = readShardNames()
    private val shardCache = object : android.util.LruCache<String, JSONObject>(8) {}
    private val additionsFile = File(context.filesDir, "user-catalog-v1.json")
    private val overridesFile = File(context.filesDir, "overrides-v1.json")
    private var additions = readLocal(additionsFile)
    private var overrides = readLocal(overridesFile)

    @Synchronized fun reloadAdditions() { additions = readLocal(additionsFile) }

    @Synchronized fun sourceOf(contentId: String, field: String, subfield: String? = null): String = when {
        hasField(overrides, contentId, field, subfield) -> "User override"
        hasField(additions, contentId, field, subfield) -> "User catalog"
        hasField(base, contentId, field, subfield) -> "Shipped catalog"
        shardFor(contentId)?.let { hasField(it, contentId, field, subfield) } == true -> "Shipped catalog"
        else -> if (field == "title") "Filename" else "App default"
    }

    private fun hasField(source: JSONObject, contentId: String, field: String,
                         subfield: String?): Boolean {
        val record = source.optJSONObject("games")?.optJSONObject(contentId) ?: return false
        return if (subfield == null) record.has(field)
            else record.optJSONObject(field)?.has(subfield) == true
    }

    @Synchronized fun resolve(contentId: String, fileName: String): Game {
        val merged = JSONObject()
        merge(merged, base.optJSONObject("games")?.optJSONObject(contentId))
        merge(merged, shardFor(contentId)?.optJSONObject("games")?.optJSONObject(contentId))
        merge(merged, additions.optJSONObject("games")?.optJSONObject(contentId))
        val user = overrides.optJSONObject("games")?.optJSONObject(contentId)
        merge(merged, user)
        val title = merged.optString("title").takeIf { validTitle(it) }
            ?: fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val artwork = merged.optJSONObject("artwork")
        val machine = merged.optJSONObject("machine")
        val controller = merged.optJSONObject("controller")
        val launch = merged.optJSONObject("launch")
        val command = launch?.optString("text")?.takeIf { validCommand(it) && launch.optString("type") == "guestCommand" }
        return Game(
            contentId, title.ifBlank { fileName },
            artwork?.optString("boxArt")?.takeIf(::validArtPath),
            artwork?.optString("preview")?.takeIf(::validArtPath),
            machine?.optInt("baseClockTenthsMHz")?.takeIf { it == 20 || it == 25 },
            controller?.optString("profile")?.takeIf { it.length in 1..64 },
            controller?.optJSONArray("bindings")?.toString(),
            command,
            launch?.optInt("timeoutMs", 30000)?.coerceIn(1000, 120000) ?: 30000,
            user?.keys()?.asSequence()?.toSet() ?: emptySet()
        )
    }

    @Synchronized fun setOverride(contentId: String, field: String, value: Any) {
        require(validId(contentId)) { "Invalid game ID" }
        require(field in FIELDS) { "Unsupported field" }
        require(validField(field, value)) { "Invalid $field" }
        val updated = JSONObject(overrides.toString())
        val games = updated.optJSONObject("games") ?: JSONObject().also { updated.put("games", it) }
        val entry = games.optJSONObject(contentId) ?: JSONObject().also { games.put(contentId, it) }
        entry.put(field, value)
        writeLocal(overridesFile, updated)
        overrides = updated
    }

    @Synchronized fun resetOverride(contentId: String, field: String? = null) {
        val updated = JSONObject(overrides.toString())
        val games = updated.optJSONObject("games") ?: return
        if (field == null) games.remove(contentId)
        else {
            require(field in FIELDS)
            games.optJSONObject(contentId)?.let {
                it.remove(field)
                if (it.length() == 0) games.remove(contentId)
            }
        }
        writeLocal(overridesFile, updated)
        overrides = updated
    }

    @Synchronized fun setArtworkOverride(contentId: String, kind: String, path: String) {
        require(validId(contentId) && kind in ART_FIELDS && validArtPath(path))
        val updated = JSONObject(overrides.toString())
        val games = updated.optJSONObject("games") ?: JSONObject().also { updated.put("games", it) }
        val entry = games.optJSONObject(contentId) ?: JSONObject().also { games.put(contentId, it) }
        val artwork = entry.optJSONObject("artwork") ?: JSONObject().also { entry.put("artwork", it) }
        artwork.put(kind, path)
        writeLocal(overridesFile, updated)
        overrides = updated
    }

    @Synchronized fun resetArtworkOverride(contentId: String, kind: String) {
        require(validId(contentId) && kind in ART_FIELDS)
        val updated = JSONObject(overrides.toString())
        val games = updated.optJSONObject("games") ?: return
        val entry = games.optJSONObject(contentId) ?: return
        val artwork = entry.optJSONObject("artwork") ?: return
        artwork.remove(kind)
        if (artwork.length() == 0) entry.remove("artwork")
        if (entry.length() == 0) games.remove(contentId)
        writeLocal(overridesFile, updated)
        overrides = updated
    }

    private fun merge(target: JSONObject, source: JSONObject?) {
        if (source == null) return
        for (field in FIELDS) {
            if (source.has(field)) {
                val value = source.opt(field) ?: continue
                if (validField(field, value)) {
                    if (field == "artwork" && value is JSONObject) {
                        val artwork = JSONObject(target.optJSONObject("artwork")?.toString() ?: "{}")
                        for (kind in ART_FIELDS) if (value.has(kind)) artwork.put(kind, value.get(kind))
                        target.put("artwork", artwork)
                    } else target.put(field, value)
                }
            }
        }
    }

    private fun validField(field: String, value: Any): Boolean = when (field) {
        "title" -> value is String && validTitle(value)
        "aliases" -> value is org.json.JSONArray && value.length() <= 64 &&
            (0 until value.length()).all { index ->
                (value.opt(index) as? String)?.let(::validTitle) == true
            }
        "artwork" -> value is JSONObject && listOf("boxArt", "preview").all {
            !value.has(it) || validArtPath(value.optString(it))
        }
        "machine" -> value is JSONObject && (!value.has("baseClockTenthsMHz") ||
            (value.opt("baseClockTenthsMHz") is Int && value.optInt("baseClockTenthsMHz") in listOf(20, 25)))
        "controller" -> value is JSONObject && (!value.has("profile") ||
            (value.opt("profile") is String && value.optString("profile").length in 1..64)) &&
            (!value.has("bindings") || value.optJSONArray("bindings")?.let(ControllerBindings::valid) == true)
        "media" -> value is org.json.JSONArray && value.length() <= 16 &&
            (0 until value.length()).all { index ->
                value.optJSONObject(index)?.let { item ->
                    MEDIA_ROLE.matches(item.optString("role")) &&
                        validId(item.optString("contentId"))
                } == true
            }
        "launch" -> value is JSONObject && value.optString("type") == "guestCommand" &&
            validCommand(value.optString("text")) &&
            (!value.has("ready") || value.optString("ready") == "dosPrompt") &&
            (!value.has("timeoutMs") ||
                (value.opt("timeoutMs") is Int && value.optInt("timeoutMs") in 1000..120000))
        else -> false
    }

    private fun readAsset(path: String): JSONObject = try {
        context.assets.open(path).use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_ASSET_JSON) { "Catalog is too large" }
            parse(bytes.toString(Charsets.UTF_8))
        }
    } catch (_: Exception) { empty() }

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

    private fun readLocal(file: File): JSONObject = try {
        if (!file.isFile || file.length() > MAX_LOCAL_JSON) empty()
        else parse(AtomicFile(file).readFully().toString(Charsets.UTF_8))
    } catch (_: Exception) { empty() }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text).takeIf { it.optInt("schemaVersion") == 1 && it.optJSONObject("games") != null }
            ?: empty()
    } catch (_: Exception) { empty() }

    private fun writeLocal(file: File, json: JSONObject) {
        if (file.isFile) {
            val existing = try { JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8)) }
                catch (_: Exception) { null }
            require(existing != null) {
                "Existing ${file.name} is unreadable; preserve it and restore a backup before editing"
            }
            require(existing.optInt("schemaVersion") == 1) {
                "This metadata uses a newer schema; update Kairo98 before editing"
            }
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_LOCAL_JSON) { "Metadata is too large" }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    companion object {
        private const val MAX_ASSET_JSON = 64 * 1024 * 1024
        private const val MAX_LOCAL_JSON = 8L * 1024 * 1024
        private val FIELDS = setOf("title", "aliases", "artwork", "machine", "controller", "media", "launch")
        private val ART_FIELDS = setOf("boxArt", "preview")
        private val ID = Regex("sha256-hdi-v1:[0-9a-f]{64}")
        private val MEDIA_ROLE = Regex("[A-Za-z0-9_-]{1,32}")
        fun validId(value: String) = ID.matches(value)
        private fun validTitle(value: String) = value.isNotBlank() && value.length <= 256
        private fun validCommand(value: String) = value.isNotBlank() && value.length <= 128 &&
            value.all { it.code in 32..126 && (it.isLetterOrDigit() || it in " \\/._:-") }
        private fun validArtPath(value: String) = value.length in 1..256 &&
            value.startsWith("art/") && !value.contains("..") && !value.contains('\\') &&
            !value.startsWith("/")
        private fun empty() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
    }
}
