package com.lladlam.melox.core.music.provider

import android.content.Context
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.music.model.MusicSource

/**
 * Provider selection is local-only. Cross-provider aggregation is deliberately
 * opt-in and defaults to disabled. Even after the global experiment switch is
 * enabled, the source whitelist defaults to the current provider only; MeloX
 * never silently fans a request out to every installed provider.
 */
object MusicProviderSelectionStore {
    private const val PreferencesName = "melox_music_providers"
    private const val KeySelectedSource = "selected_source"
    private const val KeyUnifiedEnabled = "unified_enabled"
    private const val KeyAutomaticFallback = "automatic_source_fallback"
    private const val KeyUnifiedSources = "unified_sources"

    /** Providers that need build-time credentials remain internal until configured. */
    fun visibleSources(): List<MusicSource> =
        MusicSource.entries.filter { source ->
            source != MusicSource.AppleMusic &&
                (source != MusicSource.Spotify || BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank())
        }

    fun selectedSource(context: Context): MusicSource {
        val preferences = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return MusicSource.fromStorageValue(preferences.getString(KeySelectedSource, null))
            .takeIf { it in visibleSources() }
            ?: MusicSource.Netease
    }

    fun setSelectedSource(context: Context, source: MusicSource) {
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeySelectedSource, source.storageValue)
            .apply()
    }

    fun unifiedEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getBoolean(KeyUnifiedEnabled, false)

    fun setUnifiedEnabled(context: Context, enabled: Boolean) {
        val preferences = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val editor = preferences.edit().putBoolean(KeyUnifiedEnabled, enabled)
        if (enabled && !preferences.contains(KeyUnifiedSources)) {
            editor.putStringSet(
                KeyUnifiedSources,
                setOf(selectedSource(context).storageValue),
            )
        }
        if (!enabled) {
            // Automatic fallback must never remain active when aggregation is off.
            editor.putBoolean(KeyAutomaticFallback, false)
        }
        editor.apply()
    }

    fun automaticFallbackEnabled(context: Context): Boolean =
        unifiedEnabled(context) && context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getBoolean(KeyAutomaticFallback, false)

    fun setAutomaticFallbackEnabled(context: Context, enabled: Boolean) {
        val safeEnabled = enabled && unifiedEnabled(context)
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KeyAutomaticFallback, safeEnabled)
            .apply()
    }

    fun unifiedSources(context: Context): Set<MusicSource> {
        val preferences = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val raw = preferences.getStringSet(KeyUnifiedSources, null)
        val parsed = raw?.mapNotNullTo(linkedSetOf()) { value ->
            visibleSources().firstOrNull { it.storageValue == value }
        }.orEmpty()
        return parsed.ifEmpty { linkedSetOf(selectedSource(context)) }
    }

    fun setUnifiedSources(context: Context, sources: Set<MusicSource>) {
        // An empty whitelist is normalized back to the current provider so a
        // single switch can never accidentally turn into an "all providers" request.
        val safeSources = sources.ifEmpty { setOf(selectedSource(context)) }
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KeyUnifiedSources, safeSources.mapTo(linkedSetOf()) { it.storageValue })
            .apply()
    }

    fun setUnifiedSourceEnabled(
        context: Context,
        source: MusicSource,
        enabled: Boolean,
    ): Set<MusicSource> {
        val updated = unifiedSources(context).toMutableSet().apply {
            if (enabled) add(source) else remove(source)
        }
        val normalized = updated.ifEmpty { mutableSetOf(selectedSource(context)) }
        setUnifiedSources(context, normalized)
        return normalized.toSet()
    }
}
