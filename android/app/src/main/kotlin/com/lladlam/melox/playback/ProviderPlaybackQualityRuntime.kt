package com.lladlam.melox.playback

import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import java.util.concurrent.ConcurrentHashMap

/** Process-local bridge from provider VKey resolution to the shared player UI. */
object ProviderPlaybackQualityRuntime {
    private data class QualityRecord(
        val requested: AudioQualityTier,
        val actual: AudioQualityTier,
    )

    private val actualByTrack = ConcurrentHashMap<String, QualityRecord>()

    fun recordActual(
        id: MusicResourceId,
        requested: AudioQualityTier,
        actual: AudioQualityTier,
    ) {
        // Independent background analysis resolves Standard for the same stable
        // media identity. Preserve the foreground result selected by the user.
        if (requested == MusicQualityRuntime.selected.toCommonTier()) {
            actualByTrack[PlaybackTrackIdentity.encode(id)] = QualityRecord(requested, actual)
        }
    }

    fun actualFor(id: MusicResourceId?): AudioQualityTier? {
        id ?: return null
        val record = actualByTrack[PlaybackTrackIdentity.encode(id)] ?: return null
        // A quality change updates MusicQualityRuntime before Media3 prepares the
        // same stable provider item. Do not show the old actual tier while the new
        // VKey request is still in flight.
        return record.actual.takeIf {
            record.requested == MusicQualityRuntime.selected.toCommonTier()
        }
    }

    fun clear(id: MusicResourceId? = null) {
        if (id == null) actualByTrack.clear()
        else actualByTrack.remove(PlaybackTrackIdentity.encode(id))
    }
}
