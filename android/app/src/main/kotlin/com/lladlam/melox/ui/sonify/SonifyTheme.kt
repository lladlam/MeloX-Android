/*
 * Sonify UI foundation (GPL-3.0), adapted for MeloX.
 *
 * Source: Sonify app/src/main/java/yos/music/player/ui/theme/Color.kt and
 * YosRoundedCornerShape.kt (master @ 209e1aa).
 *
 * The original reads its own SettingsLibrary.CustomTheme. MeloX owns theme
 * selection in MeloXSettingsRuntime, so withNight() follows MeloX's current
 * dark-theme decision. Keeping the infix shape lets the imported page code
 * stay close to the upstream source.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import kotlin.math.sqrt

// Sonify palette.
val SonifyPrimary = Color(0xFFF54047)
val SonifyPrimaryDark = Color(0xFFE64366)
val SonifyHeadline = Color(0xFF88878E)
val SonifyHeadlineDark = Color(0xFF8E8D93)
val SonifyBackground = Color.White
val SonifyBackgroundDark = Color.Black
val SonifySettingBackground = Color(0xFFF2F1F6)
val SonifySettingBackgroundDark = Color(0xFF000002)
val SonifySettingContainer = Color.White
val SonifySettingContainerDark = Color(0xFF1C1C1E)

@Composable
infix fun Color.withNight(nightColor: Color): Color =
    if (isSonifyDark()) nightColor else this

@Composable
fun isSonifyDark(): Boolean = isMeloXDarkTheme()

/**
 * SettingsLibrary equivalent for the imported pages.
 *
 * Sonify exposes these as user preferences. The MeloX host either has a
 * matching preference (frosted glass / motion) or the feature is intrinsic to
 * the Sonify appearance, in which case it stays enabled for the Sonify page
 * family so the imported UI renders as designed.
 */
object SonifySettings {
    /** Sonify's titlebar glass material. MeloX's frosted-glass switch is used
     *  when available; Sonify's own default is the glass material, so pages
     *  keep that look out of the box. */
    val BarBlurEffect: Boolean
        get() = true

    /** Library hub local-music section. MeloX always exposes the local hub
     *  entry when the provider supplies one. */
    val ShowLocalLibrary: Boolean
        get() = true

    /** Used by BitmapResolver to pick a larger palette source. MeloX's
     *  high-resolution artwork pipeline covers both cases. */
    val NowplayingBackgroundEffect: Boolean
        get() = MeloXSettingsRuntime.frostedGlassEnabled

    /** Sonify's dynamic page gradient. MeloX does not expose an equivalent
     *  preference, so it remains off; PageGradient is still fully wired for a
     *  future MeloX layer to provide LocalTrackGradientSource. */
    val TrackBackdropGradient: Boolean
        get() = false

    val ReduceMotion: Boolean
        get() = MeloXSettingsRuntime.reduceMotion
}

/**
 * Continuous-corner rounded rectangle from Sonify / Kyant. Reproduced here so
 * the imported Sonify cards keep their exact material-compatible outline.
 */
fun SonifyRoundedCornerShape(
    roundSize: Dp,
    n: Float = 0.5f,
    mSize: Size? = null,
) = SonifyRoundedCornerShape(CornerSize(roundSize), n, mSize)

fun SonifyRoundedCornerShape(
    corner: CornerSize,
    n: Float = 0.5f,
    mSize: Size? = null,
) = SonifyRoundedCornerShape(corner, corner, corner, corner, n, mSize)

fun SonifyRoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    n: Float = 0.5f,
    mSize: Size? = null,
) = SonifyRoundedCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart),
    n = n,
    mSize = mSize,
)

