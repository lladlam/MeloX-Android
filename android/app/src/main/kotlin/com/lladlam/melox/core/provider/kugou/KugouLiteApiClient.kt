package com.lladlam.melox.core.provider.kugou

import android.util.Base64
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAlbumRef
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import java.io.IOException
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Direct Android implementation of the 酷狗概念版 (KuGou "lite") API.
 *
 * The protocol shapes are ported from MakcRe/KuGouMusicApi (MIT). The lite app
 * uses appid 3116, clientver 11440 and its own signature salt; a standard KuGou
 * token is not valid here. Account material is persisted outside this class and
 * is never included in log messages or playback request headers.
 */
internal class KugouLiteApiClient(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
    private val persistAuth: (String) -> Unit = {},
    private val persistVip: (KugouLiteVip) -> Unit = {},
    private val persistDfid: (String) -> Unit = {},
    private val onAuthExpired: () -> Unit = {},
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)
    private val catalog = KugouCatalogClient(sessionProvider, httpClient, MusicSource.KugouLite)
    private val lyrics = KugouLyricsClient(sessionProvider, httpClient, MusicSource.KugouLite)
    private val playlists = KugouPlaylistClient(sessionProvider, httpClient, MusicSource.KugouLite)
    private val playlistWrites = KugouPlaylistWriteClient(sessionProvider, httpClient, MusicSource.KugouLite)
    private val rankings = KugouRankingClient(sessionProvider, httpClient, MusicSource.KugouLite)
    private val discovery = KugouDiscoveryClient(sessionProvider, httpClient, MusicSource.KugouLite)

    @Volatile private var vipCache: KugouLiteVip? = null
    @Volatile private var vipCacheAt: Long = 0L
    @Volatile private var vipCacheAccount: String = ""
    @Volatile private var dfidVerified: Boolean = false

    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> {
        val keyword = query.trim()
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (keyword.isBlank()) return MusicPage(emptyList(), safePage, safeSize, 0)
        val response = requests.get(
            path = "/v2/search/song",
            params = mapOf(
                "keyword" to keyword,
                "page" to safePage.toString(),
                "pagesize" to safeSize.toString(),
                "platform" to "AndroidFilter",
                "iscorrection" to "1",
                "privilegefilter" to "0",
                "area_code" to "1",
                "dopicfull" to "1",
                // Young/lite search expects the clientver 201 filter, even though
                // the request itself is still signed with the lite appid.
                "clientver" to "201",
            ),
            headers = mapOf("x-router" to "complexsearch.kugou.com"),
        )
        val data = response.optJSONObject("data") ?: response
        val list = data.optJSONArray("lists")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("info")
            ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                KugouLiteParsing.parseTrack(item)?.let(::add)
                val groups = item.optJSONArray("Grp") ?: item.optJSONArray("grp")
                for (groupIndex in 0 until (groups?.length() ?: 0)) {
                    groups?.optJSONObject(groupIndex)?.let(KugouLiteParsing::parseTrack)?.let(::add)
                }
            }
        }.distinctBy { it.id.value }
        val total = firstLong(data, "total", "total_count", "totalnum", "count").takeIf { it >= 0L }
        return MusicPage(tracks, safePage, safeSize, total)
    }

    suspend fun freeListenSongs(
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        val response = requests.post(
            path = "/concepts/v1/ai/recommend_song",
            body = JSONObject()
                .put("apiver", 2)
                .put("source", 2)
                .put("pagesize", safeSize)
                .put("page", safePage),
        )
        val tracks = KugouLiteParsing.flattenObjects(response)
            .mapNotNull(KugouLiteParsing::parseTrack)
            .map { it.copy(availability = TrackAvailability.Playable) }
            .distinctBy { it.id.value }
            .take(safeSize)
        val total = firstLong(response, "total", "total_count", "count").takeIf { it >= 0L }
        return MusicPage(tracks, safePage, safeSize, total)
    }

    suspend fun songDetail(track: MusicTrack): MusicTrack {
        val metadata = track.requireKugouLiteMetadata()
        val quality = runCatching { privilege(metadata) }.getOrNull()
        val vip = vipStatus()
        val availability = when {
            quality == null -> TrackAvailability.Unknown
            quality.qualities.any { it.playableFor(vip) } -> TrackAvailability.Playable
            quality.qualities.any { it.freeListen } -> TrackAvailability.Playable
            quality.qualities.any { it.exists && it.requireVipType.isNotBlank() } -> TrackAvailability.SubscriptionRequired
            quality.qualities.any { it.exists && it.status == 0 } -> TrackAvailability.CopyrightRestricted
            else -> TrackAvailability.Unavailable
        }
        return track.copy(availability = availability)
    }

    suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution {
        val metadata = track.requireKugouLiteMetadata()
        val dfidError = runCatching { ensureDfid() }.exceptionOrNull()
        val session = sessionProvider()
        val privilege = runCatching { privilege(metadata) }.getOrNull()
        val vip = vipStatus()
        val specs = quality.liteQualityCandidates()
        var lastError: String? = dfidError?.message
        var authError: Throwable? = null
        var previewUrl: String? = null

        val ordered = specs.sortedBy { spec ->
            val item = privilege?.qualities?.firstOrNull { it.quality == spec.apiValue }
            when {
                item == null -> 100
                item.playableFor(vip) || item.freeListen -> 0
                item.exists -> 10
                else -> 100
            }
        }
        for (spec in ordered) {
            val item = privilege?.qualities?.firstOrNull { it.quality == spec.apiValue }
            if (item != null && !item.exists) continue
            if (item != null && !decoderSupports(item.extname)) {
                lastError = "当前设备解码器不支持 ${item.extname.uppercase()} 格式"
                continue
            }
            if (item != null && !item.playableFor(vip) && !item.freeListen && spec.apiValue != "128") {
                lastError = when {
                    item.requireVipType == "suvip" -> "该音质需要酷狗概念版超级 VIP"
                    item.requireVipType.isNotBlank() -> "该音质需要酷狗概念版 VIP"
                    else -> "该音质当前不可用"
                }
                continue
            }
            // A concept "free listen" row can have status=0 in
            // privilege/lite (the free entitlement is granted by the playback
            // endpoint, not the static privilege table). Always try 128 and
            // trust the URL response's is_full_audio/p_0 marker.
            val auth = if (session.isLoggedIn) {
                runCatching { songAuth(metadata) }
                    .recoverCatching { error ->
                        if (error.message?.contains("验证") == true) {
                            registerDevice()
                            songAuth(metadata)
                        } else {
                            throw error
                        }
                    }
                    .onFailure { authError = it }
                    .getOrNull()
            } else {
                null
            }
            if (auth != null) {
                val resolved = runCatching {
                    authorizedUrl(metadata, spec.apiValue, auth)
                }.recoverCatching { error ->
                    if (error.message?.contains("验证") == true) {
                        registerDevice()
                        authorizedUrl(metadata, spec.apiValue, auth)
                    } else {
                        throw error
                    }
                }.onFailure { lastError = it.message }.getOrNull()
                if (resolved != null) {
                    if (resolved.full) {
                        return PlayableFactory.create(
                            url = resolved.url,
                            requested = quality,
                            actual = spec.tier,
                            bitrate = item?.bitrate,
                            format = formatFromUrl(resolved.url) ?: item?.extname,
                            headers = playbackHeaders(),
                        )
                    }
                    previewUrl = resolved.url
                }
            }
            // Anonymous URL is still useful for genuinely free tracks and for
            // the 30-second preview of paid tracks. It is never used to bypass
            // a signed-in failure.
            if (authError !is KugouAuthException) {
                val resolved = runCatching { anonymousUrl(metadata, spec.apiValue) }
                    .recoverCatching { error ->
                        if (error.message?.contains("验证") == true) {
                            registerDevice()
                            anonymousUrl(metadata, spec.apiValue)
                        } else {
                            throw error
                        }
                    }
                    .onFailure { lastError = it.message }
                    .getOrNull()
                if (resolved != null) {
                    if (resolved.full) {
                        return PlayableFactory.create(
                            resolved.url, quality, spec.tier, item?.bitrate,
                            formatFromUrl(resolved.url) ?: item?.extname, playbackHeaders(),
                        )
                    }
                    previewUrl = resolved.url
                }
            }
        }

        if (authError is KugouAuthException) {
            onAuthExpired()
            return PlaybackResolution.LoginRequired
        }
        previewUrl?.let { return PlaybackResolution.Preview(it) }
        if (!session.isLoggedIn) return PlaybackResolution.LoginRequired
        val normalized = lastError.orEmpty().lowercase()
        return when {
            "地区" in normalized || "region" in normalized -> PlaybackResolution.RegionRestricted
            "版权" in normalized || "copyright" in normalized -> PlaybackResolution.CopyrightRestricted
            "vip" in normalized || "会员" in normalized || "付费" in normalized ->
                PlaybackResolution.SubscriptionRequired
            else -> PlaybackResolution.Unavailable(lastError)
        }
    }

    suspend fun accountSummary() = discovery.accountSummary()

    suspend fun homeFeedNewSongs(limit: Int): List<MusicTrack> =
        runCatching { freeListenSongs(1, limit.coerceIn(1, 50)).items }.getOrDefault(emptyList())

    suspend fun userPlaylists(page: Int, pageSize: Int) = discovery.userPlaylists(page, pageSize)

    suspend fun recommendedPlaylists(limit: Int) = runCatching {
        discovery.homeFeed(playlistLimit = limit, newSongLimit = 0, rankingLimit = 0)
            .recommendedPlaylists
    }.getOrDefault(emptyList())

    suspend fun rankings(limit: Int) = runCatching {
        discovery.homeFeed(playlistLimit = 0, newSongLimit = 0, rankingLimit = limit).rankings
    }.getOrDefault(emptyList())

    suspend fun playlistDetail(
        playlist: com.lladlam.melox.core.music.model.MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ) = playlists.detail(playlist, page, pageSize)

    suspend fun rankingTracks(
        ranking: com.lladlam.melox.core.music.model.MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ) = rankings.tracks(ranking, page, pageSize)

    suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) =
        catalog.searchPlaylists(query, page, pageSize)

    suspend fun searchAlbums(query: String, page: Int, pageSize: Int) =
        catalog.searchAlbums(query, page, pageSize)

    suspend fun searchArtists(query: String, page: Int, pageSize: Int) =
        catalog.searchArtists(query, page, pageSize)

    suspend fun albumDetail(
        album: com.lladlam.melox.core.music.model.MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ) = catalog.albumDetail(album, page, pageSize)

    suspend fun artistDetail(
        artist: com.lladlam.melox.core.music.model.MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ) = catalog.artistDetail(artist, page, pageSize)

    suspend fun lyricDocument(track: MusicTrack) = lyrics.lyrics(track)

    suspend fun writablePlaylists(page: Int, pageSize: Int) =
        playlistWrites.writablePlaylists(page, pageSize)

    suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: com.lladlam.melox.core.music.model.MusicPlaylistSummary,
    ) = playlistWrites.addTrackToPlaylist(track, playlist)

    /** Returns the concept VIP state. Results are cached briefly to avoid a request per track. */
    suspend fun vipStatus(force: Boolean = false): KugouLiteVip {
        val now = System.currentTimeMillis()
        val session = sessionProvider()
        val accountKey = "${session.platform}:${session.userId}:${session.token}"
        vipCache?.takeIf {
            !force && vipCacheAccount == accountKey && now - vipCacheAt < 60_000L
        }?.let { return it }
        if (!session.isLoggedIn) {
            return KugouLiteVip.NONE.also { vipCache = it; vipCacheAt = now; vipCacheAccount = accountKey }
        }
        val value = try {
            withContext(Dispatchers.IO) { fetchVipStatus() }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            KugouLiteVip(
                isVip = session.vipActive || session.vipType > 0 || session.vipExpiresAtEpochSeconds > now / 1_000L,
                superVip = session.superVip,
                vipType = session.vipType,
                expiresAtEpochSeconds = session.vipExpiresAtEpochSeconds,
                freeListen = session.freeListenEligible,
            )
        }
        if (sessionProvider().let { "${it.platform}:${it.userId}:${it.token}" } != accountKey) return value
        vipCache = value
        vipCacheAt = now
        vipCacheAccount = accountKey
        persistVip(value)
        return value
    }

    suspend fun claimDailyVip(date: LocalDate = LocalDate.now()): JSONObject {
        val session = sessionProvider()
        if (!session.isLoggedIn) throw KugouAuthException("请先登录酷狗概念版")
        return withContext(Dispatchers.IO) {
            requests.postWithoutBody(
                path = "/youth/v1/recharge/receive_vip_listen_song",
                params = mapOf(
                    "source_id" to "90139",
                    "receive_day" to date.toString(),
                ),
                headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            )
        }.also { checkVipRewardResponse(it); vipCache = null }
    }

    suspend fun monthVipRecord(): JSONObject = withContext(Dispatchers.IO) {
        requests.get(
            path = "/youth/v1/activity/get_month_vip_record",
            params = mapOf("latest_limit" to "100"),
        )
    }

    suspend fun upgradeDailyVip(): JSONObject {
        val session = sessionProvider()
        if (!session.isLoggedIn) throw KugouAuthException("请先登录酷狗概念版")
        return withContext(Dispatchers.IO) {
            requests.postWithoutBody(
                path = "/youth/v1/listen_song/upgrade_vip_reward",
                params = mapOf("kugouid" to session.userId.toString(), "ad_type" to "1"),
            )
        }.also { checkVipRewardResponse(it); vipCache = null }
    }

    private fun checkVipRewardResponse(response: JSONObject) {
        if (response.optInt("status", 0) != 1) {
            throw IOException(response.optString("error_msg").ifBlank { "酷狗未确认本次会员领取，请刷新后重试" })
        }
    }

    private suspend fun fetchVipStatus(): KugouLiteVip {
        val response = requests.get(
            baseUrl = "https://kugouvip.kugou.com",
            path = "/v1/get_union_vip",
            params = mapOf(
                "busi_type" to "concept",
                "opt_product_types" to "dvip,qvip",
                "product_type" to "svip",
            ),
        )
        return KugouLiteParsing.parseVip(response)
    }

    private suspend fun ensureAuth(): String {
        val current = sessionProvider()
        if (current.auth.isNotBlank()) return current.auth
        val response = requests.get(
            path = "/v1/user_verify",
            params = mapOf("module_id" to "51"),
            headers = mapOf("x-router" to "trackercdngz.kugou.com"),
        )
        val auth = response.optJSONObject("data")?.optString("auth").orEmpty()
        if (auth.isBlank()) throw KugouAuthException("酷狗概念版没有返回播放授权")
        persistAuth(auth)
        return auth
    }

    private suspend fun songAuth(metadata: ProviderTrackMetadata.Kugou): SongAuth {
        val auth = ensureAuth()
        val response = requests.get(
            path = "/v1/authorization",
            params = mapOf(
                "authorization" to auth,
                "module_id" to "51",
                "album_audio_id" to (metadata.albumAudioId ?: 0L).toString(),
                "clientver" to "11561",
                "hash" to metadata.hash.lowercase(),
            ),
            headers = mapOf("x-router" to "trackercdngz.kugou.com"),
        )
        val data = response.optJSONObject("data") ?: response
        val songAuthValue = data.optString("auth")
            .ifBlank { data.optJSONObject("data")?.optString("auth").orEmpty() }
            .ifBlank { auth }
        val openTime = firstLong(data, "open_time", "openTime", "time").takeIf { it > 0L } ?: 0L
        return SongAuth(songAuthValue, openTime)
    }

    private suspend fun privilege(metadata: ProviderTrackMetadata.Kugou): KugouLitePrivilege {
        val session = sessionProvider()
        val hash = metadata.hash.lowercase()
        val response = requests.post(
            path = "/v2/get_res_privilege/lite",
            body = JSONObject()
                .put("appid", session.platform.appId)
                .put("area_code", 1)
                .put("behavior", "play")
                .put("clientver", session.platform.clientVersion)
                .put("need_hash_offset", 1)
                .put("relate", 1)
                .put("support_verify", 1)
                .put(
                    "resource",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "audio")
                            .put("page_id", 0)
                            .put("hash", hash)
                            .put("album_id", metadata.albumId?.toLongOrNull() ?: 0L),
                    ),
                )
                .put(
                    "qualities",
                    JSONArray(
                        listOf(
                            "128", "320", "flac", "high", "viper_atmos",
                            "viper_tape", "viper_clear", "super", "multitrack",
                        ),
                    ),
                ),
            headers = mapOf(
                "x-router" to "media.store.kugou.com",
                "Content-Type" to "application/json",
            ),
        )
        val data = response.optJSONArray("data") ?: JSONArray()
        val item = (0 until data.length())
            .mapNotNull(data::optJSONObject)
            .firstOrNull { it.optString("hash").equals(hash, ignoreCase = true) }
            ?: (0 until data.length()).mapNotNull(data::optJSONObject).firstOrNull()
            ?: JSONObject()
        return KugouLiteParsing.parsePrivilege(item, hash)
    }

    private suspend fun authorizedUrl(
        metadata: ProviderTrackMetadata.Kugou,
        quality: String,
        auth: SongAuth,
    ): ResolvedUrl? {
        val session = sessionProvider()
        val hash = metadata.hash.lowercase()
        val params = linkedMapOf(
            "album_id" to (metadata.albumId?.takeIf(String::isNotBlank) ?: "0"),
            "area_code" to "1",
            "module" to "",
            "hash" to hash,
            "need_m" to "0",
            "ssa_flag" to "is_fromtrack",
            "version" to "11430",
            "open_time" to auth.openTime.toString(),
            "ptype" to "0",
            "need_ogg" to "1",
            "page_id" to "967177915",
            "auth" to auth.auth,
            "mtype" to "0",
            "quality" to quality,
            "album_audio_id" to (metadata.albumAudioId ?: 0L).toString(),
            "behavior" to "play",
            "pid" to "411",
            "module_id" to "51",
            "cmd" to "26",
            "ppage_id" to "356753938,823673182,967485191",
            "clientver" to "11561",
            "pidversion" to "3001",
            "cdnBackup" to "1",
            "dfid" to session.dfid.ifBlank { "-" },
            "key" to requests.signKey(hash, session),
        )
        val response = requests.get(
            path = "/v5/url",
            params = params,
            headers = mapOf("x-router" to "trackercdn.kugou.com"),
        )
        return parseResolvedUrl(response)
    }

    private suspend fun anonymousUrl(
        metadata: ProviderTrackMetadata.Kugou,
        quality: String,
    ): ResolvedUrl? {
        val session = sessionProvider()
        val hash = metadata.hash.lowercase()
        val params = linkedMapOf(
            "album_id" to (metadata.albumId?.takeIf(String::isNotBlank) ?: "0"),
            "area_code" to "1",
            "hash" to hash,
            "ssa_flag" to "is_fromtrack",
            "version" to "11430",
            "page_id" to "967177915",
            "quality" to quality,
            "album_audio_id" to (metadata.albumAudioId ?: 0L).toString(),
            "behavior" to "play",
            "pid" to "411",
            "cmd" to "26",
            "pidversion" to "3001",
            "IsFreePart" to "1",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "module" to "",
            "clientver" to "11430",
            "dfid" to session.dfid.ifBlank { "-" },
            "key" to requests.signKey(hash, session),
        )
        val response = requests.get(
            path = "/v5/url",
            params = params,
            headers = mapOf("x-router" to "trackercdn.kugou.com"),
        )
        return parseResolvedUrl(response)
    }

    private suspend fun ensureDfid(): String {
        val current = sessionProvider()
        if (dfidVerified || (current.dfid.isNotBlank() && current.dfid != "-")) {
            dfidVerified = true
            return current.dfid
        }
        return registerDevice()
    }

    /**
     * Registers this install with KuGou's risk service. Playback URL endpoints
     * answer "本次请求需要验证" without a server-issued dfid, even for free tracks.
     *
     * The request body is AES-CBC (key derived from a random six-character
     * nonce) and the nonce is RSA-PKCS#1 encrypted. This mirrors the MIT
     * KuGouMusicApi `register_dev` implementation; no DRM or membership check
     * is bypassed.
     */
    private suspend fun registerDevice(): String = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        val nonce = (1..6).map { ALPHABET.random() }.joinToString("")
        val digest = md5Hex(nonce)
        val keyBytes = digest.take(16).toByteArray(Charsets.UTF_8)
        val ivBytes = digest.drop(16).take(16).toByteArray(Charsets.UTF_8)
        val deviceJson = JSONObject()
            .put("availableRamSize", 4_983_533_568L)
            .put("availableRomSize", 48_114_719L)
            .put("availableSDSize", 48_114_717L)
            .put("basebandVer", "")
            .put("batteryLevel", 100)
            .put("batteryStatus", 3)
            .put("brand", "Redmi")
            .put("buildSerial", "unknown")
            .put("device", "marble")
            .put("imei", session.guid)
            .put("imsi", "")
            .put("manufacturer", "Xiaomi")
            .put("uuid", session.guid)
            .put("accelerometer", false)
            .put("accelerometerValue", "")
            .put("gravity", false)
            .put("gravityValue", "")
            .put("gyroscope", false)
            .put("gyroscopeValue", "")
            .put("light", false)
            .put("lightValue", "")
            .put("magnetic", false)
            .put("magneticValue", "")
            .put("orientation", false)
            .put("orientationValue", "")
            .put("pressure", false)
            .put("pressureValue", "")
            .put("step_counter", false)
            .put("step_counterValue", "")
            .put("temperature", false)
            .put("temperatureValue", "")
        val plain = deviceJson.toString().toByteArray(Charsets.UTF_8)
        val encrypted = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            doFinal(plain)
        }
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val rsaPayload = JSONObject()
            .put("aes", nonce)
            .put("uid", session.userId)
            .put("token", session.token)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.decode(LITE_RSA_PUBLIC_KEY, Base64.NO_WRAP)),
            ))
            doFinal(rsaPayload)
        }
        val p = rsa.joinToString("") { "%02x".format(it) }
        val response = requests.postRaw(
            baseUrl = "https://userservice.kugou.com",
            path = "/risk/v2/r_register_dev",
            params = mapOf(
                "part" to "1",
                "platid" to "1",
                "p" to p,
                "dfid" to "",
            ),
            body = encoded,
            includeCookie = false,
        )
        val decrypted = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            doFinal(response)
        }
        val json = runCatching { JSONObject(String(decrypted, Charsets.UTF_8)) }
            .getOrElse { throw IOException("酷狗概念版设备注册返回了无法解析的数据", it) }
        if (json.optInt("status", 0) != 1) {
            throw IOException(
                json.optString("error_msg").ifBlank { "酷狗概念版设备注册失败" },
            )
        }
        val dfid = json.optJSONObject("data")?.optString("dfid").orEmpty()
        if (dfid.isBlank()) throw IOException("酷狗概念版设备注册没有返回设备标识")
        persistDfid(dfid)
        dfidVerified = true
        dfid
    }

    private fun parseResolvedUrl(response: JSONObject): ResolvedUrl? {
        val url = KugouLiteParsing.findPlaybackUrl(response) ?: return null
        val fullFlag = response.optInt("is_full_audio", -1)
        val full = when {
            fullFlag == 1 -> true
            fullFlag == 0 -> false
            "/p_0_" in url -> false
            else -> true
        }
        val durationSeconds = firstLong(response, "timeLength", "timelength", "duration")
            .takeIf { it > 0L }
        return ResolvedUrl(
            url = url,
            full = full,
            durationMs = durationSeconds?.times(1_000L),
        )
    }

    private fun decoderSupports(extname: String): Boolean {
        if (extname.isBlank()) return true
        return extname.lowercase() in setOf("mp3", "ogg", "flac", "m4a", "aac", "wav", "opus")
    }

    private fun playbackHeaders(): Map<String, String> = mapOf(
        "User-Agent" to KugouPlatform.Lite.userAgent,
        "Referer" to "https://www.kugou.com/",
        "Accept" to "*/*",
        "Accept-Encoding" to "identity",
    )

    private fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun md5Hex(value: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        const val LITE_RSA_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"
    }

    private fun formatFromUrl(url: String): String? =
        url.substringBefore('?').substringAfterLast('.', "").lowercase().takeIf(String::isNotBlank)

    private data class SongAuth(val auth: String, val openTime: Long)

    private data class ResolvedUrl(
        val url: String,
        val full: Boolean,
        val durationMs: Long?,
    )
}

