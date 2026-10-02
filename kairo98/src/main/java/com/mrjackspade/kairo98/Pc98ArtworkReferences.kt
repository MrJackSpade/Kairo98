package com.mrjackspade.kairo98

import org.json.JSONObject
import java.security.MessageDigest

/** Reconstruct the existing cache paths and URLs before catalog layers merge. */
internal object Pc98ArtworkReferences {
    fun expand(art: JSONObject): JSONObject {
        val keys = art.keys().asSequence().toSet()
        if (!art.has("platform") && !art.has("game") && keys.none { art.opt(it) is JSONObject }) return art
        require(keys.containsAll(setOf("platform", "game")) && keys.size in 3..4 &&
            keys.all { it in setOf("platform", "game", "boxArt", "preview") })
        val platform = art.opt("platform") as? Int ?: error("Invalid artwork platform")
        val game = art.opt("game") as? Int ?: error("Invalid artwork group")
        require(platform in setOf(88, 98) && game > 0)
        return JSONObject().apply {
            for ((kind, label) in listOf("boxArt" to "box", "preview" to "screenshot")) {
                if (!art.has(kind)) continue
                val ref = art.getJSONObject(kind)
                require(ref.keys().asSequence().toSet() == setOf("id", "format", "revision"))
                val id = ref.opt("id") as? String ?: error("Invalid artwork ID")
                val format = ref.opt("format") as? Int ?: error("Invalid artwork format")
                val revision = ref.opt("revision") as? Int ?: error("Invalid artwork revision")
                require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) &&
                    format in 0..1 && revision in setOf(0, 2))
                val url = "https://images.launchbox-app.com/" +
                    (if (revision == 2) "r2_" else "") + id + (if (format == 1) ".png" else ".jpg")
                val token = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
                    .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
                put(kind, "art/catalog/pc$platform/$game/$label-$token.webp")
                put(kind + "Url", url)
            }
        }
    }

    fun record(source: JSONObject?): JSONObject? = source?.let {
        JSONObject(it.toString()).apply {
            optJSONObject("artwork")?.let { art -> put("artwork", expand(art)) }
        }
    }
}
