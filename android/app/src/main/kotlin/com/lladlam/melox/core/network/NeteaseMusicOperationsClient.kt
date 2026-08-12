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

data class MeloXListenTogetherUser(val id: String, val name: String, val avatarUrl: String?)
data class MeloXListenTogetherRoom(
    val id: String,
    val creatorId: String,
    val users: List<MeloXListenTogetherUser>,
)
data class MeloXListenTogetherPlayback(
    val songIds: List<Long>,
    val targetSongId: Long?,
    val progressMs: Long,
    val isPlaying: Boolean,
)
data class MeloXMessageContact(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val signature: String,
)
data class MeloXPrivateMessage(
    val id: Long,
    val fromUserId: Long,
    val toUserId: Long,
    val text: String,
    val timeMs: Long,
)

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

    suspend fun songComments(songId: Long, limit: Int = 100): List<MeloXMusicComment> = withContext(Dispatchers.IO) {
        val result = eapi(
            "/api/v1/resource/comments/R_SO_4_$songId",
            JSONObject().put("rid", songId).put("limit", limit.coerceIn(1, 100)).put("offset", 0).put("beforeTime", 0),
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

    suspend fun createListenTogetherRoom(): MeloXListenTogetherRoom = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/listen/together/room/create",
            JSONObject().put("refer", "songplay_more"),
            true,
        )
        parseListenTogetherRoom(response.optJSONObject("data")?.optJSONObject("roomInfo"))
            ?: throw IOException("网易云没有返回有效的一起听房间")
    }

    suspend fun listenTogetherRoomStatus(): MeloXListenTogetherRoom? = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi("/api/listen/together/status/get", JSONObject(), true)
        parseListenTogetherRoom(response.optJSONObject("data")?.optJSONObject("roomInfo"))
    }

    suspend fun joinListenTogetherRoom(roomId: String, inviterId: String): MeloXListenTogetherRoom = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val check = eapi(
            "/api/listen/together/room/check",
            JSONObject().put("roomId", roomId),
            true,
        ).optJSONObject("data")
        if (check?.optBoolean("canJoin", check.optBoolean("joinable", true)) == false) {
            throw IOException(check.optString("message").ifBlank { "该一起听房间当前无法加入" })
        }
        val response = eapi(
            "/api/listen/together/play/invitation/accept",
            JSONObject().put("refer", "inbox_invite").put("roomId", roomId).put("inviterId", inviterId),
            true,
        )
        parseListenTogetherRoom(response.optJSONObject("data")?.optJSONObject("roomInfo"))
            ?: listenTogetherRoomStatus()
            ?: throw IOException("加入成功，但未能读取房间信息")
    }

    suspend fun listenTogetherPlayback(roomId: String): MeloXListenTogetherPlayback = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val data = eapi(
            "/api/listen/together/sync/playlist/get",
            JSONObject().put("roomId", roomId),
            true,
        ).optJSONObject("data") ?: throw IOException("房间暂时没有播放数据")
        val playlist = data.optJSONObject("playlist")
        val display = playlist?.optJSONObject("displayList")?.optJSONArray("result")
            ?: playlist?.optJSONArray("displayList") ?: JSONArray()
        val ids = buildList {
            for (index in 0 until display.length()) {
                display.optString(index).toLongOrNull()?.takeIf { it > 0L }?.let(::add)
            }
        }
        val command = data.optJSONObject("playCommand") ?: JSONObject()
        MeloXListenTogetherPlayback(
            songIds = ids,
            targetSongId = sequenceOf("targetSongId", "songId").map { command.optString(it).toLongOrNull() }.firstOrNull { it != null && it > 0L },
            progressMs = command.optLong("progress", 0L).coerceAtLeast(0L),
            isPlaying = command.optString("playStatus", "PLAY").equals("PLAY", true),
        )
    }

    suspend fun reportListenTogetherPlaylist(roomId: String, userId: Long, songIds: List<Long>, version: Int) =
        withContext(Dispatchers.IO) {
            ensureLoggedIn()
            val ids = songIds.filter { it > 0L }.distinct()
            val versionJson = JSONArray().put(JSONObject().put("userId", userId).put("version", version))
            val playlist = JSONObject()
                .put("commandType", "REPLACE")
                .put("version", versionJson)
                .put("anchorSongId", "")
                .put("anchorPosition", -1)
                .put("randomList", JSONArray(ids.map(Long::toString)))
                .put("displayList", JSONArray(ids.map(Long::toString)))
            eapi(
                "/api/listen/together/sync/list/command/report",
                JSONObject().put("roomId", roomId).put("playlistParam", playlist.toString()),
                true,
            )
            Unit
        }

    suspend fun sendListenTogetherHeartbeat(
        roomId: String,
        songId: Long,
        isPlaying: Boolean,
        progressMs: Long,
    ) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        eapi(
            "/api/listen/together/heartbeat",
            JSONObject().put("roomId", roomId).put("songId", songId)
                .put("playStatus", if (isPlaying) "PLAY" else "PAUSE")
                .put("progress", progressMs.coerceAtLeast(0L)),
            true,
        )
        Unit
    }

    suspend fun endListenTogetherRoom(roomId: String) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        eapi("/api/listen/together/end/v2", JSONObject().put("roomId", roomId), true)
        Unit
    }

    suspend fun messageContacts(userId: Long, limit: Int = 200): List<MeloXMessageContact> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/user/getfollows/$userId",
            JSONObject().put("offset", 0).put("limit", limit.coerceIn(1, 1_000)).put("order", true),
            true,
        )
        parseMessageContacts(response.optJSONArray("follow"))
    }

    suspend fun privateMessageConversations(limit: Int = 50): List<MeloXMessageContact> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/msg/private/users",
            JSONObject().put("offset", 0).put("limit", limit.coerceIn(1, 100)).put("total", "true"),
            true,
        )
        val messages = response.optJSONArray("msgs") ?: JSONArray()
        buildList {
            for (index in 0 until messages.length()) {
                val value = messages.optJSONObject(index) ?: continue
                val candidates = listOf(value.optJSONObject("fromUser"), value.optJSONObject("toUser"))
                candidates.mapNotNull(::parseMessageContact).forEach { contact ->
                    if (none { it.id == contact.id }) add(contact)
                }
            }
        }
    }

    suspend fun privateMessageHistory(userId: Long, limit: Int = 100): List<MeloXPrivateMessage> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = eapi(
            "/api/msg/private/history",
            JSONObject().put("userId", userId).put("limit", limit.coerceIn(1, 200)).put("time", -1).put("total", "true"),
            true,
        )
        val messages = response.optJSONArray("msgs") ?: JSONArray()
        buildList {
            for (index in 0 until messages.length()) {
                val value = messages.optJSONObject(index) ?: continue
                val time = value.optLong("time", 0L)
                val serialized = value.optString("msg")
                val payload = runCatching { JSONObject(serialized) }.getOrNull()
                val text = payload?.optString("msg")?.takeIf(String::isNotBlank)
                    ?: payload?.optJSONObject("song")?.optString("name")?.takeIf(String::isNotBlank)?.let { "[歌曲] $it" }
                    ?: payload?.optJSONObject("playlist")?.optString("name")?.takeIf(String::isNotBlank)?.let { "[歌单] $it" }
                    ?: serialized.ifBlank { "私信" }
                add(MeloXPrivateMessage(
                    id = value.optLong("id", time),
                    fromUserId = value.optJSONObject("fromUser")?.optLong("userId", 0L) ?: 0L,
                    toUserId = value.optJSONObject("toUser")?.optLong("userId", 0L) ?: 0L,
                    text = text,
                    timeMs = time,
                ))
            }
        }.sortedBy(MeloXPrivateMessage::timeMs)
    }

    suspend fun sendPrivateText(message: String, userId: Long) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val text = message.trim()
        if (text.isBlank()) throw IOException("请输入私信内容")
        eapi(
            "/api/msg/private/send",
            JSONObject().put("type", "text").put("msg", text).put("userIds", "[$userId]"),
            true,
        )
        Unit
    }

    private fun parseMessageContacts(values: JSONArray?): List<MeloXMessageContact> = buildList {
        val source = values ?: JSONArray()
        for (index in 0 until source.length()) parseMessageContact(source.optJSONObject(index))?.let(::add)
    }

    private fun parseMessageContact(value: JSONObject?): MeloXMessageContact? {
        value ?: return null
        val id = value.optLong("userId", 0L)
        if (id <= 0L) return null
        return MeloXMessageContact(
            id = id,
            name = value.optString("remarkName").ifBlank { value.optString("nickname") }.ifBlank { "网易云用户" },
            avatarUrl = secure(value.optString("avatarUrl").takeIf(String::isNotBlank)),
            signature = value.optString("signature"),
        )
    }

    private fun parseListenTogetherRoom(value: JSONObject?): MeloXListenTogetherRoom? {
        value ?: return null
        val id = value.optString("roomId").ifBlank { value.optLong("roomId", 0L).takeIf { it > 0L }?.toString().orEmpty() }
        if (id.isBlank()) return null
        val users = value.optJSONArray("roomUsers") ?: JSONArray()
        return MeloXListenTogetherRoom(
            id = id,
            creatorId = value.optString("creatorId").ifBlank { value.optLong("creatorId", 0L).takeIf { it > 0L }?.toString().orEmpty() },
            users = buildList {
                for (index in 0 until users.length()) {
                    val entry = users.optJSONObject(index) ?: continue
                    val profile = entry.optJSONObject("userInfo") ?: entry
                    val userId = profile.optString("userId").ifBlank { profile.optLong("userId", 0L).toString() }
                    add(MeloXListenTogetherUser(
                        id = userId,
                        name = profile.optString("nickname").ifBlank { "网易云用户" },
                        avatarUrl = secure(profile.optString("avatarUrl").takeIf(String::isNotBlank)),
                    ))
                }
            },
        )
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