internal object PlayableFactory {
    fun create(
        url: String,
        requested: AudioQualityTier,
        actual: AudioQualityTier,
        bitrate: Int?,
        format: String?,
        headers: Map<String, String>,
    ): PlaybackResolution.Playable = PlaybackResolution.Playable(
        url = url,
        requestHeaders = headers,
        requestedQuality = requested,
        actualQuality = actual,
        bitrate = bitrate,
        format = format,
    )
}

internal fun MusicTrack.requireKugouLiteMetadata(): ProviderTrackMetadata.Kugou {
    require(id.source == MusicSource.KugouLite) {
        "KugouLiteApiClient cannot handle ${id.source.storageValue} track"
    }
    return (providerMetadata as? ProviderTrackMetadata.Kugou)
        ?: ProviderTrackMetadata.Kugou(hash = id.value)
}

internal data class KugouLiteQualitySpec(
    val apiValue: String,
    val tier: AudioQualityTier,
)

internal fun AudioQualityTier.liteQualityCandidates(): List<KugouLiteQualitySpec> = when (this) {
    AudioQualityTier.Standard -> listOf(KugouLiteQualitySpec("128", AudioQualityTier.Standard))
    AudioQualityTier.High -> listOf(
        KugouLiteQualitySpec("320", AudioQualityTier.High),
        KugouLiteQualitySpec("128", AudioQualityTier.Standard),
    )
    AudioQualityTier.Lossless -> listOf(
        KugouLiteQualitySpec("flac", AudioQualityTier.Lossless),
        KugouLiteQualitySpec("320", AudioQualityTier.High),
        KugouLiteQualitySpec("128", AudioQualityTier.Standard),
    )
    AudioQualityTier.HiResolution -> listOf(
        KugouLiteQualitySpec("high", AudioQualityTier.HiResolution),
        KugouLiteQualitySpec("flac", AudioQualityTier.Lossless),
        KugouLiteQualitySpec("320", AudioQualityTier.High),
        KugouLiteQualitySpec("128", AudioQualityTier.Standard),
    )
    AudioQualityTier.Immersive -> listOf(
        KugouLiteQualitySpec("viper_atmos", AudioQualityTier.Immersive),
        KugouLiteQualitySpec("viper_clear", AudioQualityTier.Immersive),
        KugouLiteQualitySpec("high", AudioQualityTier.HiResolution),
        KugouLiteQualitySpec("flac", AudioQualityTier.Lossless),
        KugouLiteQualitySpec("320", AudioQualityTier.High),
        KugouLiteQualitySpec("128", AudioQualityTier.Standard),
    )
    AudioQualityTier.Master -> listOf(
        // viper_tape needs KuGou's server-side transcode and DSD (super) is not
        // decodable by Media3, so both are intentionally not requested.
        KugouLiteQualitySpec("viper_clear", AudioQualityTier.Immersive),
        KugouLiteQualitySpec("viper_atmos", AudioQualityTier.Immersive),
        KugouLiteQualitySpec("high", AudioQualityTier.HiResolution),
        KugouLiteQualitySpec("flac", AudioQualityTier.Lossless),
        KugouLiteQualitySpec("320", AudioQualityTier.High),
        KugouLiteQualitySpec("128", AudioQualityTier.Standard),
    )
}