@Stable
class SonifyRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val n: Float = 0.5f,
    private val mSize: Size? = null,
) : CornerBasedShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomStart = bottomStart,
    bottomEnd = bottomEnd,
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ) = if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
        Outline.Rectangle(size.toRect())
    } else {
        Outline.Generic(
            Path().apply {
                val width = mSize?.width ?: size.width
                val height = mSize?.height ?: size.height
                val c1 = calcCoordinates(topStart)
                val c2 = calcCoordinates(bottomStart)
                val c3 = calcCoordinates(bottomEnd)
                val c4 = calcCoordinates(topEnd)
                val aa1 = c1[0]; val bb1 = c1[1]; val cc1 = c1[2]; val dd1 = c1[3]
                val ee1 = c1[4]; val ff1 = c1[5]; val gg1 = c1[6]
                val aa2 = c2[0]; val bb2 = c2[1]; val cc2 = c2[2]; val dd2 = c2[3]
                val ee2 = c2[4]; val ff2 = c2[5]; val gg2 = c2[6]
                val aa3 = c3[0]; val bb3 = c3[1]; val cc3 = c3[2]; val dd3 = c3[3]
                val ee3 = c3[4]; val ff3 = c3[5]; val gg3 = c3[6]
                val aa4 = c4[0]; val bb4 = c4[1]; val cc4 = c4[2]; val dd4 = c4[3]
                val ee4 = c4[4]; val ff4 = c4[5]; val gg4 = c4[6]

                moveTo(aa1, 0f)
                if (topStart != 0f) {
                    cubicTo(bb1, 0f, cc1, 0f, dd1, ee1)
                    if (n != 1f) {
                        cubicTo(gg1, ff1, ff1, gg1, ee1, dd1)
                    }
                    cubicTo(0f, cc1, 0f, bb1, 0f, aa1)
                }
                lineTo(0f, height - aa2)
                if (bottomStart != 0f) {
                    cubicTo(0f, height - bb2, 0f, height - cc2, ee2, height - dd2)
                    if (n != 1f) {
                        cubicTo(ff2, height - gg2, gg2, height - ff2, dd2, height - ee2)
                    }
                    cubicTo(cc2, height, bb2, height, aa2, height)
                }
                lineTo(width - aa3, height)
                if (bottomEnd != 0f) {
                    cubicTo(width - bb3, height, width - cc3, height, width - dd3, height - ee3)
                    if (n != 1f) {
                        cubicTo(width - gg3, height - ff3, width - ff3, height - gg3, width - ee3, height - dd3)
                    }
                    cubicTo(width, height - cc3, width, height - bb3, width, height - aa3)
                }
                lineTo(width, aa4)
                if (topEnd != 0f) {
                    cubicTo(width, bb4, width, cc4, width - ee4, dd4)
                    if (n != 1f) {
                        cubicTo(width - ff4, gg4, width - gg4, ff4, width - dd4, ee4)
                    }
                    cubicTo(width - cc4, 0f, width - bb4, 0f, width - aa4, 0f)
                }
                lineTo(aa1, 0f)
                close()
            }
        )
    }

    /** 1/4 superellipse control points, ported verbatim from Sonify. */
    private fun calcCoordinates(rad: Float): FloatArray {
        val x = n * rad * 2f / 3f
        val sq = sqrt(1f + n * n)
        val p1 = sq * x
        val p2 = n * x / sq
        val p3 = p2 * n
        val p4 = p2 + rad / sq
        val p5 = p4 * (1f - n)
        val p7 = p5 + p1 * 4f
        val p8 = p5 + p1 * 2f
        val p9 = p5 + p1
        val p10 = p5 + p3
        val dx = rad / sq * (1f - n)
        val d = sqrt(rad * rad - dx * dx / 2f)
        val handle = (rad - d) / dx * sqrt(2f) * 4f / 3f
        val p11 = rad / sq * n
        val p12 = rad / sq
        val p13 = p2 + p11 * handle
        val p14 = p10 - p12 * handle
        return floatArrayOf(p7, p8, p9, p10, p2, p13, p14)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ) = SonifyRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart, n, mSize)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SonifyRoundedCornerShape) return false
        if (topStart != other.topStart) return false
        if (topEnd != other.topEnd) return false
        if (bottomEnd != other.bottomEnd) return false
        if (bottomStart != other.bottomStart) return false
        if (n != other.n) return false
        return mSize == other.mSize
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + n.hashCode()
        result = 31 * result + (mSize?.hashCode() ?: 0)
        return result
    }
}
