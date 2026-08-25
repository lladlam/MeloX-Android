package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import java.io.IOException
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class QQMusicAccountProfile(
    val uin: String,
    val nickname: String,
    val avatarUrl: String?,
)

/**
 * Direct Android implementation of QQ Music request shapes. The phone talks to
 * QQ Music directly; MeloX never proxies or uploads the user's login state.
 */
class QQMusicApiClient(
    private val sessionProvider: () -> QQMusicSession = { QQMusicSession("", "", "") },
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    suspend fun accountProfile(
        session: QQMusicSession = sessionProvider(),
    ): QQMusicAccountProfile = withContext(Dispatchers.IO) {
        if (!session.isLoggedIn) throw IOException("QQ音乐登录态不完整")
        val gtk = hash33(session.musicKey).toString()
        val response = getJson(
            baseUrl = "https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg",
            params = mapOf(
                "g_tk" to gtk,
                "format" to "json",
                "inCharset" to "utf-8",
                "outCharset" to "utf-8",
                "notice" to "0",
                "cid" to "205360838",
                "needNewCode" to "0",
                "loginUin" to session.uin,
                "hostUin" to "0",
                "userid" to session.uin,
                "reqfrom" to "1",
            ),
            referer = "https://y.qq.com/",
            cookie = session.cookie,
        )
        val code = response.optInt("code", -1)
        if (code != 0) {
            throw IOException(
                response.optString("message")
                    .ifBlank { response.optString("msg") }
                    .ifBlank { "QQ音乐登录状态无效（$code）" },
            )
        }
        val data = response.optJSONObject("data") ?: response
        val creator = data.optJSONObject("creator")
            ?: data.optJSONObject("user")
            ?: data.optJSONObject("profile")
        val nickname = sequenceOf(
            creator?.optString("nick"),
            creator?.optString("nickname"),
            creator?.optString("name"),
            data.optString("nick"),
            data.optString("nickname"),
            data.optString("name"),
        ).filterNotNull().firstOrNull(String::isNotBlank)
            ?: "QQ音乐用户 ${session.uin}"
        val avatar = sequenceOf(
            creator?.optString("headpic"),
            creator?.optString("avatar"),
            creator?.optString("avatarUrl"),
            data.optString("headpic"),
            data.optString("avatar"),
            data.optString("avatarUrl"),
        ).filterNotNull().firstOrNull(String::isNotBlank)?.let(::secureUrl)
        QQMusicAccountProfile(
            uin = session.uin,
            nickname = nickname,
            avatarUrl = avatar,
        )
    }

    suspend fun accountSummary(): MusicAccountSummary? = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        if (!session.isLoggedIn) return@withContext null
        val profile = accountProfile(session)
        MusicAccountSummary(
            source = MusicSource.QQMusic,
            id = profile.uin,
            displayName = profile.nickname,
            avatarUrl = profile.avatarUrl,
            subtitle = "QQ ${profile.uin}",
        )
    }

    suspend fun homeFeed(
        playlistLimit: Int = 12,
        newSongLimit: Int = 12,
        rankingLimit: Int = 8,
    ): MusicHomeFeed = withContext(Dispatchers.IO) {
        val playlists = runCatching {
            val data = postMusicu(
                module = "music.playlist.PlaylistSquare",
                method = "GetRecommendFeed",
                param = JSONObject()
                    .put("From", 0)
                    .put("Size", playlistLimit.coerceIn(1, 30)),
            )
            parseRecommendedPlaylists(data, playlistLimit)
        }.getOrDefault(emptyList())

        val newSongs = runCatching {
            val data = postMusicu(
                module = "newsong.NewSongServer",
                method = "get_new_song_info",
                param = JSONObject().put("type", 5),
            )
            val list = data.optJSONArray("songlist")
                ?: data.optJSONArray("songs")
                ?: JSONArray()
            buildList {
                for (index in 0 until minOf(list.length(), newSongLimit.coerceAtLeast(1))) {
                    list.optJSONObject(index)?.let(::parseSearchTrack)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

        val rankings = runCatching {
            val data = postMusicu(
                module = "music.musicToplist.Toplist",
                method = "GetAll",
                param = JSONObject(),
            )
            parseRankings(data, rankingLimit)
        }.getOrDefault(emptyList())

        MusicHomeFeed(
            recommendedPlaylists = playlists,
            newSongs = newSongs,
            rankings = rankings,
        )
    }

    suspend fun userPlaylists(
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        if (!session.isLoggedIn) return@withContext MusicPage(emptyList(), 1, pageSize.coerceAtLeast(1), 0)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val data = postMusicu(
            module = "music.musicasset.PlaylistBaseRead",
            method = "GetPlaylistByUin",
            param = JSONObject().put("uin", session.uin),
        )
        val all = data.optJSONArray("v_playlist") ?: JSONArray()
        val from = ((safePage - 1) * safeSize).coerceAtMost(all.length())
        val to = (from + safeSize).coerceAtMost(all.length())
        val items = buildList {
            for (index in from until to) {
                all.optJSONObject(index)?.let(::parsePlaylist)?.let(::add)
            }
        }
        MusicPage(
            items = items,
            page = safePage,
            pageSize = safeSize,
            total = data.optLong("total", all.length().toLong()).coerceAtLeast(all.length().toLong()),
        )
    }

    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext MusicPage(emptyList(), page.coerceAtLeast(1), pageSize.coerceAtLeast(1), 0)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val response = getJson(
            baseUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp",
            params = mapOf(
                "format" to "json",
                "n" to safeSize.toString(),
                "p" to safePage.toString(),
                "w" to normalized,
                "cr" to "1",
                "g_tk" to "5381",
                "t" to "0",
            ),
            referer = "https://y.qq.com",
            cookie = sessionProvider().cookie,
        )
        val data = response.optJSONObject("data") ?: JSONObject()
        val songBlock = data.optJSONObject("song") ?: data
        val list = songBlock.optJSONArray("list") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                parseSearchTrack(item)?.let(::add)
            }
        }
        val total = songBlock.optLong("totalnum", -1L).takeIf { it >= 0L }
        MusicPage(
            items = tracks,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    suspend fun lyrics(track: MusicTrack): LyricsDocument = withContext(Dispatchers.IO) {
        val metadata = track.requireQQMetadata()
        val response = getJson(
            baseUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            params = mapOf(
                "songmid" to metadata.songMid,
                "pcachetime" to System.currentTimeMillis().toString(),
                "g_tk" to "5381",
                "loginUin" to "0",
                "hostUin" to "0",
                "inCharset" to "utf8",
                "outCharset" to "utf-8",
                "notice" to "0",
                "platform" to "yqq",
                "needNewCode" to "0",
            ),
            referer = "https://y.qq.com",
            cookie = sessionProvider().cookie,
        )
        LrcLyricsParser.parse(
            lrc = decodeBase64Utf8(response.optString("lyric")),
            translation = decodeBase64Utf8(response.optString("trans")),
        )
    }

    suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = withContext(Dispatchers.IO) {
        val metadata = track.requireQQMetadata()
        val session = sessionProvider()
        val fileType = quality.qqFileType()
        val mediaMid = metadata.mediaMid?.takeIf(String::isNotBlank) ?: metadata.songMid
        val fileName = "${fileType.prefix}${metadata.songMid}${mediaMid}${fileType.extension}"
        val guid = Random.nextLong(1_000_000L, 9_999_999L).toString()

        val requestData = JSONObject()
            .put(
                "req_0",
                JSONObject()
                    .put("module", "vkey.GetVkeyServer")
                    .put("method", "CgiGetVkey")
                    .put(
                        "param",
                        JSONObject()
                            .put("filename", JSONArray().put(fileName))
                            .put("guid", guid)
                            .put("songmid", JSONArray().put(metadata.songMid))
                            .put("songtype", JSONArray().put(0))
                            .put("uin", session.uin.ifBlank { "0" })
                            .put("loginflag", 1)
                            .put("platform", "20"),
                    ),
            )
            .put(
                "comm",
                JSONObject()
                    .put("uin", session.uin.ifBlank { "0" })
                    .put("format", "json")
                    .put("ct", 19)
                    .put("cv", 0)
                    .put("authst", session.musicKey),
            )

        val response = getJson(
            baseUrl = "https://u.y.qq.com/cgi-bin/musicu.fcg",
            params = mapOf(
                "-" to "getplaysongvkey",
                "g_tk" to "5381",
                "loginUin" to session.uin.ifBlank { "0" },
                "hostUin" to "0",
                "format" to "json",
                "inCharset" to "utf8",
                "outCharset" to "utf-8",
                "notice" to "0",
                "platform" to "yqq.json",
                "needNewCode" to "0",
                "data" to requestData.toString(),
            ),
            cookie = session.cookie,
        )
        val data = response.optJSONObject("req_0")?.optJSONObject("data")
            ?: return@withContext PlaybackResolution.Unavailable("QQ音乐没有返回播放数据")
        val midUrl = data.optJSONArray("midurlinfo")?.optJSONObject(0)
        val purl = midUrl?.optString("purl").orEmpty()
        if (purl.isBlank()) {
            return@withContext if (!session.isLoggedIn) {
                PlaybackResolution.LoginRequired
            } else {
                PlaybackResolution.Unavailable("当前 QQ音乐账号没有取得可播放链接")
            }
        }
        val sip = data.optJSONArray("sip") ?: JSONArray()
        val domain = (0 until sip.length())
            .mapNotNull { sip.optString(it).takeIf(String::isNotBlank) }
            .firstOrNull { !it.startsWith("http://ws", ignoreCase = true) }
            ?: (0 until sip.length()).mapNotNull { sip.optString(it).takeIf(String::isNotBlank) }.firstOrNull()
            ?: return@withContext PlaybackResolution.Unavailable("QQ音乐没有返回播放域名")
        PlaybackResolution.Playable(
            url = secureUrl(domain + purl),
            requestedQuality = quality,
            actualQuality = fileType.actualTier,
            format = fileType.extension.removePrefix("."),
        )
    }

    private fun postMusicu(
        module: String,
        method: String,
        param: JSONObject,
    ): JSONObject {
        val session = sessionProvider()
        val gtk = hash33(session.musicKey)
        val comm = JSONObject()
            .put("ct", 24)
            .put("cv", 4_747_474)
            .put("platform", "yqq.json")
            .put("chid", "0")
            .put("uin", session.uin.toLongOrNull() ?: 0L)
            .put("g_tk", gtk)
            .put("g_tk_new_20200303", gtk)
            .put("format", "json")
            .put("inCharset", "utf-8")
            .put("outCharset", "utf-8")
            .put("notice", 0)
            .put("need_new_code", 1)
        val payload = JSONObject()
            .put("comm", comm)
            .put(
                "req_0",
                JSONObject()
                    .put("module", module)
                    .put("method", method)
                    .put("param", param),
            )
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", DesktopUserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://y.qq.com/")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("QQ音乐请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐返回了空响应")
            val root = JSONObject(body)
            val req = root.optJSONObject("req_0") ?: throw IOException("QQ音乐返回缺少 req_0")
            val code = req.optInt("code", 0)
            if (code != 0) {
                throw IOException(
                    req.optString("message")
                        .ifBlank { req.optString("msg") }
                        .ifBlank { "QQ音乐接口返回错误码 $code" },
                )
            }
            return req.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun parseRecommendedPlaylists(data: JSONObject, limit: Int): List<MusicPlaylistSummary> {
        val list = data.optJSONArray("List")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("songlists")
            ?: JSONArray()
        return buildList {
            for (index in 0 until minOf(list.length(), limit.coerceAtLeast(1))) {
                val raw = list.optJSONObject(index) ?: continue
                val playlist = raw.optJSONObject("Playlist")?.optJSONObject("basic")
                    ?: raw.optJSONObject("basic")
                    ?: raw
                parsePlaylist(playlist)?.let(::add)
            }
        }
    }

    private fun parsePlaylist(item: JSONObject): MusicPlaylistSummary? {
        val id = firstString(item, "id", "tid", "dissid", "disstid", "playlistId")
            .ifBlank { firstLong(item, "id", "tid", "dissid", "disstid", "playlistId").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val coverObject = item.optJSONObject("cover")
        val artwork = firstString(item, "picurl", "picUrl", "logo", "coverUrl", "bigpicUrl")
            .ifBlank { coverObject?.optString("default_url").orEmpty() }
            .takeIf(String::isNotBlank)
            ?.let(::secureUrl)
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.QQMusic, id),
            title = firstString(item, "title", "dissname", "name", "dirName").ifBlank { "QQ音乐歌单" },
            artworkUrl = artwork,
            creatorName = item.optJSONObject("creator")?.let { firstString(it, "nick", "nickname", "name") }
                ?.takeIf(String::isNotBlank)
                ?: firstString(item, "nick", "nickname", "creatorNick").takeIf(String::isNotBlank),
            description = firstString(item, "desc", "description").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "songnum", "songNum", "song_cnt").takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "listennum", "playCnt", "play_cnt").takeIf { it >= 0 },
        )
    }

    private fun parseRankings(data: JSONObject, limit: Int): List<MusicRankingSummary> {
        val groups = data.optJSONArray("group") ?: JSONArray()
        return buildList {
            outer@ for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val tops = group.optJSONArray("toplist") ?: group.optJSONArray("list") ?: JSONArray()
                for (topIndex in 0 until tops.length()) {
                    if (size >= limit.coerceAtLeast(1)) break@outer
                    val top = tops.optJSONObject(topIndex) ?: continue
                    val id = firstString(top, "topId", "id")
                        .ifBlank { firstLong(top, "topId", "id").takeIf { it > 0 }?.toString().orEmpty() }
                    if (id.isBlank()) continue
                    val previews = top.optJSONArray("song") ?: JSONArray()
                    val previewText = buildList {
                        for (index in 0 until minOf(previews.length(), 3)) {
                            val preview = previews.optJSONObject(index) ?: continue
                            val title = firstString(preview, "title", "name")
                            val singer = firstString(preview, "singerName", "singer_name")
                            if (title.isNotBlank()) add(if (singer.isBlank()) title else "$title · $singer")
                        }
                    }.joinToString("  /  ")
                    add(
                        MusicRankingSummary(
                            id = MusicResourceId(MusicSource.QQMusic, id),
                            title = firstString(top, "title", "titleDetail", "name").ifBlank { "QQ音乐排行榜" },
                            artworkUrl = firstString(top, "frontPicUrl", "headPicUrl", "cover")
                                .takeIf(String::isNotBlank)?.let(::secureUrl),
                            subtitle = previewText.ifBlank { firstString(top, "titleSub", "period", "updateTime") }
                                .takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
        }
    }

    private fun parseSearchTrack(item: JSONObject): MusicTrack? {
        val songMid = item.optString("songmid")
            .ifBlank { item.optJSONObject("mid")?.optString("song") ?: "" }
            .ifBlank { item.optString("mid") }
        if (songMid.isBlank()) return null
        val name = item.optString("songname")
            .ifBlank { item.optString("songname_hilight") }
            .ifBlank { item.optString("name") }
            .ifBlank { item.optString("title") }
            .ifBlank { "未知歌曲" }
        val singers = item.optJSONArray("singer") ?: item.optJSONArray("singers") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until singers.length()) {
                val singer = singers.optJSONObject(index) ?: continue
                val singerName = singer.optString("name").ifBlank { singer.optString("title") }
                if (singerName.isBlank()) continue
                val singerMid = singer.optString("mid").takeIf(String::isNotBlank)
                val names = splitQQMusicSingerName(singerName)
                names.forEach { name ->
                    add(
                        MusicArtistRef(
                            id = singerMid
                                ?.takeIf { names.size == 1 }
                                ?.let { MusicResourceId(MusicSource.QQMusic, it) },
                            name = name,
                        ),
                    )
                }
            }
        }
        val albumObject = item.optJSONObject("album")
        val albumMid = item.optString("albummid")
            .ifBlank { albumObject?.optString("mid").orEmpty() }
        val albumName = item.optString("albumname")
            .ifBlank { albumObject?.optString("name").orEmpty() }
            .ifBlank { albumObject?.optString("title").orEmpty() }
        val artwork = albumMid.takeIf(String::isNotBlank)?.let(::albumArtwork)
        val mediaMid = item.optString("media_mid")
            .ifBlank { item.optJSONObject("file")?.optString("media_mid").orEmpty() }
            .takeIf(String::isNotBlank)
        val numericId = firstLong(item, "songid", "id").takeIf { it > 0L }
        val durationSeconds = firstLong(item, "interval", "duration").takeIf { it > 0L }
        return MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, songMid),
            title = name,
            artists = artists.ifEmpty { listOf(MusicArtistRef(name = "未知歌手")) },
            album = albumName.takeIf(String::isNotBlank)?.let {
                MusicAlbumRef(
                    id = albumMid.takeIf(String::isNotBlank)?.let { mid -> MusicResourceId(MusicSource.QQMusic, mid) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = durationSeconds?.times(1_000L),
            availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.QQMusic(
                songMid = songMid,
                mediaMid = mediaMid,
                numericSongId = numericId,
            ),
        )
    }

    private fun getJson(
        baseUrl: String,
        params: Map<String, String>,
        referer: String? = null,
        cookie: String = "",
    ): JSONObject {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                referer?.let { header("Referer", it) }
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("QQ音乐请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐返回了空响应")
            return JSONObject(stripJsonp(body))
        }
    }

    private fun stripJsonp(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith('{')) return trimmed
        val first = trimmed.indexOf('{')
        val last = trimmed.lastIndexOf('}')
        if (first >= 0 && last > first) return trimmed.substring(first, last + 1)
        throw IOException("QQ音乐返回了无法解析的数据")
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
        if (value.isBlank()) "" else String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrDefault("")

    private fun albumArtwork(albumMid: String): String =
        "https://y.qq.com/music/photo_new/T002R300x300M000${albumMid}.jpg?max_age=2592000"

    private fun secureUrl(value: String): String =
        if (value.startsWith("http://", ignoreCase = true)) "https://${value.substringAfter("://")}" else value

    private fun hash33(value: String, seed: Long = 5381L): Long {
        var hash = seed
        value.forEach { char ->
            hash += (hash shl 5) + char.code
            hash = hash and 0xFFFF_FFFFL
        }
        return hash and 0x7FFF_FFFFL
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val DesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

internal fun splitQQMusicSingerName(value: String): List<String> = value
    .split(Regex("\\s*(?:;|；)\\s*"))
    .map(String::trim)
    .filter(String::isNotBlank)

private data class QQFileType(
    val prefix: String,
    val extension: String,
    val actualTier: AudioQualityTier,
)

private fun AudioQualityTier.qqFileType(): QQFileType = when (this) {
    AudioQualityTier.Standard -> QQFileType("M500", ".mp3", AudioQualityTier.Standard)
    AudioQualityTier.High -> QQFileType("M800", ".mp3", AudioQualityTier.High)
    AudioQualityTier.Lossless -> QQFileType("F000", ".flac", AudioQualityTier.Lossless)
    AudioQualityTier.HiResolution,
    AudioQualityTier.Immersive,
    AudioQualityTier.Master -> QQFileType("F000", ".flac", AudioQualityTier.Lossless)
}

private fun MusicTrack.requireQQMetadata(): ProviderTrackMetadata.QQMusic {
    require(id.source == MusicSource.QQMusic) {
        "QQMusicApiClient cannot handle ${id.source.storageValue} track"
    }
    return (providerMetadata as? ProviderTrackMetadata.QQMusic)
        ?: ProviderTrackMetadata.QQMusic(songMid = id.value)
}
