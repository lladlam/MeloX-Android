package com.lladlam.melox.ui.player

import androidx.compose.ui.graphics.Color

/**
 * Survives composition teardown of the now-playing shell.
 *
 * The player background used to live entirely in `remember` state inside
 * `MeloXIOSNowPlayingSharedHost`. Collapsing to the mini player removes that
 * subtree, so every reopen restarted from the fallback palette and an empty
 * mesh — which is the visible "re-render" hitch. This store keeps the computed
 * visual for the current track keyed by the parameters that affect pixels.
 *
 * Scope is intentionally process-local: it does not survive process death.
 * A stale frame would also be wrong after an artwork change, so a key mismatch
 * simply starts a fresh entry.
 *
 * Threading: entries are mutated from the backdrop's render coroutine and read
 * from the composition. All access goes through [lock]; pixel arrays are
 * copied on publish/consume and never handed out for mutation.
 */
internal object MeloXFlowingLightVisualStore {
    class Snapshot(
        val palette: ArtworkDynamicPalette,
        val colors: List<Color>,
        val average: Color,
        val phase: Float,
        val energy: Float,
        val beatPulse: Float,
        val pixels: IntArray?,
        val meshWidth: Int,
        val meshHeight: Int,
    )

    private val lock = Any()
    private var key: String? = null
    private var entry: Snapshot? = null

    private fun buildKey(
        artworkUrl: String?,
        mixOutgoingArtworkUrl: String?,
        mixIncomingArtworkUrl: String?,
        meshWidth: Int,
        meshHeight: Int,
        saturation: Float,
        brightness: Float,
        reduceMotion: Boolean,
    ): String = buildString {
        append(artworkUrl.orEmpty())
        append('|').append(mixOutgoingArtworkUrl.orEmpty())
        append('|').append(mixIncomingArtworkUrl.orEmpty())
        append('|').append(meshWidth).append('x').append(meshHeight)
        append('|').append(saturation.toString())
        append('|').append(brightness.toString())
        append('|').append(reduceMotion)
    }

    /** Returns the retained frame for [key], or null when it does not match. */
    fun take(
        artworkUrl: String?,
        mixOutgoingArtworkUrl: String?,
        mixIncomingArtworkUrl: String?,
        meshWidth: Int,
        meshHeight: Int,
        saturation: Float,
        brightness: Float,
        reduceMotion: Boolean,
    ): Snapshot? {
        val requested = buildKey(
            artworkUrl, mixOutgoingArtworkUrl, mixIncomingArtworkUrl,
            meshWidth, meshHeight, saturation, brightness, reduceMotion,
        )
        return synchronized(lock) {
            if (requested != key) return@synchronized null
            entry?.let { value ->
                Snapshot(
                    palette = value.palette,
                    colors = value.colors.toList(),
                    average = value.average,
                    phase = value.phase,
                    energy = value.energy,
                    beatPulse = value.beatPulse,
                    pixels = value.pixels?.copyOf(),
                    meshWidth = value.meshWidth,
                    meshHeight = value.meshHeight,
                )
            }
        }
    }

    /** Publishes the latest computed visual for the current parameters. */
    fun publish(
        artworkUrl: String?,
        mixOutgoingArtworkUrl: String?,
        mixIncomingArtworkUrl: String?,
        meshWidth: Int,
        meshHeight: Int,
        saturation: Float,
        brightness: Float,
        reduceMotion: Boolean,
        palette: ArtworkDynamicPalette,
        colors: List<Color>,
        average: Color,
        phase: Float,
        energy: Float,
        beatPulse: Float,
        pixels: IntArray?,
    ) {
        val updatedKey = buildKey(
            artworkUrl, mixOutgoingArtworkUrl, mixIncomingArtworkUrl,
            meshWidth, meshHeight, saturation, brightness, reduceMotion,
        )
        synchronized(lock) {
            key = updatedKey
            entry = Snapshot(
                palette = palette,
                colors = colors.toList(),
                average = average,
                phase = phase,
                energy = energy,
                beatPulse = beatPulse,
                pixels = pixels?.copyOf(),
                meshWidth = meshWidth,
                meshHeight = meshHeight,
            )
        }
    }

    /** Drops the frame when the track/artwork identity changes or on logout. */
    fun clear() = synchronized(lock) {
        key = null
        entry = null
    }
}
