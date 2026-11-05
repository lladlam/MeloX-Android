package com.lladlam.melox.core.music.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for the app-owned favorites playlist. Only this store needs the
 * complete [MusicTrack] (including provider metadata), because a later playback
 * must be able to resolve the original provider item after a process restart.
 */
internal object MeloXTrackJson {
    fun encode(track: MusicTrack): JSONObject = JSONObject().apply {
        put("source", track.id.sourceId.value)
        put("id", track.id.value)
        put("title", track.title)
        put("artists", JSONArray().apply {
            track.artists.forEach { artist ->
                put(JSONObject().apply {
                    put("name", artist.name)
                    artist.id?.let { id ->
                        put("artistSource", id.sourceId.value)
                        put("artistId", id.value)
                    }
                })
            }
        })
        track.album?.let { album ->
            put("album", JSONObject().apply {
                put("name", album.name)
                album.id?.let { id ->
                    put("albumSource", id.sourceId.value)
                    put("albumId", id.value)
                }
                album.artworkUrl?.let { put("artworkUrl", it) }
            })
        }
        track.artworkUrl?.let { put("artwork", it) }
        track.durationMs?.let { put("durationMs", it) }
        put("availability", track.availability.name)
        track.bestQuality?.let { put("bestQuality", it.name) }
        track.bestBitrate?.let { put("bestBitrate", it) }
        put("metadata", encodeMetadata(track.providerMetadata))
    }

