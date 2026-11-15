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
        "修复网易云歌曲库取数上限：歌曲详情每批最多 100 首，之前误设 250 会静默丢歌，导致歌单缺歌、收藏补漏变慢甚至超时。",
        "修复「添加到歌单」后歌曲消失：现在会校验服务端内层结果，写入失败会明确报错，不再静默丢弃。",
        "统一收藏开启时红心会同步写回网易云，避免刷新后被服务端真值覆盖。",
        "歌词获取统一为一条线路：主歌词与系统语言一致时不显示翻译，中文简繁只做字形转换，语言不同才使用且校验翻译语言。",
        "新增跨源歌词合并：主歌词缺翻译时从其它已完成的源借用翻译轨道，逐字源优先，时间轴对不上则整份弃用。",
        "修复 QQ 逐字歌词经常拿不到：首屏等待 5 秒，并在 12 秒内支持晚到逐字就地升级；缓存只有逐行时后台重新解析。",
        "伴唱歌词回退到 0.7.2 的亮度行为。",
        "流光背景改用 GPU（AGSL）渲染，保留并预热已渲染帧，收起再展开不再重新取色和重算首帧。",
        "自动混音新增背景增强开关：饱和度与速度按真实过渡进度升到 2.00x 再回落。",
        "「播放器展开 / 收回时长」移到「外观 → 播放器外观」，并单独成为「展开 / 收回动画」分类。",
        "网络与账号加固：只读接口恢复重试、写请求禁止重放、退出音源不再清空其它 Cookie、资料刷新按 Cookie 校验。",
        "新增赞助者：清风与黑糖C（CNY 18）。",
    )

    private val englishEntries = listOf(
        "Fixed the NetEase song-detail fetch cap: at most 100 songs per request, so the previous 250-song batches no longer silently drop tracks (which made playlists incomplete and favorites slow or timed out).",
        "Fixed songs disappearing after adding them to a playlist: the nested server result is now validated and failures surface as errors instead of being silently ignored.",
        "With unified favorites enabled, the heart button also writes back to NetEase so a refresh cannot overwrite it.",
        "Lyric fetching is one pipeline for every source: no translation when the main lyric already matches the listener's language, script conversion only for Simplified/Traditional Chinese, and a language-checked translation otherwise.",
        "Added cross-source lyric merging: a translation track from another finished source is merged when the timelines align, with word-timed sources preferred and misaligned tracks discarded.",
        "Fixed missing QQ word-level lyrics: 5 s first-paint window plus a 12 s late-upgrade path, and a line-timed cache hit now re-resolves in the background.",
        "Backing-vocal brightness restored to the 0.7.2 behaviour.",
        "The flowing-light background now renders on the GPU via AGSL and retains/prewarms frames, so reopening the player no longer resamples colors or rebuilds the first frame.",
        "New AutoMix background boost: saturation and speed ramp to 2.00x following the real transition progress, then ease back.",
        "Player expand/collapse duration moved to Appearance → Player appearance as its own Expand/collapse animation section.",
        "Hardened network and accounts: retries for read-only endpoints, no replayed writes, per-source cookie isolation and cookie-checked profile refresh.",
        "New sponsor: 清风与黑糖C (CNY 18).",
    )
}
