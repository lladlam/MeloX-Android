package com.lladlam.melox.core.provider.local

import com.lladlam.melox.core.lyrics.LyricsDocument

data class LocalTrackRecord(
    val fileKey: String,
    val contentUri: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val sourceRootUri: String? = null,
    val artworkUri: String? = null,
    val isFavorite: Boolean = false,
    val recognizedNeteaseId: Long? = null,
    val recognizedTitle: String? = null,
    val recognizedArtist: String? = null,
    val recognizedAlbum: String? = null,
    val recognizedArtworkUrl: String? = null,
    val cachedLyrics: LyricsDocument? = null,
)

data class LocalScanRoot(
    val uri: String,
    val persistedFlags: Int,
)

data class LocalPlaylist(
    val id: String,
    val name: String,
    val trackKeys: List<String> = emptyList(),
)
