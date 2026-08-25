package com.lladlam.melox.core.audio

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Android mirror of MeloX/Core/Settings/Playback/MusicQuality.swift. */
enum class MusicQuality(
    val apiLevel: String,
    val title: String,
) {
    Standard("standard", "标准"),
    High("exhigh", "高品质"),
    Lossless("lossless", "无损"),
    HiResolution("hires", "Hi-Res"),
    HighDefinitionSurround("jyeffect", "高清环绕声"),
    ImmersiveSurround("sky", "沉浸环绕声"),
    UltraClearMaster("jymaster", "超清母带");

    val requiresImmersiveType: Boolean
        get() = this == ImmersiveSurround

    val playbackFallbacks: List<MusicQuality>
        get() = when (this) {
            Standard -> listOf(Standard)
            High -> listOf(High, Standard)
            Lossless -> listOf(Lossless, High, Standard)
            HiResolution -> listOf(HiResolution, Lossless, High, Standard)
            HighDefinitionSurround -> listOf(HighDefinitionSurround, Lossless, High, Standard)
            ImmersiveSurround -> listOf(
                ImmersiveSurround,
                HighDefinitionSurround,
                Lossless,
                High,
                Standard,
            )
            UltraClearMaster -> listOf(UltraClearMaster, HiResolution, Lossless, High, Standard)
        }

    fun playbackCandidates(availability: SongAudioAvailability): List<MusicQuality> =
        playbackFallbacks.filter { availability.supports(it.apiLevel) != false }

    companion object {
        fun fromApiLevel(level: String?): MusicQuality? =
            entries.firstOrNull { it.apiLevel == level }
    }
}

data class SongAudioResource(
    val bitrate: Int?,
    val sampleRate: Int?,
    val size: Long?,
)

data class SongAudioAvailability(
    val standard: SongAudioResource? = null,
    val medium: SongAudioResource? = null,
    val high: SongAudioResource? = null,
    val lossless: SongAudioResource? = null,
    val hiResolution: SongAudioResource? = null,
    val highDefinitionSurround: SongAudioResource? = null,
    val immersiveSurround: SongAudioResource? = null,
    val ultraClearMaster: SongAudioResource? = null,
    val isKnown: Boolean = false,
) {
    fun supports(apiLevel: String): Boolean? {
        if (!isKnown) return null
        return when (apiLevel) {
            "standard" -> standard != null
            "exhigh" -> high != null
            "lossless" -> lossless != null
            "hires" -> hiResolution != null
            "jyeffect" -> highDefinitionSurround != null
            "sky" -> immersiveSurround != null
            "jymaster" -> ultraClearMaster != null
            else -> false
        }
    }

    companion object {
        val Unknown = SongAudioAvailability()
    }
}

object MusicQualityPreferences {
    private const val PREFERENCES_NAME = "melox_playback"
    private const val KEY_QUALITY = "music_quality"

    fun read(context: Context): MusicQuality {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, null)
        return MusicQuality.fromApiLevel(raw) ?: MusicQuality.Standard
    }

    fun write(context: Context, quality: MusicQuality) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY, quality.apiLevel)
            .apply()
        MusicQualityRuntime.selected = quality
    }
}

/**
 * Small process-local bridge between the Media3 resolver and Compose UI.
 * The resolver records the server-returned `level`, so the chip can display
 * the actual quality after MeloX-style fallback instead of the requested label.
 */
object MusicQualityRuntime {
    private data class QualityRecord(
        val requested: MusicQuality,
        val actual: MusicQuality,
    )

    @Volatile
    var selected: MusicQuality = MusicQuality.Standard

    private val actualBySong = ConcurrentHashMap<Long, QualityRecord>()

    fun recordActual(songId: Long, requested: MusicQuality, actual: MusicQuality) {
        // Background analysis can resolve the same song at Standard while the
        // foreground decoder keeps playing Hi-Res. Never let that secondary
        // request replace the quality reported for the user's active selection.
        if (requested == selected) {
            actualBySong[songId] = QualityRecord(requested, actual)
        }
    }

    fun actualFor(songId: Long?): MusicQuality? =
        songId
            ?.let(actualBySong::get)
            ?.takeIf { it.requested == selected }
            ?.actual

    fun clear(songId: Long? = null) {
        if (songId == null) actualBySong.clear() else actualBySong.remove(songId)
    }
}
