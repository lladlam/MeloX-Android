package com.lladlam.melox.core.provider.qishui

import com.lladlam.melox.core.music.model.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Native protocol adapter for guowenye/qishui-api; no external Node service is required. */
internal class QishuiApiClient(
    private val sessionProvider: () -> QishuiSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val http = httpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    private val cursors = ConcurrentHashMap<String, String>()
    private var qrCookies = ""
    private var qrToken = ""
    private val passport = mapOf(
        "passport_jssdk_version" to "2.4.13", "passport_jssdk_type" to "normal",
        "is_from_ttaccountsdk" to "1", "aid" to "386088",
    )

    private suspend fun request(
        path: String, params: Map<String, String> = emptyMap(), body: RequestBody? = null,
        app: Boolean = false, cookie: String? = null,
    ): Pair<JSONObject, String> = withContext(Dispatchers.IO) {
        val url = (if (app) "https://beta-luna.douyin.com" else "https://api.qishui.com").toHttpUrl().newBuilder()
            .encodedPath(path).apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val session = sessionProvider()
        val builder = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", if (path.startsWith("/passport") || path.contains("/h5/")) WebAgent else if (app) "Luna/19.1.0 Android" else "LunaPC/3.0.0(290101097)")
        val credentials = cookie
            ?: session.cookie.takeIf(String::isNotBlank)
            ?: session.sessionId.takeIf(String::isNotBlank)?.let { "sessionid=$it" }
            ?: ""
        if (credentials.isNotBlank()) builder.header("Cookie", credentials)
        if (body != null) builder.post(body)
        http.newCall(builder.build()).execute().use { response ->
            // Douyin passport may answer a confirmed QR with a redirect whose
            // Set-Cookie carries the session. Treat 3xx as a result, not an error.
            val passportRedirect = path.startsWith("/passport") && response.code in 300..399
            if (!response.isSuccessful && !passportRedirect) {
                throw IOException("汽水音乐请求失败：HTTP ${response.code}（$path）")
            }
            val text = response.body.string()
            if (text.isBlank() && !passportRedirect) throw IOException(if (path.contains("/pc/search/"))
                "汽水音乐搜索未返回数据，请先登录；此接口可能需要上游 PC 签名" else "汽水音乐返回了空响应，请稍后重试")
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }
                .getOrElse { throw IOException("汽水音乐返回了无法解析的数据") }
            val info = json.optJSONObject("status_info")
            val code = json.optInt("status_code", json.optInt("error_code", info?.optInt("status_code", 0) ?: 0))
            if (code != 0) throw IOException("汽水音乐请求未获通过（$code），请检查登录状态后重试")
            val error = QishuiParsing.text(info, "status_msg")
            if (error.startsWith("ERR_")) throw IOException("汽水音乐暂未允许本次请求，请登录后重试")
            // Keep every Set-Cookie value verbatim. OkHttp's domain-aware parser
            // drops cookies issued for a parent domain, which broke the passport
            // handshake and left the QR poll without its CSRF/session cookies.
            val cookies = response.headers.values("Set-Cookie")
                .mapNotNull { header -> header.substringBefore(';').trim().takeIf { it.contains('=') } }
                .joinToString("; ")
            json to cookies
        }
    }

    private suspend fun post(path: String, body: JSONObject) = request(path, body = body.toString().toRequestBody(Json), app = true).first

    suspend fun search(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
        val size = pageSize.coerceIn(1, 50)
        if (query.isBlank()) return MusicPage(emptyList(), page, size, 0, false)
        val session = sessionProvider()
        val key = "search:$query:"
        val cursor = if (page <= 1) "0" else cursors["$key$page"] ?: return MusicPage(emptyList(), page, size, hasMore = false)
        val params = mapOf(
            "aid" to "386088", "app_name" to "luna_pc", "region" to "cn", "geo_region" to "cn", "os_region" to "cn",
            "device_id" to session.deviceId, "iid" to session.installId, "fp" to session.deviceId,
            "version_name" to "3.0.0", "version_code" to "30000000", "channel" to "official", "build_mode" to "master",
            "ac" to "wifi", "tz_name" to "Asia/Shanghai", "device_platform" to "windows", "device_type" to "Windows",
            "os_version" to "Windows 11", "q" to query.trim(), "cursor" to cursor, "search_method" to "input",
        )
        val body = request("/luna/pc/search/track", params).first
        val tracks = QishuiParsing.searchResources(body).mapNotNull(QishuiParsing::track).distinctBy { it.id }
        val next = QishuiParsing.text(body, "next_cursor").ifBlank { QishuiParsing.text(body, "cursor") }
        val more = body.optBoolean("has_more", false) && next.isNotBlank() && next != cursor && tracks.isNotEmpty()
        if (more) cursors["$key${page + 1}"] = next
        // Search batch size is determined by upstream, so never discard returned items.
        return MusicPage(tracks, page, tracks.size.coerceAtLeast(size), hasMore = more)
    }

    suspend fun home(limit: Int): MusicHomeFeed {
        val body = post("/luna/discover/mix", JSONObject().put("count", limit.coerceIn(1, 50)))
        val playlists = QishuiParsing.objects(body.optJSONArray("inner_block")).flatMap { QishuiParsing.objects(it.optJSONArray("resources")) }
            .mapNotNull(QishuiParsing::playlist).distinctBy { it.id }.take(limit)
        return MusicHomeFeed(recommendedPlaylists = playlists)
    }

    suspend fun playlist(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
        require(playlist.id.source == MusicSource.Qishui)
        val size = pageSize.coerceIn(1, 100)
        val key = "playlist:${playlist.id.value}:$size:"
        val cursor = if (page <= 1) "" else cursors["$key$page"] ?: return MusicPlaylistDetail(playlist, emptyList())
        val body = post("/luna/playlist/detail", JSONObject().put("playlist_id", playlist.id.value).put("count", size)
            .apply { if (cursor.isNotEmpty()) put("cursor", cursor) })
        val tracks = QishuiParsing.objects(body.optJSONArray("media_resources")).mapNotNull(QishuiParsing::track).distinctBy { it.id }
        val next = QishuiParsing.text(body, "next_cursor")
        val more = next.isNotBlank() && next != cursor && body.optBoolean("has_more", true) && tracks.isNotEmpty()
        if (more) cursors["$key${page + 1}"] = next
        val summary = body.optJSONObject("playlist")?.let(QishuiParsing::playlist) ?: playlist
        val total = summary.trackCount?.toLong() ?: ((page - 1L) * size + tracks.size + if (more) 1 else 0)
        return MusicPlaylistDetail(summary, tracks, total, more)
    }

    suspend fun seo(id: MusicResourceId): JSONObject {
        require(id.source == MusicSource.Qishui)
        return request("/luna/h5/seo_track", mapOf("track_id" to id.value, "device_platform" to "web"), app = true).first
    }

    suspend fun playbackBody(track: MusicTrack, quality: AudioQualityTier): JSONObject {
        require(track.id.source == MusicSource.Qishui)
        if (sessionProvider().isLoggedIn) {
            try {
                val body = post("/luna/media-player", JSONObject().put("media_id", track.id.value)
                    .put("media_type", "track").put("queue_type", "").put("enable_refresh_api", false).put("enable_dash", false))
                val infos = QishuiParsing.objects(body.optJSONArray("player_infos"))
                val info = infos.firstOrNull { QishuiParsing.text(it, "media_id") == track.id.value }
                    ?: infos.singleOrNull()
                if (info != null) {
                    val normalized = JSONObject().put("track_player", info)
                    if (QishuiParsing.audio(normalized, track.durationMs, quality) != null) return normalized
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // The H5 endpoint may still provide an explicitly identified preview.
            }
        }
        return seo(track.id)
    }

    suspend fun profile(): MusicAccountSummary? {
        val session = sessionProvider()
        if (!session.isLoggedIn) return null
        val response = request("/luna/pc/me", mapOf("aid" to "386088")).first
        val user = response.optJSONObject("my_info") ?: response.optJSONObject("user") ?: response
        val id = QishuiParsing.text(user, "id").ifBlank { session.userId }
        if (id.isBlank()) throw IOException("汽水音乐账号状态已失效，请重新登录")
        return MusicAccountSummary(MusicSource.Qishui, id, QishuiParsing.text(user, "nickname").ifBlank { "汽水音乐用户" },
            subtitle = if (user.optBoolean("is_vip")) "汽水音乐 VIP" else "汽水音乐")
    }

    suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> {
        if (!sessionProvider().isLoggedIn) throw IOException("请先登录汽水音乐")
        val size = pageSize.coerceIn(1, 100)
        val response = request("/luna/pc/me/playlist", mapOf("aid" to "386088", "iid" to "27960026095955", "version_code" to "30020100")).first
        val all = QishuiParsing.objects(response.optJSONArray("playlists")).mapNotNull(QishuiParsing::playlist).distinctBy { it.id }
        return MusicPage(all.drop(((page - 1L) * size).coerceIn(0, all.size.toLong()).toInt()).take(size), page, size, all.size.toLong())
    }

    data class Qr(val token: String, val image: String, val link: String)
    data class QrState(val status: String, val sessionId: String, val cookie: String = "")

    suspend fun createQr(): Qr {
        qrCookies = ""; qrToken = ""
        val (response, cookies) = request("/passport/web/get_qrcode/", passport + mapOf(
            "next" to "https://api.qishui.com", "need_logo" to "false", "need_short_url" to "false", "is_new_login" to "1",
        ), cookie = "")
        val data = response.optJSONObject("data") ?: response
        val token = QishuiParsing.text(data, "token")
        if (token.isBlank()) throw IOException("汽水音乐未返回登录二维码")
        qrToken = token; qrCookies = cookies
        val indexUrl = QishuiParsing.text(data, "qrcode_index_url")
        // Upstream's `qrcode`/`qrcode_index_url` still points at the retired
        // `ucenter_web/app/sdk-next` page, which now returns 404 in Douyin.
        // Desktop clients build the QR locally from the passport token and the
        // live `light/invoke/scan_login` page instead.
        val scanUrl = scanLoginUrl(indexUrl, android.os.Build.MODEL.orEmpty())
        return if (scanUrl != null) {
            Qr(token, image = "", link = scanUrl)
        } else {
            Qr(token, QishuiParsing.text(data, "qrcode"), indexUrl)
        }
    }

    suspend fun pollQr(token: String): QrState {
        require(token == qrToken && token.isNotBlank())
        val form = FormBody.Builder().add("need_logo", "false").add("need_short_url", "false").add("is_frontier", "true")
            .add("token", token).add("is_new_login", "1").add("next", "https://api.qishui.com").build()
        val (response, cookies) = request("/passport/web/check_qrconnect/", passport + ("iid" to "27960026095955"), form, cookie = qrCookies)
        val data = response.optJSONObject("data") ?: response
        val sessionId = cookies.split(';')
            .map(String::trim)
            .firstOrNull { it.startsWith("sessionid=") }
            ?.removePrefix("sessionid=")
            .orEmpty()
        return QrState(QishuiParsing.text(data, "status"), sessionId, cookies)
    }

    companion object {
        const val WebAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
        private val Json = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * The live desktop scan-login page. Upstream's `qrcode_index_url` still embeds
 * the retired `ucenter_web/app/sdk-next` path (HTTP 404), so build the QR from
 * the passport token and this endpoint instead. Null when no token is present,
 * in which case callers fall back to the upstream image.
 */
internal fun scanLoginUrl(indexUrl: String, computerName: String): String? {
    val token = runCatching {
        java.net.URI(indexUrl).rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("token=") }
            ?.substringAfter('=')
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
    fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return "https://bff-pc.qishui.com/light/invoke/scan_login" +
        "?token=${encode(token)}&os=Windows&computer_name=${encode(computerName.ifBlank { "MeloX" })}"
}
