package com.lladlam.melox.core.provider.kuwo

import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import okhttp3.OkHttpClient

class KuwoProvider(
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : MusicProvider,
    SearchCapability,
    PlaybackCapability,
    LyricsCapability {
    override val source: MusicSource = MusicSource.Kuwo
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
    )

    private val api = KuwoApiClient(httpClient)
    private val lyricsClient = KuwoLyricsClient(httpClient)

    override suspend fun searchSongs(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = api.searchSongs(query, page, pageSize)

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = api.resolvePlayback(track, quality)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = lyricsClient.lyrics(track)
}
