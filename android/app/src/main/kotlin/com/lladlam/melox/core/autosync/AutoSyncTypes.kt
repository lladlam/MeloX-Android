package com.lladlam.melox.core.autosync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsed `manifest.json` that ships next to a converted AutoSync model.
 *
 * The exporter owns this contract (see MeloX-AutoSync
 * `docs/ANDROID_ONNX_EXPORT_PROMPT.md`); the device only reads it. Every field
 * has a defensive default so a future manifest revision cannot crash playback.
 */
/** One runtime file recorded in the local manifest, used to detect updates. */
data class AutoSyncFileDigest(
    val name: String,
    val bytes: Long,
    val sha256: String,
)

data class AutoSyncManifest(
    val language: String,
    val sourceModel: String,
    val license: String,
    val sampleRate: Int,
    val frameHopSeconds: Double,
    val inputName: String,
    val tokenCountName: String?,
    val logProbOutputName: String,
    val logitsOutputName: String?,
    val blankId: Int,
    val wordDelimiterId: Int,
    val unkId: Int,
    val tokens: List<String>,
    val modelFileName: String,
    /** `int8` (default) or `precision` when the larger variant is installed. */
    val modelVariant: String = "int8",
    val precisionModelFileName: String? = null,
    /** Required runtime files as installed; compared with the published index. */
    val requiredFiles: List<AutoSyncFileDigest> = emptyList(),
) {
    val frameHopMs: Long get() = (frameHopSeconds * 1000.0).toLong()

    companion object {
        fun parse(text: String): AutoSyncManifest {
            val root = JSONObject(text)
            val inputs = root.optJSONArray("inputs")
            val outputs = root.optJSONArray("outputs")
            // Schema 1 (English) uses singular `input`/`outputs`; schema .zh uses
            // `inputs`. Accept both rather than keying the device off the language.
            val inputName = inputs?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
                ?: root.optJSONObject("input")?.optString("name")?.takeIf { it.isNotBlank() }
                ?: "input_values"
            var logProbName = "log_probs"
            var logitsName: String? = "logits"
            if (outputs != null) {
                for (index in 0 until outputs.length()) {
                    val name = outputs.optJSONObject(index)?.optString("name").orEmpty()
                    when {
                        name.contains("log_prob", ignoreCase = true) -> logProbName = name
                        name.contains("logit", ignoreCase = true) -> logitsName = name
                    }
                }
                if (logProbName == "log_probs") {
                    val firstName = outputs.optJSONObject(0)?.optString("name").orEmpty()
                    if (firstName.isNotBlank() && !firstName.contains("log_prob", true) &&
                        !firstName.contains("logit", true)
                    ) {
                        // Chinese contract: us_alphas/us_peaks, not CTC logits.
                        logitsName = null
                    }
                }
            }
            val specials = root.optJSONObject("special_tokens")
            val tokensJson = root.optJSONArray("tokens") ?: JSONArray()
            val tokens = List(tokensJson.length()) { tokensJson.optString(it) }
            val unkId = specials?.optInt("<unk>", -1)?.takeIf { it >= 0 }
                ?: tokens.indexOf("<unk>").takeIf { it >= 0 }
                ?: 3
            val frame = root.optJSONObject("frame")
            val frameHopSeconds = root.optDouble("frame_hop_seconds", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 }
                ?: (frame?.optDouble("decoded_frame_ms", Double.NaN)
                    ?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0))
                ?: 0.02
            val files = root.optJSONObject("files")
            val variant = files?.optString("variant").orEmpty().ifBlank { "int8" }
            val int8 = files?.optJSONObject("int8")
            val precision = files?.optJSONObject("precision")
            val chosen = when (variant) {
                "precision" -> precision ?: int8
                else -> int8 ?: precision
            }
            val modelFileName = chosen?.optString("name").orEmpty().ifBlank { "model-int8.onnx" }
            val requiredFiles = files?.optJSONArray("required")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val entry = array.optJSONObject(index) ?: continue
                        val name = entry.optString("name")
                        if (name.isBlank()) continue
                        add(
                            AutoSyncFileDigest(
                                name = name,
                                bytes = entry.optLong("bytes", -1L),
                                sha256 = entry.optString("sha256").lowercase(),
                            ),
                        )
                    }
                }
            }.orEmpty()

            return AutoSyncManifest(
                language = root.optString("language", "en"),
                sourceModel = root.optString("source_model", ""),
                license = root.optString("license", ""),
                sampleRate = root.optInt("sample_rate", 16_000),
                frameHopSeconds = frameHopSeconds,
                inputName = inputName,
                tokenCountName = inputs?.let { array ->
                    (0 until array.length())
                        .mapNotNull { array.optJSONObject(it)?.optString("name") }
                        .firstOrNull { it != inputName && it.contains("token", ignoreCase = true) }
                },
                logProbOutputName = logProbName,
                logitsOutputName = logitsName,
                blankId = root.optInt("blank_id", 0),
                wordDelimiterId = root.optInt("word_delimiter_id", -1),
                unkId = unkId,
                tokens = tokens,
                modelFileName = modelFileName,
                modelVariant = if (variant == "precision" && precision != null) "precision" else "int8",
                precisionModelFileName = precision?.optString("name")?.takeIf(String::isNotBlank),
                requiredFiles = requiredFiles,
            )
        }
    }
}

