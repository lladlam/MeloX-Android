package com.lladlam.melox.core.plugin

import org.json.JSONArray
import org.json.JSONObject

/** Structural API-v1 boundary, shared by every Provider method before model mapping.
 * Unknown fields are ignored for forward compatibility; present optional fields must have their
 * declared type (null is not absence). Network/account/media policy is checked separately.
 */
internal object PluginWireContract {
    fun check(method: String, raw: String) {
        PluginJson.check(raw)
        when (method) {
            "isFavorite" -> { require(raw.trim() in setOf("true", "false")); return }
            "setFavorite" -> { require(raw.trim() == "null"); return }
            "accountSummary" -> if (raw.trim() == "null") return
        }
        val value = JSONObject(raw)
        when (method) {
            "search", "rankingTracks" -> page(value, ::track)
            "playlists", "searchPlaylists", "userPlaylists" -> page(value, ::playlist)
            "searchAlbums", "savedAlbums" -> page(value, ::album)
            "searchArtists", "followedArtists" -> page(value, ::artist)
            "rankings" -> page(value, ::ranking)
            "playlistDetail" -> { playlist(value.getJSONObject("summary")); page(value.getJSONObject("tracks"), ::track) }
            "albumDetail" -> { album(value.getJSONObject("summary")); page(value.getJSONObject("tracks"), ::track) }
            "artistDetail" -> { artist(value.getJSONObject("summary")); page(value.getJSONObject("tracks"), ::track) }
            "homeFeed" -> {
                array(value, "recommendedPlaylists", 0, 100, ::playlist)
                array(value, "newSongs", 0, 100, ::track)
                array(value, "rankings", 0, 100, ::ranking)
            }
            "accountSummary" -> account(value)
            "authenticationStatus" -> when (text(value, "type", 32)) {
                "notRequired", "signedOut" -> Unit
                "signedIn" -> account(value.getJSONObject("account"))
                "expired" -> optional(value, "accountId") { id(value, it) }
                else -> error("invalid authentication state")
            }
            "resolvePlayback", "resolveDownload" -> resolution(value)
            "lyrics" -> {
                optional(value, "isUnsynchronized") { boolean(value, it) }
                var previous = -1L
                array(value, "lines", 0, 2000) { line ->
                    val time = number(line, "timeMs", 0, 86_400_000)
                    require(time >= previous) { "lyrics must be ordered" }; previous = time
                    text(line, "text", 4096, blank = true)
                    optional(line, "durationMs") { number(line, it, 0, 86_400_000) }
                    listOf("translation", "romanization").forEach { key -> optional(line, key) { text(line, it, 4096, blank = true) } }
                }
            }
            else -> error("unsupported plugin contract method")
        }
    }

