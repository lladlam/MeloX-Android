package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MeloXMusicComment(
    val id: Long,
    val user: String,
    val avatarUrl: String?,
    val content: String,
    val likedCount: Long,
    val timeText: String,
)

data class MeloXWikiSection(val title: String, val lines: List<String>)

/** Authenticated write/comment/wiki routes mirrored from upstream MeloX NeteaseAPI. */
class NeteaseMusicOperationsClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val syntheticDeviceId = randomHex(26).uppercase()

    suspend fun setSongLiked(songId: Long, liked: Boolean) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        eapi(
            "/api/radio/like",
            JSONObject().put("alg", "itembased").put("trackId", songId).put("like", liked).put("time", "3"),
            true,
        )
        Unit
    }

    suspend fun addSongToPlaylist(songId: Long, playlistId: Long) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val path = "/api/v1/playlist/manipulate/tracks"
        val id = songId.toString()
        val first = runCatching {
            eapi(path, JSONObject().put("op", "add").put("pid", playlistId)
                .put("trackIds", "[\"$id\"]").put("imme", "true"), true)
        }
        if (first.isFailure) {
            eapi(path, JSONObject().put("op", "add").put("pid", playlistId)
                .put("trackIds", "[\"$id\",\"$id\"]").put("imme", "true"), true)
        }
        Unit
    }

    suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val path = if (subscribed) "/api/playlist/subscribe" else "/api/playlist/unsubscribe"
        val data = JSONObject().put("id", playlistId)
        if (subscribed) data.put("checkToken", NETEASE_CHECK_TOKEN)
        eapi(path, data, true)
        Unit
    }

    suspend fun songComments(songId: Long, limit: Int = 40): List<MeloXMusicComment> = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/v1/resource/comments/R_SO_4_$songId",
            JSONObject().put("rid", songId).put("limit", limit).put("offset", 0).put("beforeTime", 0),
            NeteaseSessionStore.containsMusicU(cookieProvider()),
        )
        val hot = result.optJSONArray("hotComments") ?: JSONArray()
        val normal = result.optJSONArray("comments") ?: JSONArray()
        val seen = mutableSetOf<Long>()
        buildList {
            fun addArray(values: JSONArray) {
                for (i in 0 until values.length()) {
                    val c = values.optJSONObject(i) ?: continue
                    val id = c.optLong("commentId", -1L)
                    if (id <= 0L || !seen.add(id)) continue
                    val user = c.optJSONObject("user")
                    add(MeloXMusicComment(
                        id = id,
                        user = user?.optString("nickname").orEmpty().ifBlank { "网易云用户" },
                        avatarUrl = secure(user?.optString("avatarUrl")?.takeIf(String::isNotBlank)),
                        content = c.optString("content").ifBlank { "…" },
                        likedCount = c.optLong("likedCount", 0L),
                        timeText = c.optString("timeStr").ifBlank { "" },
                    ))
                }
            }
            addArray(hot); addArray(normal)
        }
    }

    suspend fun songWiki(songId: Long): List<MeloXWikiSection> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val result = eapi(
            "/api/song/play/about/block/page",
            JSONObject().put("songId", songId),
            true,
        )
        val blocks = result.optJSONObject("data")?.optJSONArray("blocks") ?: JSONArray()
        buildList {
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                val title = block.optJSONObject("uiElement")?.optJSONObject("mainTitle")?.optString("title")
                    ?.takeIf(String::isNotBlank) ?: when (block.optString("code")) {
                        "SONG_PLAY_ABOUT_MUSIC_MEMORY" -> "音乐记忆"
                        "SONG_PLAY_ABOUT_SIMILAR_SONG" -> "相似歌曲"
                        "SONG_PLAY_ABOUT_RELATED_PLAYLIST" -> "相关歌单"
                        else -> "歌曲百科"
                    }
                val lines = mutableListOf<String>()
                val creatives = block.optJSONArray("creatives") ?: JSONArray()
                for (j in 0 until creatives.length()) {
                    val creative = creatives.optJSONObject(j) ?: continue
                    val ui = creative.optJSONObject("uiElement")
                    ui?.optJSONObject("mainTitle")?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                    val resources = creative.optJSONArray("resources") ?: JSONArray()
                    for (k in 0 until resources.length()) {
                        val rui = resources.optJSONObject(k)?.optJSONObject("uiElement") ?: continue
                        rui.optJSONObject("mainTitle")?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                        val descriptions = rui.optJSONArray("descriptions") ?: JSONArray()
                        for (x in 0 until descriptions.length()) {
                            descriptions.optJSONObject(x)?.optString("description")?.takeIf(String::isNotBlank)?.let(lines::add)
                        }
                        val subtitles = rui.optJSONArray("subTitles") ?: JSONArray()
                        for (x in 0 until subtitles.length()) {
                            subtitles.optJSONObject(x)?.optString("title")?.takeIf(String::isNotBlank)?.let(lines::add)
                        }
                    }
                }
                val directResources = block.optJSONArray("resources") ?: JSONArray()
                for (j in 0 until directResources.length()) {
                    val r = directResources.optJSONObject(j) ?: continue
                    r.optString("resourceType").takeIf(String::isNotBlank)?.let { type ->
                        val ext = r.optJSONObject("extensionInfo")
                        if (type.equals("FIRST_LISTEN", true)) {
                            ext?.optJSONObject("musicFirstListen")?.optString("date")?.takeIf(String::isNotBlank)?.let { lines.add("第一次听：$it") }
                        }
                        if (type.equals("TOTAL_PLAY", true)) {
                            ext?.optJSONObject("musicTotalPlay")?.let { total ->
                                val count = total.optLong("playCount", 0L)
                                if (count > 0) lines.add("累计播放：$count 次")
                            }
                        }
                    }
                }
                val unique = lines.map(String::trim).filter(String::isNotBlank).distinct()
                if (unique.isNotEmpty()) add(MeloXWikiSection(title, unique))
            }
        }
    }

    private companion object {
        const val NETEASE_CHECK_TOKEN = "9ca17ae2e6ffcda170e2e6ee8af14fbabdb988f225b3868eb2c15a879b9a83d274a790ac8ff54a97b889d5d42af0feaec3b92af58cff99c470a7eafd88f75e839a9ea7c14e909da883e83fb692a3abdb6b92adee9e"
    }

    private fun ensureLoggedIn() {
        if (!NeteaseSessionStore.containsMusicU(cookieProvider())) throw IOException("请先登录网易云音乐")
    }

    private fun eapi(uri: String, data: JSONObject, authenticated: Boolean): JSONObject {
        val now=System.currentTimeMillis(); val cookie=cookieProvider(); val cookies=NeteaseSessionStore.parseCookie(cookie)
        val header=if(authenticated) authenticatedHeader(cookies,now) else JSONObject().put("os","ios").put("appver","9.0.90").put("osver","18.0").put("requestId","${now}_0000")
        val payload=JSONObject(data.toString()).put("header",header).put("e_r",false); val json=payload.toString()
        val digest=md5Hex("nobody${uri}use${json}md5forencrypt"); val encrypted="$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params=aes(encrypted.toByteArray(),"e82ckenh8dichen8".toByteArray()).toHex()
        val b=Request.Builder().url("https://interface.music.163.com${uri.replace("/api/","/eapi/")}")
            .header("Accept","*/*").header("User-Agent",if(authenticated) "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)" else "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
        if(authenticated)b.header("Cookie",encodedCookie(header))
        val req=b.post(FormBody.Builder().add("params",params).build()).build()
        httpClient.newCall(req).execute().use { response ->
            val body=response.body.string(); if(!response.isSuccessful)throw IOException("网易云请求失败：HTTP ${response.code}")
            if(body.isBlank())throw IOException("网易云返回了空响应"); val result=JSONObject(body); val code=result.optInt("code",response.code)
            if(code !in 200..299)throw IOException(result.optString("message").ifBlank{result.optString("msg")}.ifBlank{"请求失败（$code）"}); return result
        }
    }
    private fun authenticatedHeader(c:Map<String,String>,now:Long)=JSONObject().put("osver",c["osver"]?:"16.2").put("deviceId",c["deviceId"]?:syntheticDeviceId).put("os",c["os"]?:"iPhone OS").put("appver",c["appver"]?:"9.0.90").put("versioncode",c["versioncode"]?:"140").put("buildver",c["buildver"]?:(now/1000L).toString()).put("resolution",c["resolution"]?:"1170x2532").put("__csrf",c["__csrf"]?:"").put("channel",c["channel"]?:"distribution").put("requestId","${now}_${randomDigits(4)}").apply{c["MUSIC_U"]?.takeIf(String::isNotBlank)?.let{put("MUSIC_U",it)}}
    private fun encodedCookie(v:JSONObject)=buildList{val it=v.keys();while(it.hasNext())add(it.next())}.sorted().joinToString("; "){k->"${enc(k)}=${enc(v.optString(k))}"}
    private fun enc(v:String)=URLEncoder.encode(v,Charsets.UTF_8.name()).replace("+","%20")
    private fun secure(v:String?)=v?.let{if(it.startsWith("http://",true))"https://${it.substringAfter("://")}" else it}
    private fun randomHex(n:Int):String{val b=ByteArray(n);SecureRandom().nextBytes(b);return b.joinToString(""){"%02x".format(it)}}
    private fun randomDigits(n:Int)=buildString(n){repeat(n){append(('0'.code+SecureRandom().nextInt(10)).toChar())}}
    private fun md5Hex(v:String)=MessageDigest.getInstance("MD5").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun aes(d:ByteArray,k:ByteArray)=Cipher.getInstance("AES/ECB/PKCS5Padding").run{init(Cipher.ENCRYPT_MODE,SecretKeySpec(k,"AES"));doFinal(d)}
    private fun ByteArray.toHex()=joinToString(""){"%02X".format(it)}
}
