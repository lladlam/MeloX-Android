package com.lladlam.melox.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object MeloXAudioAnalysisPreferences {
    private const val NAME = "melox_audio_analysis"
    private const val PERSISTENT = "persistent_cache"
    private const val INDEPENDENT = "independent_line"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun persistentEnabled(context: Context): Boolean = prefs(context).getBoolean(PERSISTENT, false)
    fun independentLineEnabled(context: Context): Boolean = prefs(context).getBoolean(INDEPENDENT, false)
    fun setPersistentEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(PERSISTENT, value).apply()
    fun setIndependentLineEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(INDEPENDENT, value).apply()
}

/** Durable index for completed AutoMix analysis; audio files are never retained here. */
class MeloXAudioAnalysisStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "automix_analysis_index.json")
    private val lock = Any()

    fun get(key: String): MeloXAutoMixTrackAnalysis? = synchronized(lock) {
        val root = readRoot() ?: return@synchronized null
        root.optJSONObject(digest(key))?.toAnalysis()
    }

    fun put(key: String, analysis: MeloXAutoMixTrackAnalysis) = synchronized(lock) {
        val root = readRoot() ?: JSONObject()
        root.put(digest(key), analysis.toJson())
        val temporary = File(file.parentFile, "${file.name}.part")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Unable to publish audio analysis index" }
        }
    }

    fun clear() = synchronized(lock) { file.delete() }

    private fun readRoot(): JSONObject? = runCatching {
        if (file.isFile) JSONObject(file.readText()) else null
    }.getOrNull()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun MeloXAutoMixTrackAnalysis.toJson(): JSONObject = JSONObject().apply {
    put("bpm", bpm)
    put("confidence", confidence)
    put("firstAudibleMs", firstAudibleMs)
    put("lastAudibleMs", lastAudibleMs)
    put("beatTimesMs", JSONArray(beatTimesMs.toList()))
    put("downbeatTimesMs", JSONArray(downbeatTimesMs.toList()))
    put("phraseBoundariesMs", JSONArray(phraseBoundariesMs.toList()))
    put("frames", JSONArray(frames.map { frame ->
        JSONObject().apply {
            put("timeMs", frame.timeMs)
            put("energy", frame.energy)
            put("lowRatio", frame.lowRatio)
            put("midRatio", frame.midRatio)
            put("highRatio", frame.highRatio)
            put("novelty", frame.novelty)
            put("onset", frame.onset)
        }
    }))
}

private fun JSONObject.toAnalysis(): MeloXAutoMixTrackAnalysis {
    fun longArray(key: String) = LongArray(optJSONArray(key)?.length() ?: 0) { index ->
        optJSONArray(key)?.optLong(index) ?: 0L
    }
    val frameArray = optJSONArray("frames")
    val frames = List(frameArray?.length() ?: 0) { index ->
        val item = frameArray?.optJSONObject(index) ?: JSONObject()
        MeloXAutoMixFrame(
            timeMs = item.optLong("timeMs"),
            energy = item.optDouble("energy").toFloat(),
            lowRatio = item.optDouble("lowRatio").toFloat(),
            midRatio = item.optDouble("midRatio").toFloat(),
            highRatio = item.optDouble("highRatio").toFloat(),
            novelty = item.optDouble("novelty").toFloat(),
            onset = item.optDouble("onset").toFloat(),
        )
    }
    return MeloXAutoMixTrackAnalysis(
        bpm = optDouble("bpm"),
        confidence = optDouble("confidence"),
        firstAudibleMs = optLong("firstAudibleMs"),
        lastAudibleMs = optLong("lastAudibleMs"),
        beatTimesMs = longArray("beatTimesMs"),
        downbeatTimesMs = longArray("downbeatTimesMs"),
        phraseBoundariesMs = longArray("phraseBoundariesMs"),
        frames = frames,
    )
}
