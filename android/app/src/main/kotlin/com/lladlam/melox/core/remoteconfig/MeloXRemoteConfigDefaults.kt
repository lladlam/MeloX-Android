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
    val AllowedProviders = setOf(
        "netease",
        "qq_music",
        "kugou_lite",
        "qishui",
        "kuwo",
        "bilibili",
        "youtube_music",
        "jellyfin",
    )

    /**
     * 跨平台兜底的默认顺序。
     *
     * 2026-10-23 应用户要求把 B站 提到最前：B站 不依赖对方平台登录态
     * （QQ/酷狗的播放接口要 uin+musicKey），且覆盖大量上传音源。
     * 2026-11-15 加入 YouTube Music：NewPipe 匿名接口即可取到完整音频，登录后自动
     * 带 Cookie 走凭证；与 B站 一起放在最前，保证「没登录任何国内平台」也有兜底。
     * Jellyfin 只有用户配置了自建服务器才会命中，放最后，不影响普通用户。
     * 2026-10-25 同步上游 0.7.0：成员集合换成上游的（新增 netease / kugou_lite /
     * qishui / kuwo；上游把旧的 `kugou` 换成 `kugou_lite`，而注册表里现在也确实
     * 只有概念版 KugouLiteProvider，旧的 `kugou` 会是个死 id）；**顺序仍沿用本地口径**
     * —— bilibili / YouTube Music 在最前，其余按上游顺序。
     * 注意这只是默认值，远程配置仍可覆盖。
     */
    val FallbackOrder = listOf(
        "bilibili",
        "youtube_music",
        "netease",
        "qq_music",
        "kugou_lite",
        "qishui",
        "kuwo",
        "jellyfin",
    )
    val Strategies = mapOf(
        "netease_playback" to "v1",
        "qq_playback" to "v1",
        "kugou_playback" to "v1",
    )

    /**
     * Values a strategy may take. `compat` caps NetEase requests at 高品质 and
     * exists so lossless/immersive endpoints can be abandoned without an APK.
     */
    val AllowedStrategyValues = mapOf(
        "netease_playback" to setOf("v1", "compat"),
        "qq_playback" to setOf("v1"),
        "kugou_playback" to setOf("v1"),
    )

    /** Only files under this prefix may be referenced by the updater. */
    const val PinnedReleasePrefix = "https://github.com/lladlam/MeloX-Android/releases/download/"

    val Rules = MeloXRemoteRules(
        lyricMatchOriginalToleranceMs = 2_000L,
        lyricMatchVersionToleranceMs = 1_000L,
        searchTitleStripSuffixes = emptyList(),
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
        textOverrides = emptyMap(),
        scripts = emptyMap(),
        colorOverrides = emptyMap(),
        rules = Rules,
        updatePatches = emptyList(),
        securityUpdate = MeloXRemoteSecurityUpdate(enabled = false, message = null),
    )
}
