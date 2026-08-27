package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.ChkszApiClient
import com.lladlam.melox.core.provider.lxuser.ChkszApiKeyStore

internal data class ChkszPlaybackResult(val url: String)

class ChkszPlaybackResolver(context: Context) {
    private val appContext = context.applicationContext
    private val client = ChkszApiClient({ ChkszApiKeyStore.read(appContext) })

    fun cacheIdentity(): String = ChkszApiKeyStore.read(appContext).let { key ->
        if (key.isBlank()) "disabled" else "configured:${key.hashCode()}"
    }

    internal fun resolve(songId: Long, quality: AudioQualityTier): ChkszPlaybackResult? =
        client.resolveNetease(songId, quality)?.let(::ChkszPlaybackResult)

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): ChkszPlaybackResult? =
        client.resolveTrack(track, quality)?.let(::ChkszPlaybackResult)
}
