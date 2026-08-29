package com.lladlam.melox.core.provider.kugou

import android.util.Log
import com.lladlam.melox.core.lyrics.KugouKrcLyricsParser
import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Direct Android implementation of the request shapes from MakcRe/KuGouMusicApi. */
class KugouApiClient(
    private val sessionProvider: () -> KugouSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> {
        val normalized = query.trim()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (normalized.isBlank()) return MusicPage(emptyList(), safePage, safeSize, 0)
        val response = requests.get(
            path = "/v3/search/song",
            params = mapOf(
                "albumhide" to "0",
                "iscorrection" to "1",
                "keyword" to normalized,
                "nocollect" to "0",
                "page" to safePage.toString(),
                "pagesize" to safeSize.toString(),
                "platform" to "AndroidFilter",
            ),
            headers = mapOf("x-router" to "complexsearch.kugou.com"),
        )
        val data = response.optJSONObject("data") ?: response
        val list = data.optJSONArray("lists")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("info")
            ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until list.length()) {
                list.optJSONObject(index)?.let(::parseSearchTrack)?.let(::add)
            }
        }
        val total = firstLong(data, "total", "total_count", "totalnum").takeIf { it >= 0L }
        return MusicPage(
            items = tracks,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    suspend fun lyrics(track: MusicTrack): LyricsDocument {
        val metadata = track.requireKugouMetadata()
        val search = requests.get(
            baseUrl = "https://lyrics.kugou.com",
            path = "/v1/search",
            params = buildMap {
                put("album_audio_id", (metadata.albumAudioId ?: 0L).toString())
                put("appid", KugouRequestClient.AppId.toString())
                put("clientver", KugouRequestClient.ClientVersion.toString())
                put("duration", ((track.durationMs ?: 0L) / 1_000L).toString())
                put("hash", metadata.hash)
                put("keyword", "${track.artistText} - ${track.title}")
                put("lrctxt", "1")
                put("man", "no")
            },
            includeDefaults = false,
            sign = false,
        )
        val candidates = search.optJSONArray("candidates")
            ?: search.optJSONObject("data")?.optJSONArray("candidates")
            ?: JSONArray()
        val candidate = (0 until candidates.length())
            .mapNotNull(candidates::optJSONObject)
            .maxByOrNull { it.optInt("score", 0) }
            ?: return LyricsDocument(emptyList())
        val id = candidate.optString("id").ifBlank { candidate.optLong("id", 0L).toString() }
        val accessKey = candidate.optString("accesskey")
            .ifBlank { candidate.optString("access_key") }
        if (id.isBlank() || id == "0" || accessKey.isBlank()) return LyricsDocument(emptyList())

        val downloaded = requests.get(
            baseUrl = "https://lyrics.kugou.com",
            path = "/download",
            params = mapOf(
                "ver" to "1",
                "client" to "android",
                "id" to id,
                "accesskey" to accessKey,
                "fmt" to "krc",
                "charset" to "utf8",
            ),
        )
        val content = downloaded.optString("content")
            .ifBlank { downloaded.optJSONObject("data")?.optString("content").orEmpty() }
        if (content.isBlank()) return LyricsDocument(emptyList())
        val contentType = downloaded.optInt(
            "contenttype",
            downloaded.optJSONObject("data")?.optInt("contenttype", 0) ?: 0,
        )
        return if (contentType == 0) {
            KugouKrcLyricsParser.decodeAndParse(content)
        } else {
            LrcLyricsParser.parse(decodeBase64Utf8(content))
        }
    }

    suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution {
        val metadata = track.requireKugouMetadata()
        val session = sessionProvider()
        val hash = metadata.hash.lowercase()
        val key = md5Hex(
            hash + "57ae12eb6890223e355ccfcb74edf70d" +
                KugouRequestClient.AppId + session.mid + session.userId,
        )
        var lastError: IOException? = null
        for (candidate in quality.kugouPlaybackCandidates()) {
            val response = runCatching {
                requests.get(
                    path = "/v5/url",
                    params = mapOf(
                        "album_id" to (metadata.albumId?.toLongOrNull() ?: 0L).toString(),
                        "area_code" to "1",
                        "hash" to hash,
                        "ssa_flag" to "is_fromtrack",
                        "version" to "11430",
                        "page_id" to "151369488",
                        "quality" to candidate.apiValue,
                        "album_audio_id" to (metadata.albumAudioId ?: 0L).toString(),
                        "behavior" to "play",
                        "pid" to "2",
                        "cmd" to "26",
                        "pidversion" to "3001",
                        "IsFreePart" to "0",
                        "ppage_id" to "463467626,350369493,788954147",
                        "cdnBackup" to "1",
                        "module" to "",
                        "clientver" to "11430",
                        "key" to key,
                    ),
                    headers = mapOf("x-router" to "trackercdn.kugou.com"),
                )
            }.getOrElse { error ->
                lastError = IOException(error.message ?: "酷狗音乐请求失败", error)
                Log.w(TAG, "Playback unavailable: hash=$hash quality=${candidate.apiValue}", error)
                null
            } ?: continue
            val url = findPlaybackUrl(response)
            if (url == null) {
                lastError = IOException("酷狗音乐没有返回 ${candidate.apiValue} 可播放链接")
                Log.w(TAG, "Playback returned no URL: hash=$hash quality=${candidate.apiValue}")
                continue
            }
            if (candidate != quality.kugouQuality()) {
                Log.i(TAG, "Playback quality fallback: hash=$hash requested=${quality.name} actual=${candidate.actualTier}")
            }
            Log.i(
                TAG,
                "Playback URL resolved: hash=$hash quality=${candidate.apiValue} " +
                    "format=${formatFromUrl(url)} endpoint=${url.redactedEndpoint()}",
            )
            return PlaybackResolution.Playable(
                url = secureUrl(url),
                requestedQuality = quality,
                actualQuality = candidate.actualTier,
                format = formatFromUrl(url),
                requestHeaders = mapOf(
                    "User-Agent" to KugouRequestClient.UserAgent,
                    "Referer" to "https://www.kugou.com/",
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                    "Cookie" to session.asCookieMap().entries.joinToString("; ") { (key, value) -> "$key=$value" },
                ),
            )
        }
        val legacyUrl = runCatching {
            resolveLegacyPlaybackUrl(metadata, session)
        }.onFailure { error ->
            Log.w(TAG, "Legacy playback fallback failed: hash=$hash", error)
        }.getOrNull()
        if (!legacyUrl.isNullOrBlank()) {
            Log.i(TAG, "Legacy playback URL resolved: hash=$hash endpoint=${legacyUrl.redactedEndpoint()}")
            return PlaybackResolution.Playable(
                url = secureUrl(legacyUrl),
                requestedQuality = quality,
                actualQuality = AudioQualityTier.Standard,
                format = formatFromUrl(legacyUrl),
                requestHeaders = mapOf(
                    "User-Agent" to KugouRequestClient.UserAgent,
                    "Referer" to "https://www.kugou.com/",
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                    "Cookie" to session.asCookieMap().entries.joinToString("; ") { (key, value) -> "$key=$value" },
                ),
            )
        }

        val message = lastError?.message ?: "酷狗音乐没有返回可播放链接"
        return if (!session.isLoggedIn && message.requiresKugouLogin()) {
            PlaybackResolution.LoginRequired
        } else {
            PlaybackResolution.Unavailable(message)
        }
    }

    private fun parseSearchTrack(item: JSONObject): MusicTrack? {
        val hash = firstString(item, "FileHash", "Hash", "hash", "filehash").uppercase()
        if (hash.isBlank()) return null
        val (title, singerName) = recoverKugouTrackText(
            firstString(item, "SongName", "songname", "AudioName", "audio_name", "FileName", "filename"),
            kugouSingerName(item, "SingerName", "singername", "SingerName2", "author_name", "AuthorName"),
        ).let { (value, singer) -> (value.ifBlank { "未知歌曲" }) to singer }
        val artistNames = singerName
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
        val albumName = firstString(item, "AlbumName", "album_name", "albumname")
        val albumId = firstString(item, "AlbumID", "album_id", "albumid").takeIf(String::isNotBlank)
        val albumAudioId = firstLong(
            item,
            "album_audio_id",
            "MixSongID",
            "mixsongid",
            "AlbumAudioID",
            "Audioid",
            "audio_id",
        ).takeIf { it > 0L }
        val durationSeconds = firstLong(item, "Duration", "duration", "time_length").takeIf { it > 0L }
        val artwork = normalizeKugouArtworkUrl(
            firstString(item, "Image", "image", "img", "album_img", "AlbumImage", "sizable_cover", "cover"),
        )
        return MusicTrack(
            id = MusicResourceId(MusicSource.Kugou, hash),
            title = title,
            artists = artistNames.map { MusicArtistRef(name = it) },
            album = albumName.takeIf(String::isNotBlank)?.let {
                MusicAlbumRef(
                    id = albumId?.let { value -> MusicResourceId(MusicSource.Kugou, value) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = durationSeconds?.times(1_000L),
            providerMetadata = ProviderTrackMetadata.Kugou(
                hash = hash,
                albumAudioId = albumAudioId,
                albumId = albumId,
            ),
        )
    }

    private fun findPlaybackUrl(value: Any?): String? = when (value) {
        is JSONObject -> {
            val preferredKeys = listOf("url", "play_url", "playUrl", "backup_url", "backupUrl")
            preferredKeys.asSequence()
                .mapNotNull { key -> value.opt(key)?.let(::findPlaybackUrl) }
                .firstOrNull()
                ?: value.keys().asSequence().mapNotNull { key -> findPlaybackUrl(value.opt(key)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findPlaybackUrl(value.opt(it)) }.firstOrNull()
        is String -> value.takeIf { it.isPlaybackUrl() }
        else -> null
    }

    /** Older free tracks are still returned by Kugou's mobile playInfo endpoint. */
    private fun resolveLegacyPlaybackUrl(
        metadata: ProviderTrackMetadata.Kugou,
        session: KugouSession,
    ): String? {
        val url = "https://m.kugou.com/app/i/getSongInfo.php".toHttpUrl().newBuilder()
            .addQueryParameter("cmd", "playInfo")
            .addQueryParameter("hash", metadata.hash)
            .addQueryParameter("appid", KugouRequestClient.AppId.toString())
            .addQueryParameter("mid", session.mid)
            .addQueryParameter("dfid", session.dfid.ifBlank { "-" })
            .addQueryParameter("userid", session.userId.toString())
            .addQueryParameter("token", session.token)
            .addQueryParameter("album_audio_id", (metadata.albumAudioId ?: 0L).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", KugouRequestClient.UserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = runCatching { JSONObject(response.body.string()) }.getOrNull() ?: return@use null
            findPlaybackUrl(json.opt("url"))
                ?: findPlaybackUrl(json.opt("play_url"))
                ?: findPlaybackUrl(json.opt("backup_url"))
        }
    }

    private fun firstString(value: JSONObject, vararg keys: String): String =
        keys.asSequence().map(value::optString).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun decodeBase64Utf8(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrDefault("")

    private fun secureUrl(value: String): String =
        if (value.startsWith("http://", ignoreCase = true)) "https://${value.substringAfter("://")}" else value

    private fun formatFromUrl(url: String): String? =
        url.substringBefore('?').substringAfterLast('.', "").takeIf(String::isNotBlank)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val TAG = "MeloXKugou"
    }
}

private data class KugouQuality(
    val apiValue: String,
    val actualTier: AudioQualityTier,
)

private fun AudioQualityTier.kugouQuality(): KugouQuality = when (this) {
    AudioQualityTier.Standard -> KugouQuality("128", AudioQualityTier.Standard)
    AudioQualityTier.High -> KugouQuality("320", AudioQualityTier.High)
    AudioQualityTier.Lossless -> KugouQuality("flac", AudioQualityTier.Lossless)
    AudioQualityTier.HiResolution -> KugouQuality("high", AudioQualityTier.HiResolution)
    AudioQualityTier.Immersive,
    AudioQualityTier.Master -> KugouQuality("high", AudioQualityTier.HiResolution)
}

private fun AudioQualityTier.kugouPlaybackCandidates(): List<KugouQuality> = when (this) {
    AudioQualityTier.Standard -> listOf(AudioQualityTier.Standard.kugouQuality())
    AudioQualityTier.High -> listOf(AudioQualityTier.High.kugouQuality(), AudioQualityTier.Standard.kugouQuality())
    AudioQualityTier.Lossless -> listOf(
        AudioQualityTier.Lossless.kugouQuality(),
        AudioQualityTier.High.kugouQuality(),
        AudioQualityTier.Standard.kugouQuality(),
    )
    AudioQualityTier.HiResolution,
    AudioQualityTier.Immersive,
    AudioQualityTier.Master,
    -> listOf(
        AudioQualityTier.HiResolution.kugouQuality(),
        AudioQualityTier.Lossless.kugouQuality(),
        AudioQualityTier.High.kugouQuality(),
        AudioQualityTier.Standard.kugouQuality(),
    )
}

private fun String.requiresKugouLogin(): Boolean {
    val normalized = lowercase()
    return "登录" in normalized || "login" in normalized || "token" in normalized || "userid" in normalized
}

private fun String.isPlaybackUrl(): Boolean {
    if (!startsWith("http://") && !startsWith("https://")) return false
    return formatFromPlaybackUrl() !in setOf("avif", "bmp", "gif", "heic", "jpeg", "jpg", "png", "webp")
}

private fun String.formatFromPlaybackUrl(): String? =
    substringBefore('?').substringAfterLast('.', "").lowercase().takeIf(String::isNotBlank)

private fun String.redactedEndpoint(): String = runCatching {
    val parsed = java.net.URI(this)
    "${parsed.host ?: "unknown"}${parsed.path ?: "/"}"
}.getOrDefault("invalid")

private fun MusicTrack.requireKugouMetadata(): ProviderTrackMetadata.Kugou {
    require(id.source == MusicSource.Kugou) {
        "KugouApiClient cannot handle ${id.source.storageValue} track"
    }
    return (providerMetadata as? ProviderTrackMetadata.Kugou)
        ?: ProviderTrackMetadata.Kugou(hash = id.value)
}
