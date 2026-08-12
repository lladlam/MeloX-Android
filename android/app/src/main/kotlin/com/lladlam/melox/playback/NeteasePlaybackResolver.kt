package com.lladlam.melox.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class NeteasePlaybackResolver(
    private val cookieProvider: () -> String = { "" },
    @Suppress("UNUSED_PARAMETER")
    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),
    private val localSourceProvider: (Long) -> Uri? = { null },
) : ResolvingDataSource.Resolver {
    private data class ResolveKey(
        val songId: Long,
        val quality: MusicQuality,
        val cookieHeader: String,
    )

    private val resolvedUris = ConcurrentHashMap<ResolveKey, Uri>()
    private val qualityClient = NeteaseQualityClient(cookieProvider = cookieProvider)

    /**
     * Resolves the same source used by ExoPlayer for offline analysis. Keeping
     * this path in the resolver guarantees that AutoMix never analyses a
     * different quality (or a stale anonymous URL) from the playing deck.
     */
    fun resolveSongUri(
        songId: Long,
        quality: MusicQuality = MusicQualityRuntime.selected,
    ): Uri {
        localSourceProvider(songId)?.let { return it }
        val cookieHeader = cookieProvider()
        val key = ResolveKey(songId, quality, cookieHeader)
        return resolvedUris[key] ?: run {
            val source = qualityClient.playbackSourceBlocking(
                songId = songId,
                requestedQuality = quality,
            )
            Uri.parse(source.url).also { resolvedUris[key] = it }
        }
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != MELOX_SCHEME || uri.host != SONG_HOST) {
            return dataSpec
        }

        val songId = uri.lastPathSegment?.toLongOrNull()
            ?: throw IOException("Invalid MeloX song URI: $uri")
        localSourceProvider(songId)?.let { local ->
            return dataSpec.withUri(local)
        }
        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))
            ?: MusicQualityRuntime.selected
        val currentCookieHeader = cookieProvider()
        val key = ResolveKey(songId, requestedQuality, currentCookieHeader)

        val resolved = resolveSongUri(songId, requestedQuality)

        return dataSpec.withUri(resolved)
    }

    override fun resolveReportedUri(uri: Uri): Uri {
        if (uri.scheme != MELOX_SCHEME || uri.host != SONG_HOST) return uri
        val songId = uri.lastPathSegment?.toLongOrNull() ?: return uri
        localSourceProvider(songId)?.let { return it }
        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))
            ?: MusicQualityRuntime.selected
        val currentCookieHeader = cookieProvider()
        return resolvedUris[ResolveKey(songId, requestedQuality, currentCookieHeader)] ?: uri
    }

    companion object {
        private const val MELOX_SCHEME = "melox"
        private const val SONG_HOST = "song"
        private const val QUALITY_QUERY = "quality"

        fun uriForSong(
            songId: Long,
            quality: MusicQuality = MusicQualityRuntime.selected,
        ): Uri = Uri.Builder()
            .scheme(MELOX_SCHEME)
            .authority(SONG_HOST)
            .appendPath(songId.toString())
            .appendQueryParameter(QUALITY_QUERY, quality.apiLevel)
            .build()
    }
}
