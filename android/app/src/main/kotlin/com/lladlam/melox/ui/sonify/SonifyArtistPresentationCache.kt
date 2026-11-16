/*
 * Sonify ArtistPresentationCache (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/data/objects/ArtistPresentationCache.kt (master
 * @ 209e1aa). It caches artwork palettes, follow state and album song counts.
 *
 * MeloX differences, kept intentionally small:
 *   - storage is SharedPreferences + org.json instead of MMKV/Gson, because
 *     MeloX already ships org.json and does not depend on Gson;
 *   - artist detail objects are not duplicated here. MeloX keeps them in
 *     rememberRetainedPageState / NeteaseLibraryCache, so only the colour and
 *     follow presentation state needs a cross-page cache.
 */
package com.lladlam.melox.ui.sonify

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val PREFERENCES_NAME = "sonify_presentation"
private const val CACHE_KEY = "artist_presentation_cache_v1"
private const val DETAIL_TTL = 24 * 60 * 60 * 1000L
private const val FOLLOW_TTL = 5 * 60 * 1000L

private data class Follow(val value: Boolean, val loadedAt: Long)

internal class ArtistPresentationStore(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val palettes = linkedMapOf<String, MutableState<Int?>>()
    private val follows = mutableMapOf<Pair<String, String>, Follow>()
    private val albumSongCounts = linkedMapOf<String, Int>()
    private val artistPaletteKeys = linkedMapOf<String, String>()
    private val paletteRequests = mutableMapOf<String, CompletableDeferred<Int?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loaded = false

    @Synchronized
    fun preload() {
        if (loaded) return
        loaded = true
        val raw = prefs.getString(CACHE_KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.optJSONArray("palettes")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    if (key.isNotBlank() && item.has("rgb")) {
                        palettes[key] = mutableStateOf(item.optInt("rgb"))
                    }
                }
            }
            root.optJSONArray("albumSongCounts")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val count = item.optInt("count")
                    if (id.isNotBlank() && count > 0) albumSongCounts[id] = count
                }
            }
            root.optJSONArray("artistPaletteKeys")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val key = item.optString("key")
                    val value = item.optString("value")
                    if (key.isNotBlank() && value.isNotBlank()) artistPaletteKeys[key] = value
                }
            }
            root.optJSONArray("follows")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val account = item.optString("account")
                    if (id.isNotBlank()) {
                        follows[id to account] = Follow(item.optBoolean("value"), item.optLong("at"))
                    }
                }
            }
        }
    }

    @Synchronized
    fun followed(id: String, accountId: String): Boolean? = follows[id to accountId]
        ?.takeIf { now() - it.loadedAt in 0 until FOLLOW_TTL }?.value

    @Synchronized
    fun setFollowed(id: String, accountId: String, value: Boolean) {
        follows[id to accountId] = Follow(value, now())
        trim()
        persist()
    }

    @Synchronized
    fun palette(key: String): State<Int?> {
        preload()
        val state = palettes.remove(key) ?: mutableStateOf<Int?>(null)
        palettes[key] = state
        trimPalettes()
        return state
    }

    suspend fun loadPalette(key: String, compute: suspend () -> Int?): Int? {
        val request = synchronized(this) {
            val state = palette(key)
            state.value?.let { return it }
            paletteRequests[key] ?: CompletableDeferred<Int?>().also { deferred ->
                paletteRequests[key] = deferred
                scope.launch {
                    try {
                        val rgb = compute()
                        if (rgb != null) synchronized(this@ArtistPresentationStore) {
                            (state as MutableState<Int?>).value = rgb
                            palettes.remove(key)
                            palettes[key] = state
                            persist()
                        }
                        deferred.complete(rgb)
                    } catch (e: Exception) {
                        deferred.completeExceptionally(e)
                    } finally {
                        synchronized(this@ArtistPresentationStore) { paletteRequests.remove(key) }
                    }
                }
            }
        }
        return request.await()
    }

    fun ensurePalette(key: String, compute: suspend () -> Int?) {
        if (key.isBlank()) return
        scope.launch { runCatching { loadPalette(key, compute) } }
    }

    @Synchronized
    fun seedPalette(key: String, rgb: Int) {
        preload()
        if (key.isBlank()) return
        val state = palettes.remove(key) ?: mutableStateOf<Int?>(null)
        palettes[key] = state
        if (state.value != rgb) {
            (state as MutableState<Int?>).value = rgb
            trimPalettes()
            persist()
        }
    }

    @Synchronized
    fun albumSongCount(albumId: String): Int? {
        preload()
        return albumSongCounts.remove(albumId)?.also { albumSongCounts[albumId] = it }
    }

    @Synchronized
    fun rememberAlbumSongCount(albumId: String, count: Int) {
        preload()
        if (albumId.isBlank() || count <= 0) return
        albumSongCounts.remove(albumId)
        albumSongCounts[albumId] = count
        trim()
        persist()
    }

    @Synchronized
    fun paletteKeyFor(artistId: String, heroAspect: Float): String? {
        if (artistId.isBlank()) return null
        preload()
        return artistPaletteKeys["$artistId#$heroAspect"]
    }

    @Synchronized
    fun rememberArtistPalette(artistId: String, heroAspect: Float, paletteKey: String) {
        if (artistId.isBlank() || paletteKey.isBlank()) return
        preload()
        val key = "$artistId#$heroAspect"
        artistPaletteKeys.remove(key)
        artistPaletteKeys[key] = paletteKey
        trim()
        persist()
    }

    private fun trim() {
        while (follows.size > 128) follows.remove(follows.minByOrNull { it.value.loadedAt }!!.key)
        while (albumSongCounts.size > 128) albumSongCounts.remove(albumSongCounts.keys.first())
        while (artistPaletteKeys.size > 128) artistPaletteKeys.remove(artistPaletteKeys.keys.first())
        trimPalettes()
    }

    private fun trimPalettes() {
        while (palettes.size > 128) palettes.remove(palettes.keys.first())
    }

    private fun persist() {
        val root = JSONObject()
        root.put("palettes", JSONArray().apply {
            palettes.forEach { (key, state) ->
                state.value?.let { rgb ->
                    put(JSONObject().put("key", key).put("rgb", rgb))
                }
            }
        })
        root.put("albumSongCounts", JSONArray().apply {
            albumSongCounts.forEach { (id, count) ->
                put(JSONObject().put("id", id).put("count", count))
            }
        })
        root.put("artistPaletteKeys", JSONArray().apply {
            artistPaletteKeys.forEach { (key, value) ->
                put(JSONObject().put("key", key).put("value", value))
            }
        })
        root.put("follows", JSONArray().apply {
            follows.forEach { (key, follow) ->
                put(
                    JSONObject()
                        .put("id", key.first)
                        .put("account", key.second)
                        .put("value", follow.value)
                        .put("at", follow.loadedAt),
                )
            }
        })
        prefs.edit().putString(CACHE_KEY, root.toString()).apply()
    }

}

