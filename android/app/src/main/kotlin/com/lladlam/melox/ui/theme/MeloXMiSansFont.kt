package com.lladlam.melox.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.lladlam.melox.R

val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    return remember(context) {
        runCatching {
            ResourcesCompat.getFont(context, R.font.misans_vf)?.let(::FontFamily)
        }.getOrNull() ?: FontFamily.SansSerif
    }
}
