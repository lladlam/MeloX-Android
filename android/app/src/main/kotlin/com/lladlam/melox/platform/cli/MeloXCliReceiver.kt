package com.lladlam.melox.platform.cli

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.playback.MeloXPlaybackService
import com.lladlam.melox.playback.ProviderPlaybackCommands
import com.lladlam.melox.playback.ProviderPlaybackRuntime
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit, shell-only broadcast endpoint. Replies are base64 UTF-8 JSON. */
class MeloXCliReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val reply = try {
                check(MeloXSettingsPreferences.boolean(app, "experimental_cli_enabled", false)) {
                    "请先在设置 → 测试功能尝鲜中开启 CLI 模式"
                }
                withTimeout(8_000) { execute(app, intent) }.put("ok", true)
            } catch (error: Exception) {
                JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName)
            }
            try {
                pending.resultCode = if (reply.optBoolean("ok")) 0 else 1
                pending.resultData = "melox:" + Base64.encodeToString(reply.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun execute(context: Context, intent: Intent): JSONObject {
        val command = intent.getStringExtra("command") ?: "status"
        require(command in setOf("status", "queue", "play", "pause", "toggle", "next", "previous", "stop", "seek", "volume", "shuffle", "repeat", "search", "search-play")) {
            "未知命令：$command"
        }
        val tracks = if (command == "search" || command == "search-play") {
            val sourceName = intent.getStringExtra("source") ?: "netease"
            val source = MusicSource.entries.firstOrNull { it.storageValue == sourceName }
                ?: error("未知音源：$sourceName")
            val query = intent.getStringExtra("query").orEmpty().trim()
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
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MeloXPlaybackService::class.java))).buildAsync()
        try {
            val player = suspendCancellableCoroutine<MediaController> { continuation ->
                future.addListener({
                    if (continuation.isActive) {
                        try { continuation.resume(future.get()) }
                        catch (error: Exception) { continuation.resumeWithException(error) }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            when (command) {
                "play" -> { player.prepare(); player.play() }
                "pause" -> player.pause()
                "toggle" -> if (player.playWhenReady) player.pause() else { player.prepare(); player.play() }
                "next" -> player.seekToNextMediaItem()
                "previous" -> player.seekToPreviousMediaItem()
                "stop" -> player.stop()
                "seek" -> {
                    val ms = intent.getStringExtra("value")?.toLongOrNull()
                    require(ms != null && ms >= 0) { "seek 需要非负毫秒数" }
                    player.seekTo(ms)
                }
                "volume" -> {
                    val volume = intent.getStringExtra("value")?.toFloatOrNull()
                    require(volume != null && volume.isFinite() && volume in 0f..1f) { "volume 范围为 0–1" }
                    player.volume = volume
                }
                "shuffle" -> {
                    val value = intent.getStringExtra("value")
                    require(value == "on" || value == "off") { "shuffle 使用 on/off" }
                    player.shuffleModeEnabled = value == "on"
                }
                "repeat" -> {
                    player.repeatMode = when (intent.getStringExtra("value")) {
                        "off" -> 0
                        "one" -> 1
                        "all" -> 2
                        else -> error("repeat 使用 off/one/all")
                    }
                }
                "search-play" -> {
                    val index = intent.getStringExtra("value")?.toIntOrNull() ?: 0
                    require(index in tracks.indices) { "搜索结果为空或 index 超出范围" }
                    player.setMediaItems(tracks.map { ProviderPlaybackCommands.mediaItemFor(it) }, index, 0)
                    player.prepare()
                    player.play()
                }
            }
            return JSONObject().put("command", command).put("playing", player.isPlaying)
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
        } finally {
            MediaController.releaseFuture(future)
        }
    }
}
