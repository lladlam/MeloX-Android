package com.lladlam.melox.ui.sonify.artist.liquid

import com.lladlam.melox.ui.sonify.SonifySettings

/**
 * 玻璃模糊强度倍率（0f..2f，1f = 原样）。
 *
 * MeloX 没有 Sonify 的「玻璃效果调节」滑块；Sonify 页面保持原版材质参数，
 * 因此恒为 1f。保留这个入口，后续若 MeloX 增加玻璃强度设置可在此统一接线。
 */
val glassBlurScale: Float
    get() = 1f

/** 混色模糊（磨砂）强度倍率，当前与玻璃倍率同源。 */
val frostedBlurScale: Float
    get() = 1f

fun activeBlurScale(liquid: Boolean = SonifySettings.BarBlurEffect): Float =
    (if (liquid) glassBlurScale else frostedBlurScale).coerceIn(0f, 2f)
