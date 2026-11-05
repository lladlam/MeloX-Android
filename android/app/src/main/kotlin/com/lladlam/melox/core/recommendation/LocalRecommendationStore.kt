package com.lladlam.melox.core.recommendation

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

object LocalRecommendationStore {
    private const val PREFS = "local_recommendation"
    private const val TRACKS = "track_features"
    private const val PREFERENCE_VECTOR = "preference_vector"
    private const val PREFERENCE_UPDATED = "preference_updated_at"
    val progress = mutableStateOf(LocalAnalysisProgress())

    fun isAlgorithmEnabled(context: Context): Boolean = prefs(context).getBoolean("algorithm_enabled", false)
    fun setAlgorithmEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("algorithm_enabled", value).apply()
    fun hasPersonalizationConsent(context: Context): Boolean = prefs(context).getBoolean("personalization_consent", false)
    fun setPersonalizationConsent(context: Context, value: Boolean) = prefs(context).edit().putBoolean("personalization_consent", value).apply()
    fun consentAt(context: Context): Long = prefs(context).getLong("personalization_consent_at", 0L)
    fun setConsentAt(context: Context, value: Long) = prefs(context).edit().putLong("personalization_consent_at", value).apply()

    fun readFeatures(context: Context): List<LocalTrackFeatures> = runCatching {
        val array = JSONArray(prefs(context).getString(TRACKS, "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(LocalTrackFeatures(item.optString("trackKey"), item.optString("title"), item.optString("artist"), item.optInt("playCount"), item.optInt("completedCount"), item.optInt("skipCount"), item.optBoolean("liked"), item.optLong("lastPlayedAtMs"), item.optInt("sourceCount", 1), item.optString("versionKind", "Studio"), item.optDouble("qualityPreference", 0.0).toFloat()))
            }
        }
    }.getOrDefault(emptyList())

    fun writeFeatures(context: Context, features: List<LocalTrackFeatures>) {
        val array = JSONArray()
        features.forEach { item ->
            array.put(JSONObject().apply {
                put("trackKey", item.trackKey); put("title", item.title); put("artist", item.artist)
                put("playCount", item.playCount); put("completedCount", item.completedCount); put("skipCount", item.skipCount)
                put("liked", item.liked); put("lastPlayedAtMs", item.lastPlayedAtMs); put("sourceCount", item.sourceCount)
                put("versionKind", item.versionKind); put("qualityPreference", item.qualityPreference)
            })
        }
        prefs(context).edit().putString(TRACKS, array.toString()).apply()
    }

    fun recordPlayback(context: Context, trackKey: String, title: String, artist: String, completed: Boolean = false, skipped: Boolean = false) {
        val current = readFeatures(context).toMutableList()
        val index = current.indexOfFirst { it.trackKey == trackKey }
        val old = current.getOrNull(index) ?: LocalTrackFeatures(trackKey, title, artist)
        val updated = old.copy(
            playCount = old.playCount + if (!completed && !skipped) 1 else 0,
            completedCount = old.completedCount + if (completed) 1 else 0,
            skipCount = old.skipCount + if (skipped) 1 else 0,
            lastPlayedAtMs = System.currentTimeMillis(),
        )
        if (index >= 0) current[index] = updated else current += updated
        writeFeatures(context, current.takeLast(2_000))
        updatePreference(
            context = context,
            title = title,
            artist = artist,
            reward = when {
                skipped -> -1.5f
                completed -> 1.25f
                else -> .25f
            },
        )
    }

