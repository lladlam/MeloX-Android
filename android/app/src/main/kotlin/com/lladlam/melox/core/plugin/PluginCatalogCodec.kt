package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned standard transport only. No upstream/platform field inference. */
internal class PluginCatalogCodec(private val manifest: PluginManifest) {
    private val source = manifest.sourceId
    private fun artwork(value: JSONObject) = PluginArtwork.wrap(manifest, optionalText(value, "artworkUrl"))
    fun id(value: JSONObject): MusicResourceId {
        val text = text(value, "id", 4096)
        require(text.toByteArray(Charsets.UTF_8).size <= 4096) { "resource id too large" }
        return MusicResourceId(source, text)
    }
    fun playlist(value: JSONObject) = MusicPlaylistSummary(id(value), text(value, "title"), artworkUrl = artwork(value),
        creatorName = optionalText(value, "creatorName"), description = optionalText(value, "description"),
        trackCount = optionalLong(value, "trackCount")?.also { require(it <= Int.MAX_VALUE) }?.toInt(),
        playCount = optionalLong(value, "playCount"), isEditable = false)
    fun album(value: JSONObject) = MusicAlbumSummary(id(value), text(value, "title"), artworkUrl = artwork(value),
        artists = value.optJSONArray("artists")?.let { list ->
            require(list.length() <= 32)
            (0 until list.length()).map { index ->
                val artist = list.getJSONObject(index)
                MusicArtistRef(if (artist.has("id")) id(artist) else null, text(artist, "name", 512))
            }
        }.orEmpty(), releaseDate = optionalText(value, "releaseDate", 80), trackCount = optionalLong(value, "trackCount"))
    fun artist(value: JSONObject) = MusicArtistSummary(id(value), text(value, "name", 512), artworkUrl = artwork(value),
        description = optionalText(value, "description"), songCount = optionalLong(value, "songCount"),
        albumCount = optionalLong(value, "albumCount"))
    fun ranking(value: JSONObject) = MusicRankingSummary(id(value), text(value, "title"), artworkUrl = artwork(value), subtitle = optionalText(value, "subtitle"))

    fun <T> page(value: JSONObject, requestedPage: Int, requestedSize: Int, decode: (JSONObject) -> T): MusicPage<T> {
        require(requestedPage > 0 && requestedSize in 1..100)
        require(number(value, "page") == requestedPage.toLong() && number(value, "pageSize") == requestedSize.toLong()) { "pagination mismatch" }
        require(value.opt("hasMore") is Boolean) { "hasMore must be a boolean" }
        val items = value.getJSONArray("items")
        require(items.length() <= requestedSize) { "too many page items" }
        return MusicPage((0 until items.length()).map { decode(items.getJSONObject(it)) }, requestedPage, requestedSize,
            total = optionalLong(value, "total"), hasMore = value.getBoolean("hasMore"))
    }
    fun tracks(value: JSONObject, page: Int, size: Int): MusicPage<MusicTrack> {
        val checked = PluginResultValidator.search(source, value.toString())
        require(checked.page == page && checked.pageSize == size) { "pagination mismatch" }
        return MusicPage(checked.tracks.map { it.copy(artworkUrl = PluginArtwork.wrap(manifest, it.artworkUrl),
            album = it.album?.copy(artworkUrl = PluginArtwork.wrap(manifest, it.album.artworkUrl))) },
            page, size, optionalLong(value, "total"), checked.hasMore)
    }
    fun <T> list(value: JSONObject, key: String, limit: Int, decode: (JSONObject) -> T): List<T> {
        val items = value.getJSONArray(key)
        require(items.length() <= limit) { "too many $key" }
        return (0 until items.length()).map { decode(items.getJSONObject(it)) }
    }
    fun track(value: JSONObject): MusicTrack = tracks(JSONObject().put("page", 1).put("pageSize", 1)
        .put("hasMore", false).put("items", JSONArray().put(value)), 1, 1).items.single()
    fun account(value: JSONObject): MusicAccountSummary = MusicAccountSummary(source, text(value, "id", 4096),
        text(value, "displayName", 512), avatarUrl = PluginArtwork.wrap(manifest, optionalText(value, "avatarUrl")), subtitle = optionalText(value, "subtitle"))

    fun text(value: JSONObject, key: String, maximum: Int = 4096): String {
        require(value.opt(key) is String) { "$key must be a string" }
        return value.getString(key).also { require(it.isNotBlank() && it.codePointCount(0, it.length) <= maximum) { "invalid $key" } }
    }
    fun optionalText(value: JSONObject, key: String, maximum: Int = 4096): String? =
        if (value.has(key)) text(value, key, maximum) else null
    fun optionalLong(value: JSONObject, key: String): Long? = if (value.has(key)) number(value, key) else null
    private fun number(value: JSONObject, key: String): Long {
        require(value.opt(key) is Number) { "$key must be a number" }
        val number = value.getDouble(key)
        require(number.isFinite() && number >= 0 && number <= 9_007_199_254_740_991.0 && number == kotlin.math.floor(number)) { "invalid $key" }
        return number.toLong()
    }
}
