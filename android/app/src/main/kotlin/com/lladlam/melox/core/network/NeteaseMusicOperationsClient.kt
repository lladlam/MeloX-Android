package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteasePlaylistSummary
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
    val id: Long, val user: String, val avatarUrl: String?, val content: String,
    val likedCount: Long, val timeText: String, val replyCount: Int = 0,
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
    val latestMessage: String? = null,
    val latestMessageTimeMs: Long = 0L,
    val unreadCount: Int = 0,
)
data class MeloXPrivateMessage(
    val id: Long,
    val fromUserId: Long,
    val toUserId: Long,
    val text: String,
    val timeMs: Long,
    val resource: MeloXPrivateMessageResource? = null,
)
enum class MeloXPrivateMessageResourceKind { Song, Playlist, Album }
data class MeloXPrivateMessageResource(
    val kind: MeloXPrivateMessageResourceKind,
    val id: Long,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
)
private data class MeloXPrivateMessagePayload(
    val text: String,
    val resource: MeloXPrivateMessageResource?,
) {
    val summary: String get() = text.takeIf(String::isNotBlank)
        ?: resource?.let { "[${when (it.kind) { MeloXPrivateMessageResourceKind.Song -> "歌曲"; MeloXPrivateMessageResourceKind.Playlist -> "歌单"; MeloXPrivateMessageResourceKind.Album -> "专辑" }}] ${it.title}" }
        ?: "私信"
}

internal data class NeteasePlaylistCreateSpec(val name: String, val privacy: Int)

internal fun neteasePlaylistCreateSpec(name: String, privatePlaylist: Boolean): NeteasePlaylistCreateSpec {
    val normalizedName = name.trim()
    require(normalizedName.isNotBlank()) { "歌单名称不能为空" }
    return NeteasePlaylistCreateSpec(normalizedName, if (privatePlaylist) 10 else 0)
}

internal data class NeteasePlaylistMutationSpec(
    val songId: Long,
    val playlistId: Long,
    val operation: String,
)

internal fun neteasePlaylistMutationSpec(
    songId: Long,
    playlistId: Long,
    operation: String,
): NeteasePlaylistMutationSpec {
    require(songId > 0L) { "歌曲 ID 无效" }
    require(playlistId > 0L) { "歌单 ID 无效" }
    require(operation == "add" || operation == "del") { "歌单写操作无效" }
    return NeteasePlaylistMutationSpec(songId, playlistId, operation)
}

