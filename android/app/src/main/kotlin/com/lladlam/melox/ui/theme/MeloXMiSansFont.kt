package com.lladlam.melox.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lladlam.melox.R

val MeloXLanTingProFontFamily = FontFamily(
    // The XML family maps standard Android weights to this font's private
    // 150..700 axis coordinates. Compose can then use normal weight matching.
    Font(R.font.mi_lan_pro, weight = FontWeight.Normal),
)

val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { MeloXLanTingProFontFamily }

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    return remember(context) {
        MeloXLanTingProFontFamily
    }
}
