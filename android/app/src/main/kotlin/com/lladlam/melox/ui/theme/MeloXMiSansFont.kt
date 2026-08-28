package com.lladlam.melox.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.lladlam.melox.R

// Keep the original device/system font chain. Xiaomi devices provide LanTing Pro
// through the system sans family, including its native weight handling and CJK fallback.
val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    return remember(context) {
        FontFamily.SansSerif
    }
}
