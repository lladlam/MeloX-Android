package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTrackIdentitySourceTest {
    @Test fun explicitUnknownSourceNeverBecomesLegacyNetease() {
        assertEquals(MusicResourceId(SourceId.parse("plugin:example.music")!!, "123"),
            PlaybackTrackIdentity.decodeWithExtras("123", "plugin:example.music", "123"))
        assertNull(PlaybackTrackIdentity.decodeWithExtras("123", "plugin:example.music", null))
        assertNull(PlaybackTrackIdentity.decodeWithExtras("123", "qq_music", null))
    }

    @Test fun explicitBuiltInAndLegacyNumericIdentitiesRemainReadable() {
        assertEquals(MusicResourceId(MusicSource.QQMusic, "123"),
            PlaybackTrackIdentity.decodeWithExtras("123", "qq_music", "123"))
        assertEquals(MusicResourceId(MusicSource.Netease, "123"),
            PlaybackTrackIdentity.decodeWithExtras("123", null, null))
    }
}