/** Language routed per lyric line, so mixed songs use the right engine per line. */
enum class AutoSyncLineLanguage(val tag: String) {
    Zh("zh"),
    En("en"),
    Ja("ja"),
    Unknown("und"),
}

/** One generated display unit (English: a word) in absolute song time. */
data class AutoSyncUnit(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** Generated word timing for a single source lyric line. */
data class AutoSyncLineTiming(
    val index: Int,
    val units: List<AutoSyncUnit>,
    val meanScore: Double,
)

/** A complete generated word-level timeline for one track. */
data class AutoSyncTimeline(
    val trackKey: String,
    /** Identity of the line-level document this timeline was generated from. */
    val sourceHash: String,
    val language: String,
    val engine: String,
    val lines: List<AutoSyncLineTiming>,
    val createdAtMs: Long,
    val modelKey: String = "",
    /** Identity of the cached audio the timeline was generated from. */
    val audioKey: String = "",
) {
    fun toJson(): JSONObject {
        val linesJson = JSONArray()
        for (line in lines) {
            val unitsJson = JSONArray()
            for (unit in line.units) {
                unitsJson.put(
                    JSONObject()
                        .put("t", unit.text)
                        .put("s", unit.startMs)
                        .put("e", unit.endMs),
                )
            }
            linesJson.put(
                JSONObject()
                    .put("i", line.index)
                    .put("score", line.meanScore)
                    .put("u", unitsJson),
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("track", trackKey)
            .put("hash", sourceHash)
            .put("lang", language)
            .put("engine", engine)
            .put("model_key", modelKey)
            .put("audio_key", audioKey)
            .put("created", createdAtMs)
            .put("lines", linesJson)
    }

    companion object {
        fun fromJson(root: JSONObject): AutoSyncTimeline {
            val linesJson = root.optJSONArray("lines") ?: JSONArray()
            val lines = ArrayList<AutoSyncLineTiming>(linesJson.length())
            for (index in 0 until linesJson.length()) {
                val lineJson = linesJson.optJSONObject(index) ?: continue
                val unitsJson = lineJson.optJSONArray("u") ?: JSONArray()
                val units = ArrayList<AutoSyncUnit>(unitsJson.length())
                for (unitIndex in 0 until unitsJson.length()) {
                    val unit = unitsJson.optJSONObject(unitIndex) ?: continue
                    val text = unit.optString("t")
                    if (text.isEmpty()) continue
                    units.add(
                        AutoSyncUnit(
                            text = text,
                            startMs = unit.optLong("s"),
                            endMs = unit.optLong("e"),
                        ),
                    )
                }
                lines.add(
                    AutoSyncLineTiming(
                        index = lineJson.optInt("i", index),
                        units = units,
                        meanScore = lineJson.optDouble("score", 0.0),
                    ),
                )
            }
            return AutoSyncTimeline(
                trackKey = root.optString("track"),
                sourceHash = root.optString("hash"),
                language = root.optString("lang", "en"),
                engine = root.optString("engine", ""),
                lines = lines,
                createdAtMs = root.optLong("created"),
                modelKey = root.optString("model_key"),
                audioKey = root.optString("audio_key"),
            )
        }
    }
}