/** Authenticated write/comment/wiki routes mirrored from upstream MeloX NeteaseAPI. */
class NeteaseMusicOperationsClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val syntheticDeviceId = randomHex(26).uppercase()
    private val authenticatedWeapi = NeteaseAuthenticatedWeapi(cookieProvider, httpClient)

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
        val spec = neteasePlaylistMutationSpec(songId, playlistId, "add")
        ensureLoggedIn()
        val path = "/api/v1/playlist/manipulate/tracks"
        val id = spec.songId.toString()
        val first = runCatching {
            eapi(path, JSONObject().put("op", spec.operation).put("pid", spec.playlistId)
                .put("trackIds", "[\"$id\"]").put("imme", "true"), true)
        }
        if (first.isFailure) {
            eapi(path, JSONObject().put("op", spec.operation).put("pid", spec.playlistId)
                .put("trackIds", "[\"$id\",\"$id\"]").put("imme", "true"), true)
        }
        Unit
    }

    suspend fun createPlaylist(name: String, privatePlaylist: Boolean): NeteasePlaylistSummary =
        withContext(Dispatchers.IO) {
            val spec = neteasePlaylistCreateSpec(name, privatePlaylist)
            ensureLoggedIn()
            val response = eapi(
                "/api/playlist/create",
                JSONObject()
                    .put("name", spec.name)
                    .put("privacy", spec.privacy),
                true,
            )
            val playlist = response.optJSONObject("playlist")
                ?: throw IOException("网易云没有返回新建歌单")
            val id = playlist.optLong("id", 0L)
            if (id <= 0L) throw IOException("网易云没有返回有效的歌单 ID")
            val creator = playlist.optJSONObject("creator")
            NeteasePlaylistSummary(
                id = id,
                name = playlist.optString("name").ifBlank { spec.name },
                coverUrl = secure(playlist.optString("coverImgUrl").takeIf(String::isNotBlank)),
                trackCount = playlist.optInt("trackCount", 0),
                creatorName = creator?.optString("nickname").orEmpty(),
                creatorUserId = creator?.optLong("userId", 0L)?.takeIf { it > 0L },
                playCount = playlist.optLong("playCount", 0L),
                description = playlist.optString("description").takeIf(String::isNotBlank),
            )
        }

    suspend fun removeSongFromPlaylist(songId: Long, playlistId: Long) = withContext(Dispatchers.IO) {
        val spec = neteasePlaylistMutationSpec(songId, playlistId, "del")
        ensureLoggedIn()
        val id = spec.songId.toString()
        eapi(
            "/api/v1/playlist/manipulate/tracks",
            JSONObject()
                .put("op", spec.operation)
                .put("pid", spec.playlistId)
                .put("trackIds", "[\"$id\"]")
                .put("imme", "true"),
            true,
        )
        Unit
    }

    suspend fun setPlaylistSubscribed(playlistId: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val data = JSONObject()
            .put("id", playlistId)
            .put("t", if (subscribed) 1 else 2)
        eapi("/api/playlist/subscribe", data, true)
        Unit
    }

    suspend fun songComments(songId: Long, limit: Int = 100): List<MeloXMusicComment> = withContext(Dispatchers.IO) {
        val path = "/api/v1/resource/comments/R_SO_4_$songId"; val data = JSONObject().put("rid", songId).put("limit", limit.coerceIn(1, 100)).put("offset", 0).put("beforeTime", 0)
        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider()); val result = if (loggedIn) try { authenticatedWeapi.post(path, data) } catch (error: IOException) { if (!error.message.orEmpty().contains("空响应")) throw error; eapi(path, data, true) } else eapi(path, data, false)
        val hot = result.optJSONArray("hotComments") ?: JSONArray(); val normal = result.optJSONArray("comments") ?: JSONArray(); val seen = mutableSetOf<Long>()
        buildList { fun addArray(values: JSONArray) { for (i in 0 until values.length()) { val c = values.optJSONObject(i) ?: continue; val id = c.optLong("commentId", -1L); if (id <= 0L || !seen.add(id)) continue; val user = c.optJSONObject("user"); add(MeloXMusicComment(id, user?.optString("nickname").orEmpty().ifBlank { "网易云用户" }, secure(user?.optString("avatarUrl")?.takeIf(String::isNotBlank)), c.optString("content").ifBlank { "…" }, c.optLong("likedCount", 0L), c.optString("timeStr"), c.optInt("replyCount", c.optJSONArray("beReplied")?.length() ?: 0))) } }; addArray(hot); addArray(normal) }
    }

    suspend fun songWiki(songId: Long): List<MeloXWikiSection> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val result = eapi(
            "/api/song/play/about/block/page",
            JSONObject().put("songId", songId),
            true,
        )
        val blocks = result.optJSONObject("data")?.optJSONArray("blocks") ?: JSONArray()
        buildList<MeloXWikiSection> {
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
        val response = try {
            authenticatedWeapi.post("/api/listen/together/status/get")
        } catch (error: IOException) {
            // Upstream MeloX only falls back when WEAPI returns an empty body.
            // Preserve that behavior instead of masking real authentication/server errors.
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi("/api/listen/together/status/get", JSONObject(), true)
        }
        val data = response.optJSONObject("data") ?: return@withContext null
        val roomInfo = data.optJSONObject("roomInfo")
        val inRoom = if (data.has("inRoom")) data.optBoolean("inRoom", false) else roomInfo != null
        if (!inRoom) return@withContext null
        parseListenTogetherRoom(roomInfo)
    }

    suspend fun joinListenTogetherRoom(roomId: String, inviterId: String): MeloXListenTogetherRoom = withContext(Dispatchers.IO) {
        ensureLoggedIn()

        // A stale local UI can ask to join a room the current NetEase account is
        // already in. The server then correctly reports joinable=false. Restore
        // that existing session instead of turning it into a misleading join error.
        listenTogetherRoomStatus()?.takeIf { it.id == roomId }?.let { return@withContext it }

        val checkResponse = eapi(
            "/api/listen/together/room/check",
            JSONObject().put("roomId", roomId),
            true,
        )
        val check = checkResponse.optJSONObject("data")
            ?: throw IOException(
                checkResponse.optString("message")
                    .ifBlank { checkResponse.optString("msg") }
                    .ifBlank { "网易云没有返回一起听房间状态" },
            )
        // Upstream MeloX models the official response field as `joinable`.
        // Only fall back to the older guessed `canJoin` spelling when the
        // canonical field is absent.
        val joinable = if (check.has("joinable")) {
            check.optBoolean("joinable", false)
        } else {
            check.optBoolean("canJoin", false)
        }
        if (!joinable) {
            val status = check.optString("status").trim()
            val type = check.optString("type").trim()
            val serverMessage = checkResponse.optString("message")
                .ifBlank { checkResponse.optString("msg") }
                .trim()
            val reason = when {
                status.equals("FULL", true) -> "一起听房间人数已满"
                status.equals("END", true) || status.equals("ENDED", true) || status.equals("CLOSED", true) ->
                    "一起听房间已结束"
                serverMessage.isNotBlank() -> serverMessage
                status.isNotBlank() && type.isNotBlank() -> "该一起听房间当前无法加入（status=$status，type=$type）"
                status.isNotBlank() -> "该一起听房间当前无法加入（status=$status）"
                type.isNotBlank() -> "该一起听房间当前无法加入（type=$type）"
                else -> "该一起听房间当前无法加入"
            }
            throw IOException(reason)
        }

        val response = eapi(
            "/api/listen/together/play/invitation/accept",
            JSONObject().put("refer", "inbox_invite").put("roomId", roomId).put("inviterId", inviterId),
            true,
        )
        parseListenTogetherRoom(response.optJSONObject("data")?.optJSONObject("roomInfo"))
            ?: listenTogetherRoomStatus()?.takeIf { it.id == roomId }
            ?: throw IOException("加入成功，但未能读取目标房间信息")
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
        val path = "/api/user/getfollows/$userId"; val data = JSONObject().put("offset", 0).put("limit", limit.coerceIn(1, 1_000)).put("order", true); val response = socialRead(path, data)
        parseMessageContacts(response.optJSONArray("follow"))
    }

    suspend fun privateMessageConversations(currentUserId: Long, limit: Int = 50): List<MeloXMessageContact> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = socialRead("/api/msg/private/users", JSONObject().put("offset", 0).put("limit", limit.coerceIn(1, 100)).put("total", "true"))
        val messages = response.optJSONArray("msgs") ?: JSONArray()
        buildList<MeloXMessageContact> {
            for (index in 0 until messages.length()) {
                val value = messages.optJSONObject(index) ?: continue
                val latestMessage = parsePrivateMessagePayload(value.optString("lastMsg")).summary
                val latestTime = value.optLong("lastMsgTime", value.optLong("time", 0L))
                val unreadCount = value.optInt(
                    "newMsgCount",
                    value.optInt("unreadCount", value.optInt("newCount", 0)),
                ).coerceAtLeast(0)
                val from = parseMessageContact(value.optJSONObject("fromUser"))
                val to = parseMessageContact(value.optJSONObject("toUser"))
                val participant = from?.takeIf { it.id != currentUserId }
                    ?: to?.takeIf { it.id != currentUserId }
                    ?: from
                    ?: to
                    ?: continue
                if (none { it.id == participant.id }) add(
                    participant.copy(
                        latestMessage = latestMessage,
                        latestMessageTimeMs = latestTime,
                        unreadCount = unreadCount,
                    ),
                )
            }
        }.sortedByDescending(MeloXMessageContact::latestMessageTimeMs)
    }

    suspend fun privateMessageHistory(userId: Long, limit: Int = 100): List<MeloXPrivateMessage> = withContext(Dispatchers.IO) {
        ensureLoggedIn()
        val response = socialRead("/api/msg/private/history", JSONObject().put("userId", userId).put("limit", limit.coerceIn(1, 200)).put("time", -1).put("total", "true"))
        val messages = response.optJSONArray("msgs") ?: JSONArray()
        buildList {
            for (index in 0 until messages.length()) {
                val value = messages.optJSONObject(index) ?: continue
                val time = value.optLong("time", 0L)
                val serialized = value.optString("msg")
                val payload = parsePrivateMessagePayload(serialized)
                add(MeloXPrivateMessage(
                    id = value.optLong("id", time),
                    fromUserId = value.optJSONObject("fromUser")?.optLong("userId", 0L) ?: 0L,
                    toUserId = value.optJSONObject("toUser")?.optLong("userId", 0L) ?: 0L,
                    text = payload.text,
                    timeMs = time,
                    resource = payload.resource,
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

    private fun socialRead(path: String, data: JSONObject): JSONObject = try { authenticatedWeapi.post(path, data) } catch (error: IOException) { if (!error.message.orEmpty().contains("空响应")) throw error; eapi(path, data, true) }

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

    private fun parsePrivateMessagePayload(serialized: String): MeloXPrivateMessagePayload {
        val payload = runCatching { JSONObject(serialized) }.getOrNull()
        if (payload == null) return MeloXPrivateMessagePayload(serialized, null)
        val text = payload.optString("msg").trim()
        val resource = listOf(
            MeloXPrivateMessageResourceKind.Song to payload.optJSONObject("song"),
            MeloXPrivateMessageResourceKind.Playlist to payload.optJSONObject("playlist"),
            MeloXPrivateMessageResourceKind.Album to payload.optJSONObject("album"),
        ).firstNotNullOfOrNull { (kind, value) -> value?.let { parsePrivateMessageResource(kind, it) } }
        return MeloXPrivateMessagePayload(text, resource)
    }

    private fun parsePrivateMessageResource(
        kind: MeloXPrivateMessageResourceKind,
        value: JSONObject,
    ): MeloXPrivateMessageResource? {
        val id = value.optLong("id", 0L)
        if (id <= 0L) return null
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        val artists = value.optJSONArray("ar") ?: value.optJSONArray("artists")
        val artistText = buildList {
            if (artists != null) for (index in 0 until artists.length()) {
                artists.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ")
        val creator = value.optJSONObject("creator")?.optString("nickname").orEmpty()
        return MeloXPrivateMessageResource(
            kind = kind,
            id = id,
            title = value.optString("name").ifBlank { "网易云音乐" },
            subtitle = if (kind == MeloXPrivateMessageResourceKind.Playlist) creator else artistText,
            artworkUrl = secure(when (kind) {
                MeloXPrivateMessageResourceKind.Song -> album?.optString("picUrl")
                MeloXPrivateMessageResourceKind.Playlist -> value.optString("coverImgUrl").ifBlank { value.optString("picUrl") }
                MeloXPrivateMessageResourceKind.Album -> value.optString("picUrl")
            }?.takeIf(String::isNotBlank)),
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
