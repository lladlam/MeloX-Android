/*
 * Sonify page gradient system (GPL-3.0), adapted for MeloX.
 *
 * Upstream: yos/music/player/ui/navigation/TrackBackdrop.kt (master @
 * 209e1aa). The original samples the current MediaController artwork. MeloX
 * owns playback state in MeloXPlaybackService and does not expose a single
 * process-wide "current artwork" object to Compose, so the host supplies the
 * artwork URL through LocalSonifyArtworkUrl. When no host provides one the
 * gradient is simply null and every page falls back to its own extracted or
 * theme colour, exactly like the upstream disabled/no-artwork path.
 */
package com.lladlam.melox.ui.sonify

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.toBitmap
import com.lladlam.melox.core.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Cleared by the MeloX host when no track is playing. */
val LocalSonifyArtworkUrl = compositionLocalOf<String?> { null }

/** Called by players that want the Sonify page gradient to follow the current track. */
val LocalSonifyCurrentTrack = compositionLocalOf<MusicTrack?> { null }

object SonifyTrackBackdrop {
    private const val FADE_MS = 700
    private const val FADE_STEPS = 8
    private const val SAMPLE_SIZE = 128

    private fun cacheKey(uri: String, isDark: Boolean): String =
        "sonify-backdrop:$uri#${if (isDark) "dark" else "light"}#v1"

    @Composable
    fun rememberGradient(artworkUrl: String? = LocalSonifyArtworkUrl.current): TrackBackdropGradient? {
        if (!SonifySettings.TrackBackdropGradient) return null
        if (artworkUrl.isNullOrBlank()) return null
        val isDark = isSonifyDark()
        val context = LocalContext.current
        val key = remember(artworkUrl, isDark) { cacheKey(artworkUrl, isDark) }
        val state = remember(key) { mutableStateOf(TrackGradientCache.get(key)) }

        LaunchedEffect(key, artworkUrl) {
            if (state.value != null) return@LaunchedEffect
            val sampled = runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                            .size(SAMPLE_SIZE)
                            .build()
                    ).image?.toBitmap() ?: return@withContext null
                    val w = bitmap.width
                    val h = bitmap.height
                    if (w <= 0 || h <= 0) return@withContext null
                    val pixels = IntArray(w * h)
                    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    TrackGradientPalette.sample(pixels, w, h, isDark)
                }
            }.getOrNull() ?: return@LaunchedEffect
            TrackGradientCache.put(key, sampled)
            state.value = sampled
        }
        return state.value
    }

    @Composable
    fun rememberBlendedGradient(artworkUrl: String? = LocalSonifyArtworkUrl.current): TrackBackdropGradient? {
        val enabled = SonifySettings.TrackBackdropGradient
        val target = rememberGradient(artworkUrl)
        val current = remember { arrayOfNulls<TrackBackdropGradient>(1) }
        val from = remember(target) { current[0] }
        val progress = remember(target) {
            Animatable(if (from == null || target == null) 1f else 0f)
        }
        val stepState = remember(progress) {
            derivedStateOf { (progress.value * FADE_STEPS).roundToInt().coerceIn(0, FADE_STEPS) }
        }
        LaunchedEffect(target) {
            if (target == null) return@LaunchedEffect
            if (from != null && from != target) {
                progress.animateTo(1f, tween(FADE_MS, easing = FastOutSlowInEasing))
            }
        }
        if (!enabled) {
            current[0] = null
            return null
        }
        if (target == null) return current[0]
        val result = if (from == null || from == target) {
            target
        } else {
            val step = stepState.value
            remember(from, target, step) {
                TrackBackdropGradient.lerp(from, target, step.toFloat() / FADE_STEPS)
            }
        }
        current[0] = result
        return result
    }
}

private object TrackGradientCache {
    private val cache = object : android.util.LruCache<String, TrackBackdropGradient>(64) {}
    fun get(key: String): TrackBackdropGradient? = cache.get(key)
    fun put(key: String, value: TrackBackdropGradient) = cache.put(key, value)
}

val LocalPageGradient = compositionLocalOf<PageGradient?> { null }
val LocalTrackGradientSource = compositionLocalOf<TrackBackdropGradient?> { null }

/**
 * Draw-time holder for the current page gradient. Keeping a stable holder
 * reference means enabling/disabling the gradient never recreates the page
 * subtree or the title backdrop.
 */
class PageGradient internal constructor(
    private val state: androidx.compose.runtime.State<TrackBackdropGradient?>,
) {
    fun current(): TrackBackdropGradient? = state.value
    fun averageColor(fallback: Color): Color = current()?.average ?: fallback
}

@Composable
fun PageGradientRoot(
    entryId: String? = null,
    scrimTransition: PageScrimTransition? = null,
    content: @Composable () -> Unit,
) {
    val gradient = LocalTrackGradientSource.current
    val enabled = SonifySettings.TrackBackdropGradient
    val isDark = isSonifyDark()
    val holder = remember { mutableStateOf<TrackBackdropGradient?>(gradient) }
    holder.value = gradient
    val page = remember { PageGradient(holder) }

    DisposableEffect(enabled, isDark, entryId, scrimTransition) {
        if (!enabled || entryId == null || scrimTransition == null) {
            return@DisposableEffect onDispose { }
        }
        val themeColor = if (isDark) Color.Black else Color.White
        val scrimColor = derivedStateOf { holder.value?.bottom ?: themeColor }
        val registration = ArtistPageAppearance.register(
            entryId,
            scrimColor,
            scrimTransition,
            contributesTint = false,
        )
        onDispose { ArtistPageAppearance.unregister(entryId, registration) }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPageGradient provides page,
        content = content,
    )
}
