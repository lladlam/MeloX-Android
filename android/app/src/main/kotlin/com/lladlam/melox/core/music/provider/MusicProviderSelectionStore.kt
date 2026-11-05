package com.lladlam.melox.core.music.provider

import android.content.Context
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.SourceId

/**
 * Provider selection is local-only. Cross-provider aggregation is deliberately
 * opt-in and defaults to disabled. Even after the global experiment switch is
 * enabled, the source whitelist defaults to the current provider only; MeloX
 * never silently fans a request out to every installed provider.
 */
object MusicProviderSelectionStore {
    private val selectionRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = selectionRevision
    private const val PreferencesName = "melox_music_providers"
    private const val KeySelectedSource = "selected_source"
    private const val KeySelectedSourceId = "selected_source_id"

    fun selectedSourceId(context: Context): SourceId = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE).getString(KeySelectedSourceId, null)
        ?.let(SourceId::parse) ?: SourceId.fromBuiltIn(selectedSource(context))

    fun setSelectedSourceId(context: Context, source: SourceId) {
        source.builtIn?.let { setSelectedSource(context, it); return }
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE).edit()
            .putString(KeySelectedSourceId, source.value).apply()
        selectionRevision.value = selectionRevision.value + 1
    }
    private const val KeyUnifiedEnabled = "unified_enabled"
    private const val KeyAutomaticFallback = "automatic_source_fallback"
    private const val KeyUnifiedSources = "unified_sources"
    private const val KeyUnifiedPlugins = "unified_plugin_sources"

    fun unifiedSourceIds(context: Context): Set<SourceId> = unifiedSources(context).mapTo(linkedSetOf(), SourceId::fromBuiltIn) +
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getStringSet(KeyUnifiedPlugins, emptySet()).orEmpty().mapNotNull(SourceId::parse).filter { it.builtIn == null }

    fun setUnifiedPluginEnabled(context: Context, source: SourceId, enabled: Boolean) {
        require(source.builtIn == null)
        val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val values = preferences.getStringSet(KeyUnifiedPlugins, emptySet()).orEmpty().toMutableSet()
        if (enabled) { require(values.size < 32 || source.value in values); values.add(source.value) } else values.remove(source.value)
        preferences.edit().putStringSet(KeyUnifiedPlugins, values).apply()
    }

    /** Keep optional providers visible so their setup/login screen is reachable. */
    fun visibleSources(): List<MusicSource> =
        MusicSource.entries.filter { source ->
            source != MusicSource.AppleMusic &&
                source != MusicSource.Kugou &&
                source != MusicSource.Kuwo
        }

    fun selectedSource(context: Context): MusicSource {
        val preferences = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val stored = MusicSource.fromStorageValue(preferences.getString(KeySelectedSource, null))
        if (stored == MusicSource.Kugou) {
            // Switch only the selected source; standard and Lite credentials remain isolated.
            preferences.edit().putString(KeySelectedSource, MusicSource.KugouLite.storageValue).apply()
            return MusicSource.KugouLite
        }
        return stored
            .takeIf { it in visibleSources() }
            ?: MusicSource.Netease
    }

    fun setSelectedSource(context: Context, source: MusicSource) {
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeySelectedSource, (if (source == MusicSource.Kugou) MusicSource.KugouLite else source).storageValue)
            .putString(KeySelectedSourceId, (if (source == MusicSource.Kugou) MusicSource.KugouLite else source).storageValue)
            .apply()
        selectionRevision.value = selectionRevision.value + 1
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
        val safeSources = sources.filterTo(linkedSetOf()) { it in visibleSources() }
            .ifEmpty { setOf(selectedSource(context)) }
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
