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
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Thin),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.ExtraLight),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Light),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Normal),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Medium),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.SemiBold),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Bold),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.ExtraBold),
    Font(R.font.mi_lan_pro_vf, weight = FontWeight.Black),
)

val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { MeloXLanTingProFontFamily }

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    return remember(context) {
        MeloXLanTingProFontFamily
    }
}
