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
        "全新 Apple Music 外观：首页、音乐库、发现、歌手页与二级页面完成迁移，保留液态玻璃与页面转场。",
        "音乐库改为在线优先、本地音乐只显示已下载内容；新增下载进度页、Apple 风格进度条、弥乐「我喜欢」与批量下载。",
        "发现页改为 Apple Music 编辑流：每个分类一张整宽大卡，标题可进入查看更多。",
        "专辑发行日期、收藏专辑、专辑/歌单统一使用 MeloX 自己的详情页。",
        "新增首次启动外观选择页，可随时在设置中切换；暗色模式页面背景统一为纯黑。",
        "歌词识别增强：B站标题拆解、音频文件随机片段识别、识别 ID 持久化，以及录屏授权后的系统音频识别。",
        "新增听歌识曲桌面快捷方式、音量条显示开关、标签栏排序对齐修复与默认启动页修复。",
        "播放器标题/更多菜单：进入歌手、专辑、百科前先收回播放器，底部 Dock 与迷你播放器保持可见。",
        "日文翻译按 Gramophone 风格精简，减少窄屏截断与布局错位。",
        "修复私信页空值崩溃、网易云收藏专辑加载、回归若干滚动/返回手势问题。",
    )

    private val englishEntries = listOf(
        "New Apple Music appearance: Home, Library, Explore, Artist and secondary pages now follow the migrated Apple Music UI with liquid glass and page transitions.",
        "Library is online-first; Local Music only contains downloaded tracks. Added download progress, Apple-style progress bars, MeloX Favorites and batch downloads.",
        "Explore is now an Apple Music-style editorial feed with full-width cards per category and a See More grid.",
        "Album release dates, saved albums, and unified MeloX detail pages for albums and playlists.",
        "Added the first-run appearance picker, switchable later in Settings; dark page backgrounds are now pure black.",
        "Better lyric lookup: Bilibili title cleanup, random audio-file fingerprint segments, persisted recognition IDs, and optional MediaProjection system-audio recognition.",
        "Added a recognition launcher shortcut, volume-slider toggle, tab-order alignment fix and default launch-page fix.",
        "Player title/more actions now collapse the player before artist, album and wiki pages; dock and mini player stay visible during transitions.",
        "Japanese copy shortened and aligned with Gramophone terminology to avoid narrow-screen truncation.",
        "Fixed the Messages null crash, NetEase saved-album loading, and several scroll/back-gesture regressions.",
    )
}
