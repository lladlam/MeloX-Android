package com.lladlam.melox.core.lyrics

import android.content.Context
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.SourceId
import org.json.JSONObject

enum class BoundLyricSource { AmlL, Provider }

data class LyricBinding(
    val source: BoundLyricSource,
    val provider: MusicSource? = null,
    val resourceValue: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val providerSourceId: SourceId? = provider?.let(SourceId::fromBuiltIn),
) {
    fun stableKey(): String = listOf(source.name, providerSourceId?.value.orEmpty(), resourceValue).joinToString(":")
}

object LyricBindingStore {
    private const val PreferencesName = "melox_lyric_bindings"

    fun read(context: Context, playbackId: MusicResourceId): LyricBinding? {
        val raw = preferences(context).getString(playbackId.key(), null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            LyricBinding(
                source = BoundLyricSource.valueOf(value.getString("source")),
                provider = SourceId.parse(value.optString("provider"))?.builtIn,
                resourceValue = value.getString("resourceValue"),
                title = value.optString("title"),
                artist = value.optString("artist"),
                durationMs = value.optLong("durationMs"),
                providerSourceId = SourceId.parse(value.optString("provider")),
            )
        }.getOrNull()
    }

    fun write(context: Context, playbackId: MusicResourceId, binding: LyricBinding) {
        val value = JSONObject()
            .put("source", binding.source.name)
            .put("provider", binding.providerSourceId?.value ?: "")
            .put("resourceValue", binding.resourceValue)
            .put("title", binding.title)
            .put("artist", binding.artist)
            .put("durationMs", binding.durationMs)
        preferences(context).edit().putString(playbackId.key(), value.toString()).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private fun MusicResourceId.key(): String = "${sourceId.value}:$value"
}
