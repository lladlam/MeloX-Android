package com.lladlam.melox.core.provider.thirdparty

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun resolverRequestUrl(settings: ResolverSettings, track: MusicTrack, quality: AudioQualityTier): HttpUrl? {
    if (!settings.configured) return null
    val source = track.id.sourceId.builtIn ?: return null
    val platform = when (source) {
        MusicSource.Netease -> "netease"
        MusicSource.QQMusic -> "tencent"
        MusicSource.Kugou, MusicSource.KugouLite -> "kugou"
        else -> return null
    }
    if (settings.kind == ResolverKind.Unm && source != MusicSource.Netease) return null
    val id = when (val metadata = track.providerMetadata) {
        is ProviderTrackMetadata.Netease -> metadata.numericId.toString()
        is ProviderTrackMetadata.QQMusic -> metadata.songMid
        is ProviderTrackMetadata.Kugou -> metadata.hash
        else -> track.id.value
    }
    if (id.isBlank()) return null
    val base = settings.endpoint.trim().toHttpUrlOrNull() ?: return null
    val builder = base.newBuilder()
    val bitrate = when (quality) {
        AudioQualityTier.Standard -> "128"
        AudioQualityTier.High -> "320"
        else -> "999"
    }
    val level = when (quality) {
        AudioQualityTier.Standard -> "standard"
        AudioQualityTier.High -> "exhigh"
        AudioQualityTier.Lossless -> "lossless"
        AudioQualityTier.HiResolution -> "hires"
        AudioQualityTier.Immersive -> "jyeffect"
        AudioQualityTier.Master -> "jymaster"
    }
    when (settings.kind) {
        ResolverKind.Meting -> {
            // A Meting endpoint may be /, /api.php, /meting/ or any custom path.
            // Do not guess and append /api (the old fork broke valid endpoints).
            builder.setQueryParameter("server", platform).setQueryParameter("type", "url")
                .setQueryParameter("id", id).setQueryParameter("br", bitrate).setQueryParameter("level", level)
            if (settings.credential.isNotBlank()) builder.setQueryParameter("auth", settings.credential)
        }
        ResolverKind.Chksz -> {
            val endpoint = when (source) {
                MusicSource.Netease -> "163_music"
                MusicSource.QQMusic -> "qq_music"
                else -> "kugou_music"
            }
            val path = base.encodedPath.trimEnd('/')
            builder.encodedPath("$path${if (path.endsWith("/api")) "" else "/api"}/$endpoint")
                .setQueryParameter(if (source == MusicSource.QQMusic) "mid" else "id", id)
                .setQueryParameter("type", "json").setQueryParameter("apikey", settings.credential)
            if (source == MusicSource.Netease) builder.setQueryParameter("level", level)
            else builder.setQueryParameter("size", when (quality) {
                AudioQualityTier.Standard -> "128k"
                AudioQualityTier.High -> "320k"
                AudioQualityTier.Lossless -> "flac"
                AudioQualityTier.HiResolution -> "hires"
                else -> "master"
            })
        }
        ResolverKind.Unm -> {
            val path = base.encodedPath.trimEnd('/')
            builder.encodedPath(if (path.endsWith("/match")) path else "$path/match")
                .setQueryParameter("id", id).setQueryParameter("server", settings.servers)
        }
    }
    return builder.build()
}

/** Only audio fields inside documented response containers are considered. */
internal object ResolverAudioUrl {
    private val audioFields = listOf("url", "playUrl", "play_url", "audioUrl", "audio_url", "proxyUrl", "downloadUrl")
    private val containers = listOf("data", "result", "song", "songs", "track", "tracks", "music", "items")
    private val nonAudioExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "lrc", "html", "json")

    fun valid(value: String): String? = value.trim().toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.substringAfterLast('.', "").lowercase() !in nonAudioExtensions
    }?.toString()

    fun parse(body: String): String? {
        val text = body.trim()
        valid(text)?.let { return it }
        // Enforce depth before org.json recursively builds the tree. A byte
        // limit alone still permits thousands of nested brackets.
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 16) return null
                '}', ']' -> if (--depth < 0) return null
            }
        }
        if (quoted || depth != 0) return null
        return runCatching { extract(JSONTokener(text).nextValue()) }.getOrNull()
    }

    private fun extract(value: Any?, depth: Int = 0): String? {
        if (depth > 8) return null
        return when (value) {
            is JSONObject -> {
                if (value.opt("success") == false) return null
                val code = value.opt("code")
                if (code is Number && code.toInt() !in setOf(0, 1, 200, 206)) return null
                audioFields.asSequence().mapNotNull { value.opt(it) as? String }.mapNotNull(::valid).firstOrNull()
                    ?: containers.asSequence().mapNotNull { extract(value.opt(it), depth + 1) }.firstOrNull()
            }
            is JSONArray -> (0 until minOf(value.length(), 100)).asSequence()
                .mapNotNull { extract(value.opt(it), depth + 1) }.firstOrNull()
            is String -> valid(value)
            else -> null
        }
    }
}
