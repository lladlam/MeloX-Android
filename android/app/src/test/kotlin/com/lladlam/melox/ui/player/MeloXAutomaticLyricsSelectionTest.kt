package com.lladlam.melox.ui.player

import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.lyrics.BoundLyricSource
import com.lladlam.melox.core.lyrics.LyricBinding
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXAutomaticLyricsSelectionTest {
    @Test
    fun nearQualityUsesSourcePriorityAsStableTieBreak() {
        val qq = document("QQ", wordSynced = true)
        val netease = document("网易", wordSynced = true)

        assertEquals(
            qq,
            selectAutomaticLyrics(
                listOf(
                    AutoLyricCandidate(0, qq),
                    AutoLyricCandidate(1, netease),
                ),
            ),
        )
    }

    @Test
    fun availableAmlLAlwaysWinsBeforeFallbackSources() {
        val shortAmlL = document("AMLL", wordSynced = true)
        val completeQrc = documentWithLines("QRC", lineCount = 12, translation = "翻译", romanization = "roma")

        assertEquals(
            shortAmlL,
            selectAutomaticLyrics(
                listOf(
                    AutoLyricCandidate(0, shortAmlL),
                    AutoLyricCandidate(1, completeQrc),
                ),
            ),
        )
    }

    @Test
    fun emptyHigherPriorityFallsBackToNetease() {
        val netease = document("网易", wordSynced = true)
        val current = document("当前", wordSynced = false)

        assertEquals(
            netease,
            selectAutomaticLyrics(
                listOf(
                    AutoLyricCandidate(0, LyricsDocument(emptyList())),
                    AutoLyricCandidate(1, netease),
                    AutoLyricCandidate(2, current),
                ),
            ),
        )
    }

    @Test
    fun lineSyncedAmlLStillWinsBeforeQqFallback() {
        val selected = selectAutomaticLyrics(
            listOf(
                AutoLyricCandidate(0, document("AMLL 行级", wordSynced = false, translation = "translation")),
                AutoLyricCandidate(1, document("QQ 逐字", wordSynced = true)),
                AutoLyricCandidate(2, document("网易 逐字", wordSynced = true)),
            ),
        )
        assertEquals(document("AMLL 行级", wordSynced = false, translation = "translation"), selected)
    }

    @Test
    fun whenNoSourceHasWordTimingPriorityStillChoosesNonEmptyAmlL() {
        val selected = selectAutomaticLyrics(
            listOf(
                AutoLyricCandidate(0, document("AMLL 行级", wordSynced = false)),
                AutoLyricCandidate(1, document("QQ 行级", wordSynced = false)),
            ),
        )
        assertEquals(document("AMLL 行级", wordSynced = false), selected)
    }

    @Test
    fun originalDoesNotMatchDjLiveRemixOrInstrumental() {
        listOf(
            "提瓦特民谣 DJ版",
            "提瓦特民谣 (Live)",
            "提瓦特民谣 Remix",
            "提瓦特民谣 伴奏",
        ).forEach { title ->
            assertFalse(isSafeCrossProviderLyricMatch("提瓦特民谣", "宴宁", 240_000, track(title, "宴宁", 240_000)))
        }
    }

    @Test
    fun versionedTracksRequireSameVersionLabelAndStrictDuration() {
        assertTrue(isSafeCrossProviderLyricMatch("提瓦特民谣 DJ版", "宴宁", 240_000, track("提瓦特民谣 DJ版", "宴宁", 240_800)))
        assertFalse(isSafeCrossProviderLyricMatch("提瓦特民谣 DJ版", "宴宁", 240_000, track("提瓦特民谣 Remix", "宴宁", 240_000)))
        assertFalse(isSafeCrossProviderLyricMatch("提瓦特民谣 DJ版", "宴宁", 240_000, track("提瓦特民谣 DJ版", "宴宁", 242_000)))
    }

    @Test
    fun exactOriginalRequiresArtistAndTwoSecondDurationWindow() {
        assertTrue(isSafeCrossProviderLyricMatch("提瓦特民谣", "宴宁", 240_000, track("提瓦特民谣", "宴宁 / 陶典", 241_500)))
        assertFalse(isSafeCrossProviderLyricMatch("提瓦特民谣", "宴宁", 240_000, track("提瓦特民谣", "其他歌手", 240_000)))
        assertFalse(isSafeCrossProviderLyricMatch("提瓦特民谣", "宴宁", 240_000, track("提瓦特民谣", "宴宁", 243_000)))
    }

    @Test
    fun selectedCandidateRetainsExactBindingIdentity() {
        val binding = LyricBinding(
            source = BoundLyricSource.AmlL,
            resourceValue = "2750140001",
            title = "提瓦特民谣",
            artist = "宴宁",
            durationMs = 240_000,
        )
        val selected = selectAutomaticLyricCandidate(
            listOf(AutoLyricCandidate(0, document("AMLL", true), binding)),
        )
        assertEquals(binding, selected?.binding)
    }

    @Test
    fun routesAreAmlLThenQQThenNeteaseThenCurrentProvider() {
        MusicSource.entries.forEach { source ->
            val routes = automaticLyricSourcesFor(source)
            assertEquals(LyricAutoSource.AmlL, routes.first())
            assertEquals(routes.distinct(), routes)
            when (source) {
                MusicSource.QQMusic -> {
                    assertEquals(listOf(LyricAutoSource.AmlL, LyricAutoSource.Current, LyricAutoSource.Netease), routes)
                }
                MusicSource.Netease -> {
                    assertEquals(listOf(LyricAutoSource.AmlL, LyricAutoSource.QQMusic, LyricAutoSource.Current), routes)
                }
                else -> {
                    assertEquals(listOf(LyricAutoSource.AmlL, LyricAutoSource.QQMusic, LyricAutoSource.Netease, LyricAutoSource.Current), routes)
                }
            }
        }
    }

    @Test
    fun automaticCacheSeparatesUnknownKnownDurationAndMetadata() {
        val id = MusicResourceId(MusicSource.Spotify, "track")
        val unknown = lyricCacheKey(id, "Title", "Artist", 0L, automaticSelection = true)
        val known = lyricCacheKey(id, "Title", "Artist", 180_000L, automaticSelection = true)
        val changedMetadata = lyricCacheKey(id, "Other", "Artist", 180_000L, automaticSelection = true)

        assertFalse(unknown == known)
        assertFalse(known == changedMetadata)
    }

    @Test
    fun automaticCacheRoundsMinorDurationCorrections() {
        val id = MusicResourceId(MusicSource.Spotify, "track")
        assertEquals(
            lyricCacheKey(id, "Title", "Artist", 180_100L, automaticSelection = true),
            lyricCacheKey(id, "Title", "Artist", 180_400L, automaticSelection = true),
        )
    }

    @Test
    fun unknownDurationCanMatchExactTitleAndArtistForAmlLDiscovery() {
        assertTrue(isSafeCrossProviderLyricMatch("SCARED LONELY", "virtual: girl", 0L, track("SCARED LONELY", "virtual girl", 180_000L)))
        assertFalse(isSafeCrossProviderLyricMatch("SCARED LONELY", "virtual: girl", 0L, track("SCARED LONELY", "other artist", 180_000L)))
    }

    @Test
    fun bilibiliAlignmentOnlyControlsReplacementAssociationSideEffect() {
        val alignmentDisabled = bilibiliLyricLoadPolicy(
            automaticSelection = true,
            alignmentEnabled = false,
            durationMs = 180_000L,
        )
        assertTrue(alignmentDisabled.loadMatchedLyrics)
        assertFalse(alignmentDisabled.saveReplacementAssociation)

        val unknownDuration = bilibiliLyricLoadPolicy(
            automaticSelection = true,
            alignmentEnabled = true,
            durationMs = 0L,
        )
        assertFalse(unknownDuration.loadMatchedLyrics)
        assertFalse(unknownDuration.saveReplacementAssociation)
    }

    private fun document(text: String, wordSynced: Boolean, translation: String? = null): LyricsDocument =
        LyricsDocument(
            listOf(
                LyricLine(
                    timeMs = 0,
                    durationMs = 1_000,
                    text = text,
                    syllables = if (wordSynced) listOf(LyricSyllable(text, 0, 1_000)) else emptyList(),
                    translation = translation,
                ),
            ),
        )

    private fun documentWithLines(
        text: String,
        lineCount: Int,
        translation: String? = null,
        romanization: String? = null,
    ): LyricsDocument = LyricsDocument(
        List(lineCount) { index ->
            LyricLine(
                timeMs = index * 1_000L,
                durationMs = 1_000,
                text = "$text $index",
                syllables = listOf(LyricSyllable(text, index * 1_000L, (index + 1) * 1_000L)),
                translation = translation,
                romanization = romanization,
            )
        },
    )

    private fun track(title: String, artist: String, durationMs: Long) = MusicTrack(
        id = MusicResourceId(MusicSource.QQMusic, title),
        title = title,
        artists = listOf(MusicArtistRef(name = artist)),
        durationMs = durationMs,
    )
}
