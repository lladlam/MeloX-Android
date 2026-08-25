package com.lladlam.melox.core.remoteconfig

object MeloXRemoteConfigDefaults {
    val AllowedCapabilities = setOf(
        "netease_phone_login",
        "qq_phone_login",
        "qq_playback",
        "kugou_playback",
        "bilibili_playback",
        "spotify_oauth",
        "cross_provider_fallback",
    )
    val AllowedProviders = setOf("qq_music", "kugou", "bilibili")
    val FallbackOrder = listOf("qq_music", "kugou", "bilibili")
    val Strategies = mapOf(
        "netease_playback" to "v1",
        "qq_playback" to "v1",
        "kugou_playback" to "v1",
    )
    val Config = MeloXRemoteConfig(
        schemaVersion = 1,
        configVersion = 0,
        issuedAtEpochSeconds = 0L,
        minVersionCode = 1,
        maxVersionCode = Int.MAX_VALUE,
        disabledCapabilities = emptySet(),
        strategies = Strategies,
        fallback = MeloXRemoteFallbackConfig(
            enabled = true,
            order = FallbackOrder,
            disabledProviders = emptySet(),
            timeoutMs = 6_000,
        ),
        notice = null,
    )
}