data class KugouLiteVip(
    val isVip: Boolean,
    val superVip: Boolean,
    val vipType: Int,
    val expiresAtEpochSeconds: Long,
    val freeListen: Boolean,
) {
    companion object {
        val NONE = KugouLiteVip(false, false, 0, 0L, false)
    }
}

internal data class KugouLiteQuality(
    val quality: String,
    val exists: Boolean,
    val level: Int,
    val bitrate: Int,
    val extname: String,
    val hash: String,
    val size: Long,
    val privilege: Int,
    val status: Int,
    val payType: Int,
    val needVip: Boolean,
    val freeListen: Boolean,
    val requireVipType: String,
) {
    fun playableFor(vip: KugouLiteVip): Boolean {
        if (!exists || status == 0) return false
        if (!needVip || freeListen) return true
        return when (requireVipType) {
            "suvip" -> vip.superVip
            "concept" -> vip.isVip
            else -> vip.isVip
        }
    }
}

internal data class KugouLitePrivilege(
    val hash: String,
    val title: String,
    val singer: String,
    val album: String,
    val freeSong: Boolean,
    val qualities: List<KugouLiteQuality>,
)

internal object KugouLiteParsing {
    private val qualityOrder = listOf(
        "128", "320", "flac", "high", "super",
        "viper_clear", "viper_tape", "viper_atmos", "multitrack",
    )
    private val levelForQuality = mapOf(
        "128" to listOf(1, 2),
        "320" to listOf(4),
        "flac" to listOf(5),
        "high" to listOf(6),
        "super" to listOf(7),
    )
    private val superVipQualities = setOf("viper_tape", "viper_clear", "viper_atmos")