    fun writeRecommendations(context: Context, items: List<LocalRecommendationItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply { put("trackKey", item.trackKey); put("title", item.title); put("artist", item.artist); put("score", item.score); put("ruleScore", item.ruleScore); put("modelScore", item.modelScore); put("reason", item.reason) })
        }
        prefs(context).edit().putString("recommendations", array.toString()).apply()
    }

    fun readRecommendations(context: Context): List<LocalRecommendationItem> = runCatching {
        val array = JSONArray(prefs(context).getString("recommendations", "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(LocalRecommendationItem(item.optString("trackKey"), item.optString("title"), item.optString("artist"), item.optDouble("score").toFloat(), item.optDouble("ruleScore").toFloat(), item.optDouble("modelScore").toFloat(), item.optString("reason")))
            }
        }
    }.getOrDefault(emptyList())

    fun writeCandidateTracks(context: Context, tracks: List<com.lladlam.melox.core.music.model.MusicTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("source", track.id.sourceId.value); put("id", track.id.value); put("title", track.title)
                put("artist", track.artistText); put("artwork", track.artworkUrl); put("durationMs", track.durationMs ?: 0L)
                put("track", com.lladlam.melox.core.music.model.MeloXTrackJson.encode(track))
            })
        }
        prefs(context).edit().putString("candidate_tracks", array.toString()).apply()
    }

    fun readCandidateTracks(context: Context): List<com.lladlam.melox.core.music.model.MusicTrack> = runCatching {
        val array = JSONArray(prefs(context).getString("candidate_tracks", "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                decodeCandidate(item)?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

    internal fun decodeCandidate(item: JSONObject): com.lladlam.melox.core.music.model.MusicTrack? {
        val source = com.lladlam.melox.core.music.model.SourceId.parse(item.optString("source")) ?: return null
        val id = item.optString("id").takeIf(String::isNotBlank) ?: return null
        val identity = com.lladlam.melox.core.music.model.MusicResourceId(source, id)
        item.optJSONObject("track")?.let {
            return com.lladlam.melox.core.music.model.MeloXTrackJson.decode(it)?.takeIf { track -> track.id == identity }
        }
        return com.lladlam.melox.core.music.model.MusicTrack(identity, item.optString("title"),
            listOf(com.lladlam.melox.core.music.model.MusicArtistRef(name = item.optString("artist"))),
            artworkUrl = item.optString("artwork").takeIf(String::isNotBlank),
            durationMs = item.optLong("durationMs").takeIf { it > 0L },
            availability = com.lladlam.melox.core.music.model.TrackAvailability.Playable)
    }

    fun readRecommendedTracks(context: Context): List<com.lladlam.melox.core.music.model.MusicTrack> {
        val candidates = readCandidateTracks(context)
        val recommendations = readRecommendations(context)
        // 官方服务不提供黑名单读取接口，本地算法这份推荐是 MeloX 自己算出来的，
        // 所以必须在出口按服务剔除被屏蔽的歌曲/歌手——否则屏蔽只在官方链路生效，
        // 而 MeloX 独有的推荐里照样推。
        return recommendations.mapNotNull { recommendation ->
            candidates.firstOrNull { track ->
                track.title == recommendation.title && track.artistText == recommendation.artist
            }
        }
    }

    fun clearPersonalization(context: Context) {
        prefs(context).edit().remove(TRACKS).remove(PREFERENCE_VECTOR).remove(PREFERENCE_UPDATED).remove("model_weights").remove("recommendations").remove("candidate_tracks").remove("model_metadata").remove("personalization_consent").remove("personalization_consent_at").apply()
        progress.value = LocalAnalysisProgress()
    }

    fun preferenceVector(context: Context): FloatArray = runCatching {
        val array = JSONArray(prefs(context).getString(PREFERENCE_VECTOR, "[]").orEmpty())
        FloatArray(64) { index -> array.optDouble(index, 0.0).toFloat() }
    }.getOrDefault(FloatArray(64))

    fun updatePreference(context: Context, title: String, artist: String, reward: Float) {
        val vector = preferenceVector(context)
        val now = System.currentTimeMillis()
        val previous = prefs(context).getLong(PREFERENCE_UPDATED, 0L)
        val days = if (previous <= 0L) 0.0 else ((now - previous).coerceAtLeast(0L) / 86_400_000.0)
        val decay = kotlin.math.exp(kotlin.math.ln(.98) * days).toFloat().coerceIn(.25f, 1f)
        val sample = featureVector(title, artist)
        vector.indices.forEach { index ->
            vector[index] = ((vector[index] * decay) + .08f * reward * sample[index]).coerceIn(-1f, 1f)
        }
        val array = JSONArray()
        vector.forEach(array::put)
        prefs(context).edit().putString(PREFERENCE_VECTOR, array.toString()).putLong(PREFERENCE_UPDATED, now).apply()
    }

    fun featureVector(title: String, artist: String): FloatArray {
        val vector = FloatArray(64)
        (title.lowercase() + " " + artist.lowercase())
            .windowed(size = 2, step = 1, partialWindows = true)
            .forEach { token -> vector[(token.hashCode() and Int.MAX_VALUE) % vector.size] += 1f }
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
        return vector
    }

    fun writeModelMetadata(context: Context, metadata: LocalModelMetadata) {
        prefs(context).edit().putString("model_metadata", JSONObject().apply {
            put("version", metadata.version); put("sampleCount", metadata.sampleCount); put("trackCount", metadata.trackCount)
            put("trainedAtMs", metadata.trainedAtMs); put("backend", metadata.backend)
        }.toString()).apply()
    }

    fun readModelMetadata(context: Context): LocalModelMetadata = runCatching {
        val value = JSONObject(prefs(context).getString("model_metadata", "{}").orEmpty())
        LocalModelMetadata(value.optInt("version", 2), value.optInt("sampleCount"), value.optInt("trackCount"), value.optLong("trainedAtMs"), value.optString("backend", "本地线性模型"))
    }.getOrDefault(LocalModelMetadata())

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