    fun decode(value: JSONObject): MusicTrack? {
        val source = SourceId.parse(value.optString("source")) ?: return null
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val title = value.optString("title").takeIf(String::isNotBlank) ?: return null
        val artists = value.optJSONArray("artists")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                    val artistSource = item.optString("artistSource").takeIf(String::isNotBlank)
                    val artistId = item.optString("artistId").takeIf(String::isNotBlank)
                    add(
                        MusicArtistRef(
                            id = artistSource?.let { sourceValue ->
                                SourceId.parse(sourceValue)
                            }?.let { artistSourceValue -> MusicResourceId(artistSourceValue, artistId ?: return@let null) },
                            name = name,
                        ),
                    )
                }
            }
        }.orEmpty()
        val albumValue = value.optJSONObject("album")
        val album = albumValue?.optString("name")?.takeIf(String::isNotBlank)?.let { name ->
            val albumSource = albumValue.optString("albumSource").takeIf(String::isNotBlank)
            val albumId = albumValue.optString("albumId").takeIf(String::isNotBlank)
            MusicAlbumRef(
                id = albumSource?.let { sourceValue ->
                    SourceId.parse(sourceValue)
                }?.let { albumSourceValue -> MusicResourceId(albumSourceValue, albumId ?: return@let null) },
                name = name,
                artworkUrl = albumValue.optString("artworkUrl").takeIf(String::isNotBlank),
            )
        }
        return MusicTrack(
            id = MusicResourceId(source, id),
            title = title,
            artists = artists.ifEmpty { listOf(MusicArtistRef(name = "未知歌手")) },
            album = album,
            artworkUrl = value.optString("artwork").takeIf(String::isNotBlank) ?: album?.artworkUrl,
            durationMs = value.optLong("durationMs", 0L).takeIf { it > 0L },
            availability = runCatching {
                TrackAvailability.valueOf(value.optString("availability"))
            }.getOrDefault(TrackAvailability.Unknown),
            bestQuality = runCatching {
                AudioQualityTier.valueOf(value.optString("bestQuality"))
            }.getOrNull(),
            bestBitrate = value.optInt("bestBitrate", 0).takeIf { it > 0 },
            providerMetadata = decodeMetadata(value.optJSONObject("metadata")),
        )
    }

    private fun encodeMetadata(metadata: ProviderTrackMetadata): JSONObject = JSONObject().apply {
        when (metadata) {
            ProviderTrackMetadata.Empty -> put("type", "empty")
            is ProviderTrackMetadata.Netease -> {
                put("type", "netease")
                put("numericId", metadata.numericId)
            }
            is ProviderTrackMetadata.QQMusic -> {
                put("type", "qq")
                put("songMid", metadata.songMid)
                metadata.mediaMid?.let { put("mediaMid", it) }
                metadata.numericSongId?.let { put("numericSongId", it) }
            }
            is ProviderTrackMetadata.Kugou -> {
                put("type", "kugou")
                put("hash", metadata.hash)
                metadata.albumAudioId?.let { put("albumAudioId", it) }
                metadata.albumId?.let { put("albumId", it) }
            }
            is ProviderTrackMetadata.Kuwo -> {
                put("type", "kuwo")
                put("mid", metadata.mid)
            }
            is ProviderTrackMetadata.AppleMusic -> {
                put("type", "apple")
                put("catalogId", metadata.catalogId)
                put("storefront", metadata.storefront)
                metadata.previewUrl?.let { put("previewUrl", it) }
            }
            is ProviderTrackMetadata.Bilibili -> {
                put("type", "bilibili")
                put("bvid", metadata.bvid)
                put("cid", metadata.cid)
                metadata.aid?.let { put("aid", it) }
                put("page", metadata.page)
            }
            is ProviderTrackMetadata.Spotify -> {
                put("type", "spotify")
                put("trackId", metadata.trackId)
                metadata.isrc?.let { put("isrc", it) }
            }
            is ProviderTrackMetadata.YouTube -> {
                put("type", "youtube")
                put("videoId", metadata.videoId)
                metadata.setVideoId?.let { put("setVideoId", it) }
            }
            is ProviderTrackMetadata.Local -> {
                put("type", "local")
                put("contentUri", metadata.contentUri)
                put("fileKey", metadata.fileKey)
            }
        }
    }

    private fun decodeMetadata(value: JSONObject?): ProviderTrackMetadata {
        value ?: return ProviderTrackMetadata.Empty
        return when (value.optString("type")) {
            "netease" -> value.optLong("numericId", 0L).takeIf { it > 0L }
                ?.let { ProviderTrackMetadata.Netease(it) } ?: ProviderTrackMetadata.Empty
            "qq" -> value.optString("songMid").takeIf(String::isNotBlank)?.let { songMid ->
                ProviderTrackMetadata.QQMusic(
                    songMid = songMid,
                    mediaMid = value.optString("mediaMid").takeIf(String::isNotBlank),
                    numericSongId = value.optLong("numericSongId", 0L).takeIf { it > 0L },
                )
            } ?: ProviderTrackMetadata.Empty
            "kugou" -> value.optString("hash").takeIf(String::isNotBlank)?.let { hash ->
                ProviderTrackMetadata.Kugou(
                    hash = hash,
                    albumAudioId = value.optLong("albumAudioId", 0L).takeIf { it > 0L },
                    albumId = value.optString("albumId").takeIf(String::isNotBlank),
                )
            } ?: ProviderTrackMetadata.Empty
            "kuwo" -> value.optLong("mid", 0L).takeIf { it > 0L }
                ?.let { ProviderTrackMetadata.Kuwo(it) } ?: ProviderTrackMetadata.Empty
            "apple" -> value.optString("catalogId").takeIf(String::isNotBlank)?.let { catalogId ->
                ProviderTrackMetadata.AppleMusic(
                    catalogId = catalogId,
                    storefront = value.optString("storefront").ifBlank { "us" },
                    previewUrl = value.optString("previewUrl").takeIf(String::isNotBlank),
                )
            } ?: ProviderTrackMetadata.Empty
            "bilibili" -> {
                val bvid = value.optString("bvid").takeIf(String::isNotBlank)
                val cid = value.optLong("cid", 0L).takeIf { it > 0L }
                if (bvid == null || cid == null) ProviderTrackMetadata.Empty else {
                    ProviderTrackMetadata.Bilibili(
                        bvid = bvid,
                        cid = cid,
                        aid = value.optLong("aid", 0L).takeIf { it > 0L },
                        page = value.optInt("page", 1).coerceAtLeast(1),
                    )
                }
            }
            "spotify" -> value.optString("trackId").takeIf(String::isNotBlank)?.let { trackId ->
                ProviderTrackMetadata.Spotify(
                    trackId = trackId,
                    isrc = value.optString("isrc").takeIf(String::isNotBlank),
                )
            } ?: ProviderTrackMetadata.Empty
            "youtube" -> value.optString("videoId").takeIf(String::isNotBlank)?.let { videoId ->
                ProviderTrackMetadata.YouTube(
                    videoId = videoId,
                    setVideoId = value.optString("setVideoId").takeIf(String::isNotBlank),
                )
            } ?: ProviderTrackMetadata.Empty
            "local" -> {
                val contentUri = value.optString("contentUri").takeIf(String::isNotBlank)
                val fileKey = value.optString("fileKey").takeIf(String::isNotBlank)
                if (contentUri == null || fileKey == null) ProviderTrackMetadata.Empty else {
                    ProviderTrackMetadata.Local(contentUri = contentUri, fileKey = fileKey)
                }
            }
            else -> ProviderTrackMetadata.Empty
        }
    }
}
