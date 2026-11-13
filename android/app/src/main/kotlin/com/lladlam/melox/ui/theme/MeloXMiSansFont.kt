package com.lladlam.melox.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

/**
 * The app-wide family. The system family is the default everywhere; MiSans is
 * only used when the user explicitly opts in and the font was provisioned by
 * [MeloXOnDemandFont].
 */
val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    val useSystemFont = com.lladlam.melox.ui.settings.MeloXSettingsRuntime.systemFontEnabled
    val revision = MeloXOnDemandFont.revision
    return remember(context, useSystemFont, revision) {
        if (useSystemFont) FontFamily.Default else MeloXOnDemandFont.fontFamily(context) ?: FontFamily.Default
    }
}
