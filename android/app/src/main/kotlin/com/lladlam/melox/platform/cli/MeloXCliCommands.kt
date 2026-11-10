package com.lladlam.melox.platform.cli

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.playback.ProviderPlaybackRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared command surface used by the ADB CLI receiver and the local MCP server. */
internal object MeloCliCommands {
    private val commands = setOf(
        "status", "queue", "play", "pause", "toggle", "next", "previous", "stop",
        "seek", "volume", "shuffle", "repeat", "search", "search-play",
    )

    suspend fun execute(context: Context, command: String, args: JSONObject = JSONObject()): JSONObject {
        require(command in commands) { "未知命令：$command" }
        val tracks = if (command == "search" || command == "search-play") {
            val sourceName = args.optString("source", "netease")
            val source = MusicSource.entries.firstOrNull { it.storageValue == sourceName }
                ?: error("未知音源：$sourceName")
            val query = args.optString("query").trim()
            require(query.isNotEmpty()) { "query 不能为空" }
            withContext(Dispatchers.IO) {
                ProviderPlaybackRuntime.initialize(context)
                val provider = ProviderPlaybackRuntime.registryOrNull()?.get(source) as? SearchCapability
                    ?: error("该音源不支持搜索")
                provider.searchSongs(query, pageSize = 10).items
            }
        } else emptyList()
        if (command == "search") {
            return JSONObject().put("tracks", JSONArray().apply {
                tracks.forEachIndexed { index, track ->
                    put(JSONObject().put("index", index).put("source", track.id.sourceId.value)
                        .put("id", track.id.value).put("title", track.title).put("artist", track.artistText))
                }
            })
        }
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MeloXPlaybackService::class.java)),
        ).buildAsync()
        try {
            val player = suspendCancellableCoroutine<MediaController> { continuation ->
                future.addListener({
                    if (continuation.isActive) {
                        try {
                            continuation.resume(future.get())
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            // MediaController enforces its application looper. ADB invokes the
            // receiver on the main thread, but MCP calls this from a server
            // worker, so always marshal player access explicitly.
            return withContext(Dispatchers.Main.immediate) {
                when (command) {
                    "play" -> { player.prepare(); player.play() }
                    "pause" -> player.pause()
                    "toggle" -> if (player.playWhenReady) player.pause() else { player.prepare(); player.play() }
                    "next" -> player.seekToNextMediaItem()
                    "previous" -> player.seekToPreviousMediaItem()
                    "stop" -> player.stop()
                    "seek" -> {
                        val ms = args.optString("value").toLongOrNull()
                            ?: args.optLong("positionMs", -1L).takeIf { it >= 0L }
                        require(ms != null && ms >= 0L) { "seek 需要非负毫秒数" }
                        player.seekTo(ms)
                    }
                    "volume" -> {
                        val volume = args.optString("value").toFloatOrNull()
                            ?: args.optDouble("volume", Double.NaN).takeIf { !it.isNaN() }?.toFloat()
                        require(volume != null && volume.isFinite() && volume in 0f..1f) { "volume 范围为 0–1" }
                        player.volume = volume
                    }
                    "shuffle" -> {
                        val value = args.optString("value").ifBlank {
                            if (args.optBoolean("enabled", false)) "on" else "off"
                        }
                        require(value == "on" || value == "off") { "shuffle 使用 on/off" }
                        player.shuffleModeEnabled = value == "on"
                    }
                    "repeat" -> {
                        player.repeatMode = when (args.optString("value")) {
                            "off" -> 0
                            "one" -> 1
                            "all" -> 2
                            else -> error("repeat 使用 off/one/all")
                        }
                    }
                    "search-play" -> {
                        val index = args.optInt("index", args.optString("value").toIntOrNull() ?: 0)
                        require(index in tracks.indices) { "搜索结果为空或 index 超出范围" }
                        player.setMediaItems(tracks.map { ProviderPlaybackCommands.mediaItemFor(it) }, index, 0)
                        player.prepare()
                        player.play()
                    }
                }
                JSONObject().put("command", command).put("playing", player.isPlaying)
                    .put("playWhenReady", player.playWhenReady).put("state", player.playbackState)
                    .put("mediaId", player.currentMediaItem?.mediaId.orEmpty())
                    .put("title", player.mediaMetadata.title?.toString().orEmpty())
                    .put("artist", player.mediaMetadata.artist?.toString().orEmpty())
                    .put("positionMs", player.currentPosition)
                    .put("durationMs", player.duration.takeIf { it != C.TIME_UNSET } ?: -1)
                    .put("volume", player.volume).put("shuffle", player.shuffleModeEnabled)
                    .put("repeat", player.repeatMode).apply {
                        if (command == "queue") put("queue", JSONArray().apply {
                            repeat(player.mediaItemCount) { index ->
                                val item = player.getMediaItemAt(index)
                                put(JSONObject().put("index", index).put("mediaId", item.mediaId)
                                    .put("title", item.mediaMetadata.title?.toString().orEmpty()))
                            }
                        })
                    }
            }
        } finally {
            MediaController.releaseFuture(future)
        }
    }
}
