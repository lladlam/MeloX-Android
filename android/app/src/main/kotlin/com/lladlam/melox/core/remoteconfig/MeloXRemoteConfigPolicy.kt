package com.lladlam.melox.core.remoteconfig

import android.content.Context
import com.lladlam.melox.core.music.model.MusicSource

object MeloXRemoteConfigPolicy {
    fun activeConfig(context: Context): MeloXRemoteConfig = effectiveConfig(
        consentEnabled = MeloXRemoteConfigConsent.enabled(context),
        status = MeloXRemoteConfigRuntime.status.value,
    )

    fun capabilityEnabled(context: Context, capability: String): Boolean =
        capability !in activeConfig(context).disabledCapabilities

    fun providerPlaybackEnabled(context: Context, source: MusicSource): Boolean = providerPlaybackEnabled(
        config = activeConfig(context),
        source = source,
    )

    internal fun effectiveConfig(
        consentEnabled: Boolean,
        status: MeloXRemoteConfigStatus,
    ): MeloXRemoteConfig = if (consentEnabled && status.source == MeloXRemoteConfigSource.VerifiedRemote) {
        status.config
    } else {
        MeloXRemoteConfigDefaults.Config
    }

    internal fun providerPlaybackEnabled(config: MeloXRemoteConfig, source: MusicSource): Boolean = when (source) {
        MusicSource.QQMusic -> "qq_playback" !in config.disabledCapabilities
        MusicSource.Kugou -> "kugou_playback" !in config.disabledCapabilities
        MusicSource.Kuwo -> "kuwo_playback" !in config.disabledCapabilities
        MusicSource.Bilibili -> "bilibili_playback" !in config.disabledCapabilities
        else -> true
    }
}
