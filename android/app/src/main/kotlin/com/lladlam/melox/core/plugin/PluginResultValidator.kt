package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import org.json.JSONObject
import java.net.URI

/** Validates the host's standardized result shape; it does not adapt platform payloads. */
object PluginResultValidator {
    data class SearchPage(val tracks: List<MusicTrack>, val page: Int, val pageSize: Int, val hasMore: Boolean)

    private const val MAX_RESULT_BYTES = 1024 * 1024

    fun search(source: SourceId, json: String): SearchPage {
        require(source.value.startsWith("plugin:")) { "invalid plugin source" }
        val result = parse(json)
        val page = integer(result, "page", 1..Int.MAX_VALUE)
        val pageSize = integer(result, "pageSize", 1..100)
        require(result.opt("hasMore") is Boolean) { "hasMore must be a boolean" }
        val items = result.getJSONArray("items")
        require(items.length() <= pageSize) { "too many tracks" }
        val tracks = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = resource(source, item, "id") ?: error("missing track id")
            val title = text(item, "title", 1024)
            val rawArtists = item.getJSONArray("artists")
            require(rawArtists.length() in 1..32) { "invalid artists" }
            val artists = (0 until rawArtists.length()).map { artistIndex ->
                val artist = rawArtists.getJSONObject(artistIndex)
                MusicArtistRef(resource(source, artist, "id"), text(artist, "name", 512))
            }
            val duration = optionalInteger(item, "durationMs", 0..86_400_000)?.toLong()
            val artwork = url(item, "artworkUrl")
            val album = if (item.has("album")) item.getJSONObject("album").let {
                MusicAlbumRef(resource(source, it, "id"), text(it, "name", 1024), url(it, "artworkUrl"))
            } else null
            val availability = if (item.has("availability")) TrackAvailability.entries.firstOrNull {
                wire(it.name) == item.getString("availability")
            } ?: error("invalid availability") else TrackAvailability.Unknown
            val quality = if (item.has("bestQuality")) AudioQualityTier.entries.firstOrNull {
                wire(it.name) == item.getString("bestQuality")
            } ?: error("invalid quality") else null
            MusicTrack(id, title, artists, album, artwork ?: album?.artworkUrl, duration, availability,
                bestQuality = quality, bestBitrate = optionalInteger(item, "bestBitrate", 1..Int.MAX_VALUE))
        }
        return SearchPage(tracks, page, pageSize, result.getBoolean("hasMore"))
    }

    private fun resource(source: SourceId, item: JSONObject, key: String): MusicResourceId? {
        if (!item.has(key)) return null
        val value = text(item, key, 4096)
        require(value.toByteArray(Charsets.UTF_8).size <= 4096) { "resource id too large" }
        return MusicResourceId(source, value)
    }
    private fun text(item: JSONObject, key: String, maximum: Int): String {
        require(item.opt(key) is String) { "$key must be a string" }
        return item.getString(key).also { require(it.isNotBlank() && it.codePointCount(0, it.length) <= maximum) { "invalid $key" } }
    }
    private fun url(item: JSONObject, key: String): String? = if (!item.has(key)) null
        else text(item, key, 4096).also(::validateUrlSyntax)
    private fun optionalInteger(item: JSONObject, key: String, range: IntRange): Int? =
        if (!item.has(key)) null else integer(item, key, range)
    private fun integer(item: JSONObject, key: String, range: IntRange): Int {
        require(item.opt(key) is Number) { "$key must be a number" }
        val value = item.getDouble(key)
        require(value.isFinite() && value == kotlin.math.floor(value) && value >= range.first && value <= range.last) { "invalid $key" }
        return value.toInt()
    }
    private fun wire(value: String) = value.replaceFirstChar { it.lowercaseChar() }

    /** This checks syntax only. Requests still require a network broker and address checks. */
    fun validateUrlSyntax(value: String) {
        require(value.length <= 4096) { "URL too long" }
        val uri = URI(value)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "invalid URL" }
    }

    private fun parse(json: String): JSONObject {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) { "plugin result too large" }
        PluginJson.check(json)
        return JSONObject(json)
    }
}