/**
 * Public facade. Call [initialize] once from the artist page with an
 * application context; until then every accessor returns an empty cache.
 */
object ArtistPresentationCache {
    @Volatile
    private var store: ArtistPresentationStore? = null

    fun initialize(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store != null) return
            val prefs = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            store = ArtistPresentationStore(prefs)
        }
    }

    private fun store(): ArtistPresentationStore? = store

    fun preload() = store()?.preload()
    fun followed(id: String, accountId: String) = store()?.followed(id, accountId)
    fun setFollowed(id: String, accountId: String, followed: Boolean) =
        store()?.setFollowed(id, accountId, followed)

    fun palette(key: String): State<Int?> = store()?.palette(key) ?: mutableStateOf(null)
    fun seedPalette(key: String, rgb: Int) = store()?.seedPalette(key, rgb)
    suspend fun loadPalette(key: String, compute: suspend () -> Int?): Int? =
        store()?.loadPalette(key, compute)
    fun ensurePalette(key: String, compute: suspend () -> Int?) = store()?.ensurePalette(key, compute)
    fun albumSongCount(albumId: String): Int? = store()?.albumSongCount(albumId)
    fun rememberAlbumSongCount(albumId: String, count: Int) =
        store()?.rememberAlbumSongCount(albumId, count)
    fun paletteKeyFor(artistId: String, heroAspect: Float): String? =
        store()?.paletteKeyFor(artistId, heroAspect)
    fun rememberArtistPalette(artistId: String, heroAspect: Float, paletteKey: String) =
        store()?.rememberArtistPalette(artistId, heroAspect, paletteKey)
}
