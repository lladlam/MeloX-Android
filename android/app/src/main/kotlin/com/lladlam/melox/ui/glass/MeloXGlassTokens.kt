package com.lladlam.melox.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import com.lladlam.melox.ui.theme.MeloXLanTingProFontFamily
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

/** The two Liquid Glass variants Apple exposes for custom components. */
enum class MeloXGlassMaterial {
    Clear,
    Regular,
}

/** Semantic colors used by the iOS Native Components reference. */
object MeloXSystemColors {
    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF3B30)
    val SecondaryFill = Color(0x26787880)
    val TertiaryFill = Color(0x1F767680)
    val Separator = Color(0x4A3C3C43)
}

/** Shared geometry for the iOS-style surfaces used outside the player. */
object MeloXShapes {
    // Use the same continuous/capsule geometry exposed by the public Kyant
    // library that Mei uses. RoundedCornerShape remains only where iOS needs
    // asymmetric corners (the bottom sheet).
    val capsule: Shape = Capsule()
    val compact: Shape = ContinuousRoundedRectangle(16.dp)
    val card: Shape = ContinuousRoundedRectangle(22.dp)
    val largeCard: Shape = ContinuousRoundedRectangle(28.dp)
    val sheet: Shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val circle: Shape = CircleShape
}

/** iOS-derived type sizes while retaining Android's system/CJK fallback font. */
object MeloXTypography {
    private val family = MeloXLanTingProFontFamily
    val largeTitle = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp)
    val title2 = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val headline = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val subheadline = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
}

enum class MeloXGlassButtonStyle {
    Bordered,
    BorderedProminent,
    Plain,
    Destructive,
}

data class MeloXGlassSpec(
    val blurRadius: Dp,
    val lensRadius: Dp,
    val refractionHeight: Dp,
    /** Regular is the opaque-enough control material; Clear is reserved for rich media. */
    val useLens: Boolean,
) {
    companion object {
        fun forMaterial(material: MeloXGlassMaterial): MeloXGlassSpec = when (material) {
            // Keep the same optical envelope as Mei: low blur, visible lens,
            // and a shallow refraction depth. The distinction between Clear
            // and Regular is carried by the tint, not by disabling refraction.
            MeloXGlassMaterial.Clear -> MeloXGlassSpec(2.dp, 24.dp, 12.dp, useLens = true)
            MeloXGlassMaterial.Regular -> MeloXGlassSpec(2.dp, 24.dp, 12.dp, useLens = true)
        }
    }
}
