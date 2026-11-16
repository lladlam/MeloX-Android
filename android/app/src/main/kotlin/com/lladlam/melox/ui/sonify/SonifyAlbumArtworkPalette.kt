/*
 * Sonify album/playlist artwork palette (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/navigation/AlbumArtworkPalette.kt (master @
 * 209e1aa). Coil2 calls were migrated to Coil3; the sampling maths and
 * contrast rules are unchanged.
 */
package com.lladlam.melox.ui.sonify

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal fun albumPaletteKey(albumId: String): String =
    "album-cover:$albumId#square-avg-v1-128"

internal fun playlistPaletteKey(source: String, playlistId: String): String =
    "playlist-cover:$source:$playlistId#square-avg-v1-128"

internal fun sampleAlbumCoverColor(bitmap: Bitmap): Int? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val insetX = (width * 0.04f).toInt()
    val insetY = (height * 0.04f).toInt()
    val left = insetX
    val top = insetY
    val right = (width - insetX).coerceAtLeast(left + 1)
    val bottom = (height - insetY).coerceAtLeast(top + 1)

    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var total = 0.0
    for (y in top until bottom) {
        for (x in left until right) {
            val pixel = bitmap.getPixel(x, y)
            val weight = AndroidColor.alpha(pixel) / 255.0
            if (weight <= 0.0) continue
            red += AndroidColor.red(pixel) * weight
            green += AndroidColor.green(pixel) * weight
            blue += AndroidColor.blue(pixel) * weight
            total += weight
        }
    }
    if (total <= 0.0) return null
    return AndroidColor.rgb(
        (red / total).roundToInt(),
        (green / total).roundToInt(),
        (blue / total).roundToInt(),
    )
}

internal fun artworkContentColor(accent: Color): Color {
    val l = accent.luminance()
    val whiteContrast = 1.05f / (l + 0.05f)
    val darkContrast = (l + 0.05f) / (0.0075f + 0.05f)
    return if (whiteContrast >= darkContrast) Color.White else Color(0xFF161616)
}

@Composable
internal fun rememberAlbumBackdropColors(albumId: String, coverUrl: String?): ArtistArtworkColors? {
    if (albumId.isBlank()) return null
    return rememberCoverBackdropColors(
        paletteKey = remember(albumId) { albumPaletteKey(albumId) },
        memoryCacheId = "album-palette:$albumId",
        coverUrl = coverUrl,
    )
}

@Composable
internal fun rememberPlaylistBackdropColors(
    source: String,
    playlistId: String,
    coverUrl: String?,
): ArtistArtworkColors? {
    if (playlistId.isBlank()) return null
    return rememberCoverBackdropColors(
        paletteKey = remember(source, playlistId) { playlistPaletteKey(source, playlistId) },
        memoryCacheId = "playlist-palette:$source:$playlistId",
        coverUrl = coverUrl,
    )
}

@Composable
private fun rememberCoverBackdropColors(
    paletteKey: String,
    memoryCacheId: String,
    coverUrl: String?,
): ArtistArtworkColors? {
    val context = LocalContext.current
    val isDark = isSonifyDark()
    val paletteState = remember(paletteKey) { ArtistPresentationCache.palette(paletteKey) }
    val rgb = paletteState.value

    LaunchedEffect(paletteKey, coverUrl) {
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        if (paletteState.value != null) return@LaunchedEffect
        val bitmap = runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(coverUrl)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .size(256)
                    .memoryCacheKey(memoryCacheId)
                    .diskCacheKey(coverUrl)
                    .build()
            ).image?.toBitmap()
        }.getOrNull() ?: return@LaunchedEffect

        val sampleSrc = if (bitmap.width > 128) Bitmap.createScaledBitmap(
            bitmap,
            128,
            (128f * bitmap.height / bitmap.width).roundToInt().coerceAtLeast(1),
            false,
        ) else bitmap

        runCatching {
            ArtistPresentationCache.loadPalette(paletteKey) {
                withContext(Dispatchers.Default) { sampleAlbumCoverColor(sampleSrc) }
            }
        }
    }

    return remember(rgb, isDark) { rgb?.let { ArtistArtworkColors.fromRgb(it, isDark) } }
}
