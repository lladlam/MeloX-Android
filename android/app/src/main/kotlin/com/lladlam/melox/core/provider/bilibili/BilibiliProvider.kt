package com.lladlam.melox.core.provider.bilibili

import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.*
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

class BilibiliProvider(
    private val sessionProvider: () -> BilibiliSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
    private val associationProvider: (String, Long) -> BilibiliPlaybackAssociation? = { _, _ -> null },
    private val apiCache: BilibiliApiCache? = null,
    private val sessionRevisionProvider: () -> Long = { 0L },
    /**
     * 「B 站定向流量（实验性）」当前是否生效（开关打开 + 移动数据）。
     *
     * 默认关闭，保持既有选线行为；开启后只在候选地址之间挑选官方 CDN 线路，
     * 不会改写 WBI 请求的平台身份，也不会动 Cookie / Referer / User-Agent。
     */
    private val directedTrafficProvider: () -> Boolean = { false },
) : MusicProvider, SearchCapability, PlaybackCapability, UserLibraryCapability, PlaylistCapability {
    override val source = MusicSource.Bilibili
    override val displayName = source.displayName
    override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Library, MusicCapability.Playlists)

    override suspend fun accountSummary(): MusicAccountSummary? {
        val session = sessionProvider()
        if (!session.isLoggedIn) return null
        val data = runCatching {
            cachedGet("account", "/x/web-interface/nav", emptyMap(), BilibiliApiCache.Account).data()
        }.getOrNull()
        val mid = data?.optLong("mid")?.takeIf { it > 0 }?.toString()
            ?: session.userId
        return MusicAccountSummary(
            source,
            mid,
            data?.optString("uname").orEmpty().ifBlank { "Bilibili 用户 $mid" },
            secure(data?.optString("face").orEmpty()),
        )
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> {
        val session = sessionProvider()
        if (!session.isLoggedIn) return MusicPage(emptyList(), page, pageSize, 0)
        val created = cachedGet(
            "folders", "/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to session.userId, "type" to "2"), BilibiliApiCache.Folders,
        ).data().optJSONArray("list") ?: JSONArray()
        // The library must also show folders collected from other users. The
        // old implementation only read `created/list-all`, so an account whose
        // favorites were all collected appeared as an empty playlist page.
        val collected = buildList {
            var page = 1
            while (page <= 5) {
                val response = runCatching {
                    cachedGet(
                        "folders-collected",
                        "/x/v3/fav/folder/collected/list",
                        mapOf(
                            "up_mid" to session.userId,
                            "pn" to page.toString(),
                            "ps" to "40",
                            "platform" to "web",
                        ),
                        BilibiliApiCache.Folders,
                    )
                }.getOrNull() ?: break
                val pageItems = response.data().optJSONArray("list") ?: JSONArray()
                if (pageItems.length() == 0) break
                addAll(pageItems.objects())
                if (pageItems.length() < 40) break
                page++
            }
        }
        val list = JSONArray().also { array ->
            created.objects().forEach { array.put(it) }
            collected.forEach { array.put(it) }
        }
        val all = list.objects().mapNotNull { item ->
            val id = item.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            val season = item.optInt("type") == 21
            val owner = item.optLong("mid", item.optJSONObject("upper")?.optLong("mid") ?: 0)
            val value = if (season) "season:$owner:$id" else id.toString()
            MusicPlaylistSummary(
                MusicResourceId(source, value), item.optString("title"), secure(item.optString("cover")),
                trackCount = item.optInt("media_count"),
                creatorName = item.optJSONObject("upper")?.optString("name"),
                isEditable = false,
            )
        }.distinctBy { it.id }
        val start = ((page.coerceAtLeast(1) - 1) * pageSize).coerceAtMost(all.size)
        val selected = all.drop(start).take(pageSize).map { playlist ->
            runCatching { playlistSummaryWithCover(playlist) }.getOrDefault(playlist)
        }
        return MusicPage(selected, page, pageSize, all.size.toLong())
    }

    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
        require(playlist.id.source == source)
        if (playlist.id.value.startsWith("season:")) return seasonDetail(playlist, page, pageSize)
        val params = mapOf(
            "media_id" to playlist.id.value, "pn" to page.toString(), "ps" to pageSize.coerceIn(1, 20).toString(),
            "order" to "mtime", "tid" to "0", "type" to "0", "platform" to "web",
        )
        val data = cachedGet("folder-detail", "/x/v3/fav/resource/list", params, BilibiliApiCache.FolderDetail).data()
        val medias = data.optJSONArray("medias") ?: JSONArray()
        val tracks = favoriteTracks(medias)
        val info = data.optJSONObject("info")
        val summary = playlist.copy(
            title = info?.optString("title").orEmpty().ifBlank { playlist.title },
            artworkUrl = secure(info?.optString("cover").orEmpty())
                ?: secure(medias.optJSONObject(0)?.optString("cover").orEmpty())
                ?: playlist.artworkUrl,
            creatorName = info?.optJSONObject("upper")?.optString("name").orEmpty()
                .ifBlank { playlist.creatorName.orEmpty() }
                .ifBlank { null },
            description = info?.optString("intro").orEmpty()
                .ifBlank { playlist.description.orEmpty() }
                .ifBlank { null },
            trackCount = info?.optInt("media_count")?.takeIf { it >= 0 } ?: playlist.trackCount,
        )
        return MusicPlaylistDetail(summary, tracks, info?.optLong("media_count"), data.optBoolean("has_more", medias.length() >= pageSize.coerceIn(1, 20)))
    }

    private fun seasonDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
        val identity = playlist.id.value.split(':')
        require(identity.size == 3 && identity[1].toLongOrNull()?.let { it > 0 } == true && identity[2].toLongOrNull()?.let { it > 0 } == true) { "Bilibili 合集身份不完整" }
        val size = pageSize.coerceIn(1, 20)
        val data = cachedGet("season-detail", "/x/polymer/web-space/seasons_archives_list", mapOf(
            "mid" to identity[1], "season_id" to identity[2], "page_num" to page.coerceAtLeast(1).toString(),
            "page_size" to size.toString(), "sort_reverse" to "false",
        ), BilibiliApiCache.FolderDetail).data()
        val archives = data.optJSONArray("archives") ?: JSONArray()
        val meta = data.optJSONObject("meta")
        val total = data.optJSONObject("page")?.optLong("total") ?: meta?.optLong("total") ?: playlist.trackCount?.toLong()
        val summary = playlist.copy(
            title = meta?.optString("name").orEmpty().ifBlank { playlist.title },
            artworkUrl = secure(meta?.optString("cover").orEmpty()) ?: playlist.artworkUrl,
            description = meta?.optString("description").orEmpty().ifBlank { playlist.description },
            trackCount = total?.toInt(),
        )
        return MusicPlaylistDetail(summary, favoriteTracks(archives), total,
            if (total != null) page.toLong() * size < total else archives.length() >= size)
    }

    private fun favoriteTracks(medias: JSONArray): List<MusicTrack> = medias.objects().flatMap { media ->
        if (media.optInt("type", 2) != 2) return@flatMap emptyList()
        val bvid = media.optString("bvid").ifBlank { media.optString("bv_id") }
        if (bvid.isBlank()) return@flatMap emptyList()
        val cid = media.optJSONObject("ugc")?.optLong("first_cid")?.takeIf { it > 0 }
            ?: media.optLong("cid").takeIf { it > 0 }
        val owner = media.optJSONObject("upper")?.optString("name")
        if (cid != null && media.optInt("page", 1) <= 1) {
            listOf(mapTrack(media, bvid, cid, media.optLong("id", media.optLong("aid")), 1,
                durationMs = media.optLong("duration").takeIf { it > 0 }?.times(1_000), owner = owner))
        } else {
            try {
                videoTracks(bvid, media)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // A deleted/private video must not discard every other item in the folder.
                if (cid == null) emptyList() else listOf(mapTrack(media, bvid, cid, media.optLong("id"), 1,
                    durationMs = media.optLong("duration").takeIf { it > 0 }?.times(1_000), owner = owner))
            }
        }
    }

    private fun playlistSummaryWithCover(playlist: MusicPlaylistSummary): MusicPlaylistSummary {
        if (!playlist.artworkUrl.isNullOrBlank()) return playlist
        if (playlist.id.value.startsWith("season:")) return playlist
        val params = mapOf(
            "media_id" to playlist.id.value, "pn" to "1", "ps" to "20",
            "order" to "mtime", "tid" to "0", "type" to "0", "platform" to "web",
        )
        val data = cachedGet("folder-detail", "/x/v3/fav/resource/list", params, BilibiliApiCache.FolderDetail).data()
        val cover = secure(data.optJSONObject("info")?.optString("cover").orEmpty())
            ?: secure(data.optJSONArray("medias")?.optJSONObject(0)?.optString("cover").orEmpty())
        return playlist.copy(artworkUrl = cover)
    }

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
        if (query.isBlank()) return MusicPage(emptyList(), page, pageSize, 0)
        val params = mapOf(
            "keyword" to BilibiliApiCache.normalizeSearchQuery(query), "search_type" to "video",
            "page" to page.toString(), "page_size" to pageSize.toString(),
        )
        val data = cachedWbiGet("search", "/x/web-interface/wbi/search/type", params, BilibiliApiCache.Search).data()
        val results = data.optJSONArray("result") ?: JSONArray()
        val tracks = results.objects().mapNotNull { item ->
            val bvid = item.optString("bvid").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val cid = item.optLong("cid").takeIf { it > 0 } ?: runCatching { view(bvid).optLong("cid") }.getOrNull()
            cid?.takeIf { it > 0 }?.let { mapTrack(item, bvid, it, item.optLong("aid"), 1) }
        }
        return MusicPage(tracks, page, pageSize, data.optLong("numResults").takeIf { it > 0 })
    }

    suspend fun searchReplacementCandidates(query: String, pageSize: Int = 20): List<MusicTrack> =
        searchSongs(query, page = 1, pageSize = pageSize.coerceIn(1, 50)).items

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        val metadata = track.providerMetadata as? ProviderTrackMetadata.Bilibili
            ?: parseIdentity(track.id.value)?.let { ProviderTrackMetadata.Bilibili(it.first, it.second) }
            ?: return PlaybackResolution.Unavailable("Bilibili 资源身份缺少 bvid+cid")
        val association = associationProvider(metadata.bvid, metadata.cid)
        val physicalBvid = association?.replacementBvid ?: metadata.bvid
        val physicalCid = association?.replacementCid ?: metadata.cid
        val params = mapOf(
            "bvid" to physicalBvid, "cid" to physicalCid.toString(), "qn" to "127", "fnval" to "4048",
            "fnver" to "0", "fourk" to "1",
        )
        var response = wbiGet("/x/player/wbi/playurl", params, retry403 = false)
        if (response.optInt("code") == -403) {
            apiCache?.invalidateMixin(sessionScope())
            response = wbiGet("/x/player/wbi/playurl", params, retry403 = false)
        }
        val data = response.data()
        val directedTrafficActive = directedTrafficProvider()
        val candidates = buildList {
            addAudio(data.optJSONObject("dash")?.optJSONArray("audio"))
            addAudio(data.optJSONObject("dash")?.optJSONObject("flac")?.opt("audio"))
            addAudio(data.optJSONObject("dash")?.optJSONObject("dolby")?.optJSONArray("audio"))
        }.distinctBy { it.id to it.urls }
            .filter { it.id in SafeAndroidAudioIds }
        val selected = quality.bilibiliAudioPreference()
            .firstNotNullOfOrNull { preferredId ->
                val group = candidates.filter { it.id == preferredId }
                // 定向流量生效时，同音质优先选带官方 CDN 线路的候选。
                if (directedTrafficActive) {
                    group.firstOrNull { BilibiliDirectedTrafficPolicy.hasOfficialCandidate(it.urls) }
                        ?: group.firstOrNull()
                } else {
                    group.firstOrNull()
                }
            }
            ?: candidates.firstOrNull()
            ?: return PlaybackResolution.Unavailable("Bilibili 没有返回 DASH 音频")
        val mediaUrl = BilibiliDirectedTrafficPolicy.selectMediaUrl(selected.urls, directedTrafficActive)
            ?: return PlaybackResolution.Unavailable("Bilibili 没有返回 DASH 音频")
        android.util.Log.i(
            "MeloXBilibiliPlayback",
            "selected bvid=$physicalBvid cid=$physicalCid requested=$quality " +
                "audioId=${selected.id} mime=${selected.mime} bandwidth=${selected.bandwidth} " +
                "directedTraffic=$directedTrafficActive",
        )
        val cookie = sessionProvider().cookie
        if (cookie.isBlank()) return PlaybackResolution.LoginRequired
        return PlaybackResolution.Playable(
            url = mediaUrl,
            requestHeaders = mapOf(
                "Referer" to "https://www.bilibili.com/video/$physicalBvid",
                "User-Agent" to UserAgent,
                "Cookie" to cookie,
            ),
            requestedQuality = quality,
            actualQuality = selected.id.bilibiliTier(),
            bitrate = selected.bandwidth,
            format = selected.mime,
            expiresAtEpochMs = BilibiliApiCache.playbackExpiry(mediaUrl),
        )
    }

    private fun videoTracks(bvid: String, fallback: JSONObject): List<MusicTrack> {
        val view = view(bvid)
        val pages = view.optJSONArray("pages") ?: JSONArray()
        return pages.objects().mapIndexedNotNull { index, page ->
            val cid = page.optLong("cid").takeIf { it > 0 } ?: return@mapIndexedNotNull null
            mapTrack(
                fallback, bvid, cid, view.optLong("aid"), page.optInt("page", index + 1),
                page.optString("part").takeIf(String::isNotBlank), page.optLong("duration").takeIf { it > 0 }?.times(1000),
                view.optJSONObject("owner")?.optString("name"), view.optString("pic"),
            )
        }
    }

    private fun view(bvid: String) = cachedGet(
        "view", "/x/web-interface/view", mapOf("bvid" to bvid), BilibiliApiCache.View,
    ).data()

    private fun mapTrack(item: JSONObject, bvid: String, cid: Long, aid: Long, page: Int, part: String? = null,
                         durationMs: Long? = null, owner: String? = null, pic: String? = null): MusicTrack {
        val baseTitle = cleanTitle(item.optString("title").ifBlank { "Bilibili 视频" })
        val title = part?.takeIf { it != baseTitle }?.let { "$baseTitle - $it" } ?: baseTitle
        val artist = cleanTitle(
            owner.orEmpty().ifBlank {
                item.optString("author").ifBlank { item.optString("upper_name") }
            },
        ).ifBlank { "未知 UP 主" }
        return MusicTrack(
            MusicResourceId(source, "$bvid:$cid"), title, listOf(MusicArtistRef(name = artist)),
            artworkUrl = secure(pic.orEmpty().ifBlank { item.optString("pic").ifBlank { item.optString("cover") } }),
            durationMs = durationMs ?: parseDuration(item.optString("duration")), availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.Bilibili(bvid, cid, aid.takeIf { it > 0 }, page),
            bestQuality = AudioQualityTier.High,
            bestBitrate = 192_000,
        )
    }

    private fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject = request(path, params)
    private fun cachedGet(operation: String, path: String, params: Map<String, String>, policy: BilibiliApiCache.Policy): JSONObject {
        val cache = apiCache ?: return get(path, params)
        return cache.getOrLoad(BilibiliApiCache.cacheKey(sessionScope(), operation, params), policy) {
            request(path, params)
        }
    }
    private fun cachedWbiGet(operation: String, path: String, params: Map<String, String>, policy: BilibiliApiCache.Policy): JSONObject {
        val cache = apiCache ?: return wbiGet(path, params)
        return cache.getOrLoad(BilibiliApiCache.cacheKey(sessionScope(), operation, params), policy) {
            wbiGet(path, params)
        }
    }
    private fun wbiGet(path: String, params: Map<String, String>, retry403: Boolean = true): JSONObject {
        val signed = BilibiliWbiSigner.sign(params, currentMixinKey(), System.currentTimeMillis() / 1000)
        val result = request(path, signed)
        if (retry403 && result.optInt("code") == -403) {
            apiCache?.invalidateMixin(sessionScope())
            return wbiGet(path, params, false)
        }
        return result
    }

    private fun currentMixinKey(): String {
        val loader = {
            cachedGet("nav", "/x/web-interface/nav", emptyMap(), BilibiliApiCache.Account)
                .data().optJSONObject("wbi_img")?.let {
                    BilibiliWbiSigner.mixinKey(it.optString("img_url"), it.optString("sub_url"))
                }?.takeIf(String::isNotBlank) ?: throw IOException("无法获取 Bilibili WBI key")
        }
        return apiCache?.mixinKey(sessionScope(), loader) ?: loader()
    }

    private fun sessionScope(): String {
        val session = sessionProvider()
        return BilibiliSessionStore.scope(session.userId, sessionRevisionProvider())
    }

    private fun request(path: String, params: Map<String, String>): JSONObject {
        val url = "https://api.bilibili.com$path".toHttpUrl().newBuilder().apply { params.toSortedMap().forEach(::addQueryParameter) }.build()
        val session = sessionProvider()
        val request = Request.Builder().url(url).header("User-Agent", UserAgent).header("Referer", "https://www.bilibili.com/").apply {
            session.cookie.takeIf(String::isNotBlank)?.let { header("Cookie", it) }
        }.build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bilibili HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun JSONObject.data(): JSONObject {
        val code = optInt("code")
        if (code != 0) throw IOException(optString("message").ifBlank { "Bilibili API $code" })
        return optJSONObject("data") ?: JSONObject()
    }

    /** 一条 DASH 音频轨：`urls` 为 `base_url` + 全部 `backup_url`（按服务端顺序）。 */
    private data class Audio(val id: Int, val urls: List<String>, val bandwidth: Int?, val mime: String?)
    private fun MutableList<Audio>.addAudio(value: Any?) {
        when (value) {
            is JSONArray -> addAudio(value)
            is JSONObject -> addAudio(value)
        }
    }
    private fun MutableList<Audio>.addAudio(array: JSONArray?) {
        if (array == null) return
        array.objects().forEach { addAudio(it) }
    }
    private fun MutableList<Audio>.addAudio(item: JSONObject) {
        val backups = item.optJSONArray("backup_url") ?: item.optJSONArray("backupUrl")
        val urls = buildList {
            item.optString("base_url").ifBlank { item.optString("baseUrl") }
                .takeIf(String::isNotBlank)?.let(::add)
            if (backups != null) {
                for (index in 0 until backups.length()) {
                    val value = backups.optString(index).orEmpty()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.distinct()
        if (urls.isEmpty()) return
        add(
            Audio(
                item.optInt("id"),
                urls,
                item.optInt("bandwidth").takeIf { it > 0 },
                item.optString("mime_type").ifBlank { item.optString("mimeType") },
            ),
        )
    }

    companion object {
        const val UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        private val SafeAndroidAudioIds = setOf(30216, 30232, 30280, 30250)
        fun cleanTitle(value: String) = value.replace(Regex("</?em[^>]*>", RegexOption.IGNORE_CASE), "").trim()
        fun parseDuration(value: String): Long? {
            val parts = value.trim().split(':').mapNotNull(String::toLongOrNull)
            if (parts.size !in 2..3) return null
            return parts.fold(0L) { total, part -> total * 60 + part } * 1000
        }
        fun parseIdentity(value: String): Pair<String, Long>? {
            val separator = value.lastIndexOf(':')
            if (separator <= 0) return null
            return value.substring(0, separator) to (value.substring(separator + 1).toLongOrNull() ?: return null)
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)
private fun secure(value: String): String? = when {
    value.startsWith("//") -> "https:$value"
    value.startsWith("http://", ignoreCase = true) -> "https://${value.substring(7)}"
    else -> value
}
    .takeIf(String::isNotBlank)
private fun AudioQualityTier.bilibiliAudioPreference() = when (this) {
    AudioQualityTier.Standard -> listOf(30216)
    AudioQualityTier.High -> listOf(30232, 30216)
    AudioQualityTier.Lossless -> listOf(30280, 30232, 30216)
    // Android's platform FLAC decoder can expose only a 32 KiB input buffer.
    // Bilibili 96 kHz 30251 frames exceed it and put Media3 in a fatal state.
    AudioQualityTier.HiResolution, AudioQualityTier.Master -> listOf(30280, 30232, 30216)
    AudioQualityTier.Immersive -> listOf(30250, 30280, 30232, 30216)
}
private fun Int.bilibiliTier() = when (this) {
    30250 -> AudioQualityTier.Immersive
    30251 -> AudioQualityTier.HiResolution
    30280 -> AudioQualityTier.Lossless
    30232 -> AudioQualityTier.High
    else -> AudioQualityTier.Standard
}