    private fun track(v: JSONObject) {
        id(v); text(v, "title", 1024)
        array(v, "artists", 1, 32, ::artistRef)
        optional(v, "album") { val a = v.getJSONObject(it); text(a, "name", 1024); optional(a, "id") { key -> id(a, key) }; image(a) }
        image(v)
        optional(v, "durationMs") { number(v, it, 0, 86_400_000) }
        optional(v, "availability") { enum(v, it, setOf("unknown", "playable", "previewOnly", "loginRequired", "subscriptionRequired", "regionRestricted", "copyrightRestricted", "unavailable")) }
        optional(v, "bestQuality") { enum(v, it, qualities) }
        optional(v, "bestBitrate") { number(v, it, 1, Int.MAX_VALUE.toLong()) }
    }
    private fun artistRef(v: JSONObject) { text(v, "name", 512); optional(v, "id") { id(v, it) } }
    private fun playlist(v: JSONObject) {
        id(v); text(v, "title", 4096); image(v)
        listOf("creatorName", "description").forEach { key -> optional(v, key) { text(v, it, 4096) } }
        optional(v, "trackCount") { number(v, it, 0, Int.MAX_VALUE.toLong()) }
        optional(v, "playCount") { number(v, it) }
    }
    private fun album(v: JSONObject) {
        id(v); text(v, "title", 4096); image(v)
        optional(v, "artists") { array(v, it, 0, 32, ::artistRef) }
        optional(v, "trackCount") { number(v, it) }; optional(v, "releaseDate") { text(v, it, 80) }
    }
    private fun artist(v: JSONObject) {
        id(v); text(v, "name", 512); image(v)
        optional(v, "description") { text(v, it, 4096) }
        listOf("songCount", "albumCount").forEach { key -> optional(v, key) { number(v, it) } }
    }
    private fun ranking(v: JSONObject) { id(v); text(v, "title", 4096); image(v); optional(v, "subtitle") { text(v, it, 4096) } }
    private fun account(v: JSONObject) {
        id(v); text(v, "displayName", 512); image(v, "avatarUrl")
        optional(v, "subtitle") { text(v, it, 4096) }
    }
    private fun page(v: JSONObject, decode: (JSONObject) -> Unit) {
        number(v, "page", 1, Int.MAX_VALUE.toLong())
        val size = number(v, "pageSize", 1, 100).toInt()
        boolean(v, "hasMore"); optional(v, "total") { number(v, it) }
        array(v, "items", 0, size, decode)
    }
    private fun resolution(v: JSONObject) {
        when (text(v, "type", 32)) {
            "playable" -> {
                val url = text(v, "url", 4096)
                optional(v, "actualQuality") { enum(v, it, qualities) }
                optional(v, "bitrate") { number(v, it, 1, Int.MAX_VALUE.toLong()) }
                optional(v, "expiresAtEpochMs") { number(v, it, 1) }
                optional(v, "credential") { require(text(v, it, 16) == "active") }
                val format = if (v.has("format")) text(v, "format", 64, blank = true) else null
                PluginMediaFormats.check(url, format, v.has("drm") || v.has("licenseUrl"))
                optional(v, "headers") {
                    val headers = v.getJSONObject(it); require(headers.length() <= 32)
                    headers.keys().forEach { key ->
                        require(key.matches(Regex("[A-Za-z0-9-]{1,80}")))
                        val header = text(headers, key, 4096, blank = true)
                        require('\r' !in header && '\n' !in header)
                    }
                }
            }
            "preview" -> {
                val url = text(v, "url", 4096)
                optional(v, "durationMs") { number(v, it, 1, 86_400_000) }
                PluginMediaFormats.check(url, drm = v.has("drm") || v.has("licenseUrl"))
            }
            "loginRequired", "subscriptionRequired", "regionRestricted", "copyrightRestricted" -> Unit
            "unavailable" -> optional(v, "reason") { text(v, it, 512, blank = true) }
            else -> error("invalid resolution type")
        }
    }
    private val qualities = setOf("standard", "high", "lossless", "hiResolution", "immersive", "master")
    private fun enum(v: JSONObject, key: String, values: Set<String>) { require(text(v, key, 128) in values) { "invalid $key" } }
    private fun image(v: JSONObject, key: String = "artworkUrl") { optional(v, key) { text(v, it, 4096, blank = true) } }
    private fun id(v: JSONObject, key: String = "id") { require(text(v, key, 4096).toByteArray(Charsets.UTF_8).size <= 4096) { "resource ID too large" } }
    private fun text(v: JSONObject, key: String, max: Int, blank: Boolean = false): String {
        require(v.opt(key) is String) { "$key must be a string" }
        return v.getString(key).also { require((blank || it.isNotBlank()) && it.codePointCount(0, it.length) <= max) { "invalid $key" } }
    }
    private fun number(v: JSONObject, key: String, min: Long = 0, max: Long = 9_007_199_254_740_991): Long {
        require(v.opt(key) is Number) { "$key must be a number" }
        val n = v.getDouble(key)
        require(n.isFinite() && n == kotlin.math.floor(n) && n >= min && n <= max) { "invalid $key" }
        return n.toLong()
    }
    private fun boolean(v: JSONObject, key: String) { require(v.opt(key) is Boolean) { "$key must be boolean" } }
    private fun optional(v: JSONObject, key: String, check: (String) -> Unit) { if (v.has(key)) check(key) }
    private fun array(v: JSONObject, key: String, min: Int, max: Int, decode: (JSONObject) -> Unit) {
        val values: JSONArray = v.getJSONArray(key); require(values.length() in min..max) { "invalid $key size" }
        for (i in 0 until values.length()) decode(values.getJSONObject(i))
    }
}
