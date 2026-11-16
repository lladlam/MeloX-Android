package com.lladlam.melox.ui.sonify

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

/** Raw artwork color and gentle RGB variants; no theme-driven HSV clipping. */
@Immutable
data class ArtistArtworkColors(
    val accent: Color,
    val bright: Color,
    val dark: Color
) {
    val content: Color
        get() = if (accent.luminance() > 0.179f) Color(0xFF161616) else Color.White

    companion object {
        fun fromRgb(rgb: Int?, isDark: Boolean): ArtistArtworkColors {
            val accent = rgb?.let(::Color)
                ?: if (isDark) Color(0xFF1B1B1D) else Color(0xFFF2F2F4)
            return ArtistArtworkColors(
                accent = accent,
                bright = lerp(accent, Color.White, 0.16f),
                dark = lerp(accent, Color.Black, 0.16f)
            )
        }
    }
}

/**
 * 无照片歌手的稳定身份派生色。酷狗对未上传头像的歌手返回空 url，取色链路整条失效，
 * 页面退化为主题兜底色（见 fromRgb）——这就是「歌手页白板、且没有背景色」的根因。
 * 以艺人稳定标识（优先 artistId，退化为名字）派生一个受控色相的实色冒充「提取色」，
 * 喂回既有调色板链路，使整页背景 / 文字色 / 底栏遮罩 / 子页面配色全部自动生效。
 *
 * 同一 seed 恒得同色（String.hashCode 由 Java 规范固定，跨进程/重启稳定）；相邻数值
 * artistId 的哈希步长恒定，色相天然散开不聚集。明度刻意压在中低区间：hero 歌手名与
 * 各文字层在白底上会发淡，且 ArtistArtworkColors.content 因此判定为白字，与既有观感一致；
 * 深色模式再压一档，保证同一歌手在深浅主题下同色相、深浅分明。
 */
internal fun artistIdentityRgb(seed: String, isDark: Boolean): Int {
    val h = seed.hashCode()
    val hue = (((h % 360) + 360) % 360).toFloat()
    val saturation = 0.34f + ((h ushr 8) % 18) / 100f
    val value = if (isDark) 0.30f + ((h ushr 16) % 10) / 100f
                else 0.44f + ((h ushr 16) % 10) / 100f
    return hsvToArgb(hue, saturation, value)
}

/** 纯 Kotlin HSV→ARGB；不依赖 android.graphics，供本地 JVM 单测直接调用。 */
private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val sector = h.toInt()
    val f = h - sector
    val p = value * (1f - saturation)
    val q = value * (1f - saturation * f)
    val t = value * (1f - saturation * (1f - f))
    val (r, g, b) = when (sector) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return (0xFF shl 24) or
        ((r * 255f).roundToInt() shl 16) or
        ((g * 255f).roundToInt() shl 8) or
        (b * 255f).roundToInt()
}
