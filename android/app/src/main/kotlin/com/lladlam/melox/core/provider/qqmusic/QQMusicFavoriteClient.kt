package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal const val QQ_LIKED_DIRECTORY_ID = 201
internal fun qqFavoriteWriteMethod(favorite: Boolean): String =
    if (favorite) "AddSonglist" else "DelSonglist"

internal fun qqMusicLoginType(musicKey: String): Int =
    if (musicKey.startsWith("W_X")) 1 else 2

/** Authenticated QQ Music "我喜欢" writer. */
class QQMusicFavoriteClient(
    private val sessionProvider: () -> QQMusicSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    internal data class SongWriteRef(
        val songId: Long,
        val songType: Int,
    )

    suspend fun setFavorite(track: MusicTrack, favorite: Boolean) = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.QQMusic) { "QQMusicFavoriteClient only accepts QQ Music tracks" }
        val session = sessionProvider()
        if (!session.isLoggedIn) throw IOException("请先登录 QQ音乐账号")

        val songMid = track.id.value
        val song = resolveWriteRef(songMid, session)
        val primary = runCatching {
            val data = postMusicu(
                session = session,
                module = "music.musicasset.PlaylistDetailWrite",
                method = qqFavoriteWriteMethod(favorite),
                param = buildWriteParam(song),
            )
            val retCode = findInt(data, "retCode", "retcode", "code") ?: 0
            if (retCode != 0) throw IOException("QQ音乐账号写入返回 $retCode")
        }
        if (primary.isSuccess) return@withContext

        // Some QQ accounts reject PlaylistDetailWrite unless the Android client
        // has a complete device-session/QIMEI context. The authenticated Web
        // playlist endpoints perform the same user-owned playlist operation and
        // only consume the QQ cookies already stored locally by MeloX.
        runCatching { legacySetFavorite(session, songMid, song, favorite) }
            .getOrElse { fallbackError ->
                val primaryMessage = primary.exceptionOrNull()?.message.orEmpty()
                throw IOException(
                    "QQ音乐${if (favorite) "收藏" else "取消收藏"}失败" +
                        primaryMessage.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty(),
                    fallbackError,
                )
            }
    }

    private fun resolveWriteRef(songMid: String, session: QQMusicSession): SongWriteRef {
        val appResult = runCatching {
            val data = postMusicu(
                session = session,
                module = "music.trackInfo.UniformRuleCtrl",
                method = "CgiGetTrackInfo",
                param = JSONObject()
                    .put("ctx", 0)
                    .put("client", 1)
                    .put("types", JSONArray().put(0))
                    .put("modify_stamp", JSONArray().put(0))
                    .put("mids", JSONArray().put(songMid)),
            )
            parseWriteRef(data, songMid)
        }.getOrNull()
        if (appResult != null) return appResult

        return resolveWebSongRef(songMid, session)
            ?: throw IOException("QQ音乐无法解析歌曲收藏标识")
    }

    private fun resolveWebSongRef(songMid: String, session: QQMusicSession): SongWriteRef? {
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg".toHttpUrl()
            .newBuilder()
            .addQueryParameter("songmid", songMid)
            .addQueryParameter("tpl", "yqq_song_detail")
            .addQueryParameter("format", "json")
            .addQueryParameter("g_tk", hash33(session.musicKey).toString())
            .addQueryParameter("loginUin", session.uin)
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("outCharset", "utf8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq")
            .addQueryParameter("needNewCode", "0")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DesktopUserAgent)
            .header("Referer", "https://y.qq.com/")
            .header("Cookie", session.cookie)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = runCatching { JSONObject(response.body.string()) }.getOrNull() ?: return null
            val item = root.optJSONArray("data")?.optJSONObject(0) ?: return null
            val id = firstLong(item, "id", "songid", "songId")
            if (id <= 0L) return null
            val type = firstInt(item, "type", "songtype", "songType") ?: 0
            return SongWriteRef(id, type)
        }
    }

    internal fun buildWriteParam(song: SongWriteRef): JSONObject = JSONObject()
        .put("dirId", QQ_LIKED_DIRECTORY_ID)
        .put("tid", 0)
        .put("bFmtUtf8", true)
        .put(
            "v_songInfo",
            JSONArray().put(
                JSONObject()
                    .put("songId", song.songId)
                    .put("songType", song.songType),
            ),
        )

    internal fun parseWriteRef(data: JSONObject, expectedMid: String): SongWriteRef? {
        val tracks = findArray(data, "tracks", "track_list", "songlist") ?: return null
        for (index in 0 until tracks.length()) {
            val item = tracks.optJSONObject(index) ?: continue
            val mid = firstString(item, "mid", "songmid", "songMid")
            if (mid.isNotBlank() && mid != expectedMid) continue
            val id = firstLong(item, "id", "songid", "songId")
            if (id <= 0L) continue
            val type = firstInt(item, "type", "songtype", "songType") ?: 0
            return SongWriteRef(id, type)
        }
        return null
    }

    private fun postMusicu(
        session: QQMusicSession,
        module: String,
        method: String,
        param: JSONObject,
    ): JSONObject {
        val payload = JSONObject()
            .put(
                "comm",
                JSONObject()
                    .put("ct", AndroidClientType)
                    .put("cv", AndroidClientVersion)
                    .put("v", AndroidClientVersion)
                    .put("chid", "10003505")
                    .put("qq", session.uin)
                    .put("authst", session.musicKey)
                    .put("tmeAppID", "qqmusic")
                    .put("tmeLoginType", qqMusicLoginType(session.musicKey)),
            )
            .put(
                "req_0",
                JSONObject()
                    .put("module", module)
                    .put("method", method)
                    .put("param", param),
            )

        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", "QQMusic $AndroidClientVersion(android 15)")
            .header(com.lladlam.melox.core.network.RetryInterceptor.NO_RETRY_HEADER, "true")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("QQ音乐账号写入请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐账号写入返回空响应")
            val root = runCatching { JSONObject(body) }
                .getOrElse { throw IOException("QQ音乐账号写入返回无法解析的数据", it) }
            val req = root.optJSONObject("req_0")
                ?: throw IOException("QQ音乐账号写入响应缺少 req_0")
            val code = req.optInt("code", 0)
            if (code != 0) {
                throw IOException(
                    req.optString("message")
                        .ifBlank { req.optString("msg") }
                        .ifBlank { "QQ音乐账号写入错误码 $code" },
                )
            }
            return req.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun legacySetFavorite(
        session: QQMusicSession,
        songMid: String,
        song: SongWriteRef,
        favorite: Boolean,
    ) {
        val gtk = hash33(session.musicKey).toString()
        val builder = if (favorite) {
            "https://c.y.qq.com/splcloud/fcgi-bin/fcg_music_add2songdir.fcg".toHttpUrl()
                .newBuilder()
                .addQueryParameter("g_tk", gtk)
                .addQueryParameter("midlist", songMid)
                .addQueryParameter("typelist", "13")
                .addQueryParameter("dirid", QQ_LIKED_DIRECTORY_ID.toString())
                .addQueryParameter("addtype", "")
                .addQueryParameter("sender", "4")
                .addQueryParameter("formsender", "4")
                .addQueryParameter("r2", "0")
                .addQueryParameter("r3", "1")
                .addQueryParameter("utf8", "1")
        } else {
            "https://c.y.qq.com/qzone/fcg-bin/fcg_music_delbatchsong.fcg".toHttpUrl()
                .newBuilder()
                .addQueryParameter("g_tk", gtk)
                .addQueryParameter("loginUin", session.uin)
                .addQueryParameter("hostUin", "0")
                .addQueryParameter("format", "json")
                .addQueryParameter("inCharset", "utf8")
                .addQueryParameter("outCharset", "utf-8")
                .addQueryParameter("notice", "0")
                .addQueryParameter("platform", "yqq.post")
                .addQueryParameter("needNewCode", "0")
                .addQueryParameter("uin", session.uin)
                .addQueryParameter("dirid", QQ_LIKED_DIRECTORY_ID.toString())
                .addQueryParameter("ids", song.songId.toString())
                .addQueryParameter("source", "103")
                .addQueryParameter("types", "3")
                .addQueryParameter("formsender", "4")
                .addQueryParameter("flag", "2")
                .addQueryParameter("utf8", "1")
                .addQueryParameter("from", "3")
        }
        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", DesktopUserAgent)
            .header("Referer", "https://y.qq.com/n/yqq/playlist")
            .header("Cookie", session.cookie)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("QQ音乐 Web 收藏请求失败：HTTP ${response.code}")
            val body = response.body.string().trim()
            if (body.isBlank()) throw IOException("QQ音乐 Web 收藏返回空响应")
            val json = runCatching { JSONObject(stripJsonp(body)) }
                .getOrElse { throw IOException("QQ音乐 Web 收藏响应无法解析", it) }
            val code = firstInt(json, "code") ?: -1
            if (code != 0) throw IOException(json.optString("msg").ifBlank { "QQ音乐 Web 收藏错误码 $code" })
        }
    }

    private fun stripJsonp(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith('{')) return trimmed
        val first = trimmed.indexOf('{')
        val last = trimmed.lastIndexOf('}')
        if (first >= 0 && last > first) return trimmed.substring(first, last + 1)
        throw IOException("QQ音乐返回无法解析的数据")
    }

    private fun hash33(value: String): Int {
        var hash = 5381L
        value.forEach { char -> hash += (hash shl 5) + char.code }
        return (hash and 0x7fffffffL).toInt()
    }

    private fun findArray(value: Any?, vararg keys: String): JSONArray? = when (value) {
        is JSONObject -> {
            keys.asSequence().mapNotNull(value::optJSONArray).firstOrNull()
                ?: value.keys().asSequence().mapNotNull { key -> findArray(value.opt(key), *keys) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { index -> findArray(value.opt(index), *keys) }.firstOrNull()
        else -> null
    }

    private fun findInt(value: Any?, vararg keys: String): Int? {
        return when (value) {
            is JSONObject -> {
                for (key in keys) {
                    when (val raw = value.opt(key)) {
                        is Number -> return raw.toInt()
                        is String -> raw.toIntOrNull()?.let { return it }
                    }
                }
                value.keys().asSequence().mapNotNull { key -> findInt(value.opt(key), *keys) }.firstOrNull()
            }
            is JSONArray -> (0 until value.length()).asSequence().mapNotNull { index -> findInt(value.opt(index), *keys) }.firstOrNull()
            else -> null
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

    private fun firstInt(value: JSONObject, vararg keys: String): Int? =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            }
        }.firstOrNull()

    private companion object {
        const val AndroidClientType = 11
        const val AndroidClientVersion = 14_090_008
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val DesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
