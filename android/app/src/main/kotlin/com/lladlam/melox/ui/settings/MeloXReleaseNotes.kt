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
        "新增实验性插件音源（Plugin API v1）：导入时校验包、版本、签名与权限，插件默认禁用；独立 QuickJS 运行时与账号隔离存储，贯通搜索、播放、歌词、歌单、专辑、歌手、榜单、首页、资料库、收藏、下载与认证。当前为 RC 工程预览，API 24、完整 UI/OAuth/后台与真机验收仍未完成。",
        "新增动态音源的原生入口：插件出现在来源筛选、首页、资料库、搜索结果与歌曲菜单中，并提供 SDK、示例与开发文档。",
        "后台页面恢复覆盖首页/发现的歌单与榜单、搜索结果与详情、音乐库子页、播客、下载、云盘、私信与全屏播放器；Activity 被销毁重建后保留原页面、滚动位置与已加载内容。",
        "修复每日推荐/热榜第二次打开或重建后只剩封面和歌曲数、列表空白：已传入歌曲不再被“本进程只刷新一次”的网络闸门拦截。",
        "统一新旧音源选择记录，避免返回前台时意外切源并清空当前页。",
        "首页快捷入口改为每页两张半宽卡片，左侧留白、右侧保留 44dp 露出并横向磁吸翻页；移除卡片矩形按压高光，统一为圆角柔和阴影。",
        "存储设置新增播放缓存上限（256MB/512MB/1GB/2GB），按 LRU 清理最旧缓存，下次启动生效。",
        "歌单详情先显示摘要和前 100 首，剩余歌曲按 100 首并行加载；打开中的网易云歌单在进程回收后 30 分钟内自动恢复。",
        "一起听新增单人模式：房间保持在线，本机可独立切歌、暂停和拖动进度，不向对方发送指令；关闭后同步回房间当前播放。",
        "新增 QQ 音乐括号歌词识别为伴唱（默认关闭）：整行括号内容挂到上一句，从主歌词结束时开始并保留原结束时间。",
        "播放器支持点按封面切换歌词页、左右滑动封面切歌；歌词跳转后重新锚定，上一首遵循 5 秒设置；改善部分 Qualcomm 设备 FLAC 解码回退。",
        "修复 YouTube Music 登录白屏：WebView 改为在 onRelease 中销毁，并补充网络/VPN 错误提示与重试入口。",
        "修复汽水音乐扫码登录：改用新版 scan_login 二维码，并提示使用汽水音乐 App 扫码。",
        "补齐简体中文、繁体中文、日文翻译；土耳其语缺失条目回退英文。",
        "设置 → 测试功能尝鲜新增实验性 MeloX Rear 副屏伴侣入口；该独立应用来自上游 YTRear，不随本次 APK 分发，许可证与真机兼容性仍待确认。",
    )

    private val englishEntries = listOf(
        "Added experimental Plugin API v1 sources: package, version, signature and permission checks on import, disabled by default, with an isolated QuickJS runtime and account-scoped storage across search, playback, lyrics, playlists, albums, artists, rankings, home, library, favorites, download and authentication. This is an RC engineering preview; API 24, complete UI/OAuth/background flows and device acceptance remain pending.",
        "Dynamic sources now appear in source filters, home, library, search results and song menus, with an SDK, examples and developer documentation.",
        "Background restoration now covers home/explore collections and charts, search results and details, library subpages, podcasts, downloads, cloud drive, messages and the full-screen player, retaining the page, scroll position and loaded content after Activity destruction.",
        "Fixed daily recommendations/charts reopening or rebuilding with only the cover and count: supplied songs are no longer blocked by the process-wide refresh gate.",
        "Unified old and new source-selection records so returning to the foreground cannot silently switch providers and clear the current page.",
        "Home quick actions now use two half-width cards per page, left-aligned with a 44dp peek and magnetic paging; removed the rectangular press ripple and unified rounded soft shadows.",
        "Added a playback cache limit (256MB/512MB/1GB/2GB) with LRU eviction, applied on the next launch.",
        "Playlist details show the summary and first 100 tracks immediately, then load the rest in parallel batches; an open NetEase playlist restores within 30 minutes after process reclamation.",
        "Listen Together gains solo mode: the room stays online while local play/pause/skip/seek sends no commands to the other listener; disabling solo syncs back to the room.",
        "Added optional QQ Music bracket-line backing vocals: a fully parenthesized line follows the previous lead line from its end while keeping its original end time.",
        "Tap artwork for lyrics and swipe it to change tracks; lyrics re-anchor after seeks, Previous follows the 5-second setting, and FLAC fallback is improved on some Qualcomm devices.",
        "Fixed the blank YouTube Music sign-in page by releasing the WebView in onRelease, with network/VPN errors and a retry action.",
        "Fixed Qishui QR sign-in with the live scan_login QR and a prompt to scan using the Qishui app.",
        "Completed Simplified Chinese, Traditional Chinese and Japanese translations; Turkish falls back to English where missing.",
        "Added an experimental MeloX Rear companion entry under Settings → Experimental; the separate YTRear-derived app is not distributed with this APK, and its license and device compatibility remain pending.",
    )
}
