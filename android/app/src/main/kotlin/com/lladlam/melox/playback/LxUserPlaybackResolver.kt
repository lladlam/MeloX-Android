package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.provider.lxuser.LxUserRuntime
import com.lladlam.melox.core.provider.lxuser.LxUserScript
import com.lladlam.melox.core.provider.lxuser.LxUserSourceStore
import java.io.IOException

internal data class LxUserPlaybackResult(
    val sourceId: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/** Resolves a song through locally installed LX Music user API scripts. */
class LxUserPlaybackResolver(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun cacheIdentity(): String = LxUserSourceStore.list(appContext).joinToString("|") { it.id }

    internal fun resolve(
        songId: Long,
        title: String,
        artist: String,
        durationMs: Long?,
        quality: AudioQualityTier,
    ): LxUserPlaybackResult? {
        if (title.isBlank() || artist.isBlank()) return null
        val song = mapOf<String, Any?>(
            "id" to songId,
            "songId" to songId,
            "name" to title,
            "title" to title,
            "artist" to artist,
            "singer" to artist,
            "duration" to durationMs?.div(1_000L),
            "durationMs" to durationMs,
            "quality" to quality.name,
        )
        for (record in LxUserSourceStore.list(appContext)) {
            val script = LxUserSourceStore.script(appContext, record.id) ?: continue
            runCatching {
                LxUserRuntime().use { runtime ->
                    runtime.load(LxUserScript(script))
                    listOf("kw", "kg", "tx", "wy", "mg").asSequence()
                        .mapNotNull { source ->
                            val response = runtime.callAction(
                                "musicUrl",
                                song + mapOf(
                                    "source" to source,
                                    "type" to quality.name,
                                    "musicInfo" to song,
                                ),
                            )
                            response?.let { value ->
                                val url = when (value) {
                                    is String -> value
                                    is Map<*, *> -> value["url"]?.toString()
                                    else -> null
                                }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                url?.let { LxUserPlaybackResult(record.id, it) }
                            }
                        }
                        .firstOrNull() ?: throw IOException("LX 音乐源没有返回可播放链接")
                }
            }.onSuccess { return it }
        }
        return null
    }
}