    fun parseTrack(item: JSONObject): MusicTrack? {
        val audio = item.optJSONObject("audio_info")
        val albumInfo = item.optJSONObject("album_info")
        val hash = firstString(item, "FileHash", "Hash", "hash", "filehash")
            .ifBlank { audio?.optString("hash").orEmpty() }
            .uppercase()
        if (hash.isBlank()) return null
        val (title, singer) = recoverKugouTrackText(
            firstString(item, "SongName", "songname", "AudioName", "audio_name", "FileName", "filename", "name")
                .ifBlank { audio?.optString("name").orEmpty() },
            kugouSingerName(item, "SingerName", "singername", "SingerName2", "author_name", "AuthorName")
                .ifBlank { item.optString("author_name") },
        )
        if (title.isBlank()) return null
        val albumName = firstString(item, "AlbumName", "album_name", "albumname")
            .ifBlank { albumInfo?.optString("album_name").orEmpty() }
        val albumId = firstString(item, "AlbumID", "album_id", "albumid")
            .ifBlank { albumInfo?.optString("album_id").orEmpty() }
            .takeIf(String::isNotBlank)
        val albumAudioId = firstLong(
            item, "album_audio_id", "MixSongID", "mixsongid", "AlbumAudioID", "Audioid", "audio_id",
        ).takeIf { it > 0L }
            ?: audio?.optString("audio_id")?.toLongOrNull()?.takeIf { it > 0L }
        val rawDuration = firstLong(item, "Duration", "duration", "time_length", "timelen")
            .takeIf { it > 0L }
            ?: audio?.optString("timelength")?.toLongOrNull()?.takeIf { it > 0L }
        val duration = rawDuration?.let { if (it > 10_000L) it else it * 1_000L }
        val artwork = kugouArtworkUrl(item)
        val artists = singer
            .split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("未知歌手") }
            .map { MusicArtistRef(name = it) }
        val quality = kugouSearchQuality(item)
        return MusicTrack(
            id = MusicResourceId(MusicSource.KugouLite, hash),
            title = title,
            artists = artists,
            album = albumName.takeIf(String::isNotBlank)?.let {
                MusicAlbumRef(
                    id = albumId?.let { value -> MusicResourceId(MusicSource.KugouLite, value) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = duration,
            availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.Kugou(
                hash = hash,
                albumAudioId = albumAudioId,
                albumId = albumId,
            ),
            bestQuality = quality?.tier,
            bestBitrate = quality?.bitrate,
        )
    }

    fun parsePrivilege(item: JSONObject, fallbackHash: String): KugouLitePrivilege {
        val relates = item.optJSONArray("relate_goods") ?: JSONArray()
        val goods = (0 until relates.length()).mapNotNull(relates::optJSONObject)
        val trans = item.optJSONObject("trans_param")
        val qualities = qualityOrder.map { quality ->
            val candidates = goods.filter { it.optString("quality") == quality }
            val preferredLevels = levelForQuality[quality].orEmpty()
            val relate = preferredLevels.asSequence()
                .mapNotNull { level -> candidates.firstOrNull { it.optInt("level", 0) == level } }
                .firstOrNull()
                ?: candidates.firstOrNull()
            val exists = relate != null
            val info = relate?.optJSONObject("info")
            val oggHash = when (quality) {
                "128" -> trans?.optString("ogg_128_hash").orEmpty()
                "320" -> trans?.optString("ogg_320_hash").orEmpty()
                else -> ""
            }
            val size = when (quality) {
                "128" -> trans?.optLong("ogg_128_filesize", 0L) ?: 0L
                "320" -> trans?.optLong("ogg_320_filesize", 0L) ?: 0L
                else -> info?.optLong("filesize", 0L) ?: 0L
            }
            val privilege = relate?.optInt("privilege", 0) ?: -1
            val status = relate?.optInt("status", 1) ?: 0
            val allowMessage = relate?.optString("_msg").orEmpty().lowercase()
            // privilege=8 is also used for free copyrighted tracks ("Allow: the
            // audio is free(copyright)"). Treat those as free instead of
            // mislabelling them as VIP-only.
            val needVip = exists && privilege != 0 && "free" !in allowMessage
            // The concept free-listen entitlement is granted by the playback
            // endpoint, so a static privilege row may still say status=0.
            val freeListen = quality == "128" && needVip
            KugouLiteQuality(
                quality = quality,
                exists = exists,
                level = relate?.optInt("level", 0) ?: 0,
                bitrate = info?.optInt("bitrate", 0) ?: 0,
                extname = oggHash.takeIf(String::isNotBlank)?.let { "ogg" }
                    ?: info?.optString("extname").orEmpty(),
                hash = oggHash.takeIf(String::isNotBlank)
                    ?: relate?.optString("hash").orEmpty(),
                size = size,
                privilege = privilege,
                status = status,
                payType = relate?.optInt("pay_type", -1) ?: -1,
                needVip = needVip,
                freeListen = freeListen,
                requireVipType = when {
                    !exists || !needVip || freeListen -> ""
                    quality in superVipQualities -> "suvip"
                    else -> "concept"
                },
            )
        }
        val freeSong = qualities.isNotEmpty() && qualities.all { !it.needVip }
        return KugouLitePrivilege(
            hash = item.optString("hash").ifBlank { fallbackHash },
            title = item.optString("name"),
            singer = item.optString("singername"),
            album = item.optString("albumname"),
            freeSong = freeSong,
            qualities = qualities,
        )
    }

    fun parseVip(root: JSONObject): KugouLiteVip {
        var vipType = 0
        var expires = 0L
        var isVip = false
        var superVip = false
        var freeListen = false
        flattenObjects(root).forEach { item ->
            vipType = maxOf(vipType, firstLong(item, "vip_type", "vipType").toInt().coerceAtLeast(0))
            val rawExpiry = firstLong(
                item, "vip_end_time", "vip_expire_time", "expire_time", "vip_end_time_ms", "end_time",
            )
            if (rawExpiry > 0) {
                expires = maxOf(expires, if (rawExpiry > 10_000_000_000L) rawExpiry / 1_000L else rawExpiry)
            }
            isVip = isVip || firstBoolean(item, "is_vip", "vip", "isVip", "vip_status")
            superVip = superVip || firstBoolean(item, "super_vip", "is_super_vip", "suvip", "svip")
            freeListen = freeListen || firstBoolean(item, "free_listen", "freeListen", "can_free_listen")
        }
        val now = System.currentTimeMillis() / 1_000L
        val active = isVip || vipType > 0 || expires > now
        return KugouLiteVip(
            isVip = active,
            superVip = superVip || vipType == 6,
            vipType = vipType,
            expiresAtEpochSeconds = expires,
            freeListen = freeListen,
        )
    }

    fun findPlaybackUrl(root: Any?): String? = when (root) {
        is JSONObject -> {
            listOf("url", "play_url", "playUrl", "backup_url", "backupUrl", "src")
                .asSequence()
                .mapNotNull { key -> root.opt(key)?.let(::findPlaybackUrl) }
                .firstOrNull()
                ?: root.keys().asSequence()
                    .filterNot { it in setOf("lyric", "cover", "img", "image") }
                    .mapNotNull { key -> findPlaybackUrl(root.opt(key)) }
                    .firstOrNull()
        }
        is JSONArray -> (0 until root.length()).asSequence()
            .mapNotNull { findPlaybackUrl(root.opt(it)) }
            .firstOrNull()
        is String -> root.takeIf(::isPlaybackUrl)
        else -> null
    }

    fun flattenObjects(root: Any?): List<JSONObject> = buildList {
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    add(value)
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
    }

    private fun isPlaybackUrl(value: String): Boolean {
        if (!value.startsWith("http://") && !value.startsWith("https://")) return false
        val ext = value.substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext !in setOf("avif", "bmp", "gif", "heic", "jpeg", "jpg", "png", "webp")
    }

    internal fun firstString(value: JSONObject, vararg keys: String): String =
        keys.asSequence().map(value::optString).firstOrNull(String::isNotBlank).orEmpty()

    internal fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun firstBoolean(value: JSONObject, vararg keys: String): Boolean =
        keys.asSequence().any { key ->
            when (val raw = value.opt(key)) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw == "1" || raw.equals("true", true)
                else -> false
            }
        }
}
