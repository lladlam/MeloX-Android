package com.lladlam.melox.ui.settings

import java.util.Locale

/** Local, offline "what's new" notes for the installed build. */
internal data class MeloXReleaseNotes(
    val version: String,
    val entries: List<String>,
)

internal object MeloXReleaseNotesCatalog {
    fun current(version: String = com.lladlam.melox.BuildConfig.VERSION_NAME): MeloXReleaseNotes {
        val chinese = Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("zh")
        val entries = if (chinese) chineseEntries else englishEntries
        return MeloXReleaseNotes(version, entries)
    }

    private val chineseEntries = listOf(
        "修复第三方音源 HTTP 被系统明文策略拦截：脚本导入、请求与播放改走隔离 HTTP 通道，支持动态域名、跳转与 Range 请求，不继承账号 Cookie。",
        "修复 LX 音源脚本跨线程调用 QuickJS 导致解析失败：脚本会话全程串行在所属线程，等待覆盖默认 HTTP 超时。",
        "实验性 USB 独占扩展到全速／高速 UAC1 与 16～32 位 PCM 描述符，按 USB 微帧调度包长；暂停保留缓冲、拖动进度清理旧音频。UAC2 与异步反馈暂未支持。",
        "USB 输出失败时可在导出日志中附带最近一次音频描述符、速度查询与失败原因（不含序列号），便于排查 DAC 兼容性。",
        "USB 独占与自动混音、LAN 投放互斥加固：交叉淡化不会断流 DAC，恢复本机输出不会造成喇叭与 DAC 双响。",
        "加固启动：账号加密迁移失败不再抛异常，播放控制器绑定与音量监听异常隔离，可选 Lyricon／小米链路失败不再传到主线程。",
        "修复旧系统冷启动：Android 10/EMUI 使用兼容输出面板与蓝牙设置，Android 14+ 路由与前台服务类型按版本判断。",
        "汽水音乐会话存储改用可恢复加密存储，密钥库异常自动降级为未登录，不再阻塞启动；扫码登录保留完整 Cookie，修复扫码后 404。",
        "汽水音乐扫码改用新版 scan_login 二维码并提示使用汽水音乐 App：上游旧二维码指向已 404 的 sdk-next 页面，导致抖音扫码提示「无法访问页面」。",
        "修复 YouTube Music 登录白屏：WebView 在加载中被提前销毁会带崩渲染进程；现在在视图释放时销毁，并在网络/VPN 失败时显示重试入口。",
        "补齐简体中文、繁体中文、日文翻译，并补充汽水音乐扫码与 YouTube 登录文案。",
        "新增 QQ 音乐括号歌词识别为伴唱：整行括号内容挂到上一句，从主歌词结束时开始，保留原结束时间（默认关闭）。",
        "跨平台聚合搜索按音质择优：同曲接近时长只保留可播放的最高音质来源，不同版本仍并列展示。",
        "系统歌词、悬浮窗歌词与各厂商锁屏歌词统一改用歌词页 Provider 加载链，支持全部内置音源。",
        "新增弥乐内「我喜欢」本地收藏歌单，首次启用可选择迁移音源；任意音源的爱心都写入这个歌单。",
        "新增「测试功能尝鲜 → CLI 模式」与 tools/melox_cli.py，可通过 ADB 查询并控制播放。",
        "构建产物拆分为 arm64-v8a 与 armeabi-v7a 两个 APK；自动混音期间流光背景合并双封面取色。",
    )

    private val englishEntries = listOf(
        "Fixed third-party HTTP sources blocked by the system cleartext policy: imports, requests and playback use an isolated HTTP channel with dynamic domains, redirects and Range requests, without account cookies.",
        "Fixed LX Music scripts failing from cross-thread QuickJS calls: script sessions stay serial on their owning thread and network waits cover the default HTTP timeout.",
        "Experimental USB exclusive output now covers full-speed/high-speed UAC1 with 16-32-bit PCM descriptors and per-microframe packet scheduling; pause keeps the buffer and seeking flushes stale audio. UAC2 and asynchronous feedback remain unsupported.",
        "Failed USB attempts can attach audio descriptors, speed results and the failure reason (no serial strings) to exported logs for DAC compatibility diagnosis.",
        "Hardened USB exclusivity against AutoMix and LAN cast: crossfades no longer cut the DAC, and restoring local output cannot cause speaker and DAC double output.",
        "Hardened startup: account-encryption migration failures no longer throw, playback-controller/volume-listening errors are isolated, and optional Lyricon/Xiaomi failures no longer reach the main thread.",
        "Fixed legacy cold starts: Android 10/EMUI use the compatible output panel and Bluetooth settings, with Android 14+ routing and foreground-service types version-gated.",
        "Qishui session storage uses recoverable encrypted storage that degrades to signed-out instead of blocking startup; QR sign-in keeps the full cookie and fixes the post-scan 404.",
        "Qishui QR sign-in now builds the live scan_login QR and asks for the Qishui app; the upstream legacy QR pointed at the retired sdk-next page, which made Douyin report \"Unable to access page\".",
        "Fixed the blank YouTube Music sign-in page: destroying a still-loading WebView could crash the shared renderer; it is now released with the view, and network/VPN failures show a retry action.",
        "Completed Simplified Chinese, Traditional Chinese and Japanese translations, including the Qishui QR and YouTube sign-in strings.",
        "Added an optional QQ Music bracket-line backing vocal mode: a fully parenthesized line follows the previous lead line from its end and keeps its original end time (off by default).",
        "Cross-provider search keeps only the highest playable quality for near-identical matches while distinct versions remain listed side by side.",
        "System, floating and OEM lockscreen lyrics now share the player page's Provider loader and support every built-in source.",
        "Added in-app Liked, a MeloX-only local favourites playlist with optional source migration; love actions from any source write into it.",
        "Added Settings → Experimental → CLI mode with tools/melox_cli.py for ADB playback control.",
        "Builds are split into arm64-v8a and armeabi-v7a APKs; AutoMix's flowing background now blends both covers.",
    )
}
