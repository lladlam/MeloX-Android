package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle owner. All calls perform disk/runtime work and must run off the main thread. */
class PluginManager(
    private val store: PluginRepository,
    private val registry: MusicProviderRegistry,
    private val factory: ProviderFactory,
) : Closeable {
    fun interface ProviderFactory {
        /** Must fully load/check the entry and declared capabilities before returning. */
        fun prepare(plugin: PluginArchiveReader.Package): LoadedProvider
    }

    data class LoadedProvider(val provider: MusicProvider, val resources: Closeable)
    enum class Status { Disabled, Enabled, Failed }
    data class State(val manifest: PluginManifest, val status: Status, val error: String? = null)

    private val lock = Any()
    private val loaded = mutableMapOf<SourceId, LoadedProvider>()
    private val state = MutableStateFlow<Map<SourceId, State>>(emptyMap())
    val plugins = state.asStateFlow()
    fun diagnostics(source: SourceId): List<String> = synchronized(lock) {
        val entry = state.value[source] ?: return@synchronized listOf("插件未安装：${source.value}")
        buildList {
            add("${source.value} · ${entry.manifest.version} · API ${entry.manifest.apiVersion} · ${entry.status}")
            entry.error?.let { add("宿主错误类型：$it") }
            val runtime = loaded[source]?.resources as? PluginRuntime
            runtime?.logs()?.takeLast(30)?.forEach { add("${it.level}: ${it.message}") }
            if (runtime == null) add("运行时未加载")
        }
    }
    private var closed = false

    fun install(archive: ByteArray): PluginManifest = synchronized(lock) {
        checkOpen()
        val manifest = store.install(archive)
        publish(State(manifest, Status.Disabled))
        manifest
    }

    fun restore(): List<PluginPackageStore.Installed> = synchronized(lock) {
        checkOpen()
        val installed = store.installed()
        val desired = store.enabledSources()
        installed.forEach { item ->
            val manifest = item.manifest ?: return@forEach
            if (manifest.sourceId !in loaded) {
                publish(State(manifest, Status.Disabled))
                if (manifest.sourceId in desired) {
                    try { enable(manifest.sourceId) }
                    catch (error: Exception) { publish(State(manifest, Status.Failed, error.javaClass.simpleName)) }
                }
            }
        }
        installed
    }

    fun enable(source: SourceId) = synchronized(lock) {
        checkOpen()
        if (source in loaded) return@synchronized
        val plugin = store.read(source) ?: error("plugin is not installed")
        val candidate = try { factory.prepare(plugin) }
        catch (error: Exception) {
            publish(State(plugin.manifest, Status.Failed, error.javaClass.simpleName))
            throw error
        }
        try {
            require(candidate.provider.sourceId == source) { "provider identity mismatch" }
            registry.registerDynamic(candidate.provider, enabled = false)
            try {
                store.setEnabledSources(store.enabledSources() + source)
            } catch (error: Throwable) {
                registry.unregisterDynamic(source)
                throw error
            }
            loaded[source] = candidate
            registry.setDynamicEnabled(source, true)
            publish(State(plugin.manifest, Status.Enabled))
        } catch (error: Throwable) {
            runCatching { candidate.resources.close() }
            publish(State(plugin.manifest, Status.Failed, error.javaClass.simpleName))
            throw error
        }
    }

    fun disable(source: SourceId) = synchronized(lock) {
        checkOpen()
        PluginOAuth.cancel(source)
        // A failed durable state write leaves the running provider unchanged.
        store.setEnabledSources(store.enabledSources() - source)
        registry.unregisterDynamic(source)
        loaded.remove(source)?.let { runCatching { it.resources.close() } }
        state.value[source]?.let { publish(it.copy(status = Status.Disabled, error = null)) }
    }

    fun update(source: SourceId, archive: ByteArray, approvedManifest: PluginManifest? = null): PluginManifest = synchronized(lock) {
        checkOpen()
        PluginOAuth.cancel(source)
        val previous = loaded[source]
        var candidate: LoadedProvider? = null
        try {
            val manifest = store.update(source, archive) { plugin ->
                val prior = store.read(source)?.manifest ?: error("plugin is not installed")
                require(!PluginPermissions.expands(prior, plugin.manifest) || approvedManifest == plugin.manifest) {
                    "PLUGIN_PERMISSION_APPROVAL_REQUIRED"
                }
                // Check even disabled upgrades so a broken entry does not replace the recoverable package.
                candidate = factory.prepare(plugin)
                require(candidate!!.provider.sourceId == source) { "provider identity mismatch" }
                val validation = com.lladlam.melox.core.music.provider.DynamicMusicProviders()
                validation.register(candidate!!.provider)
            }
            if (previous != null) {
                registry.replaceDynamic(candidate!!.provider)
                loaded[source] = candidate!!
                candidate = null
                runCatching { previous.resources.close() }
            }
            publish(State(manifest, if (previous != null) Status.Enabled else Status.Disabled))
            manifest
        } finally {
            candidate?.let { runCatching { it.resources.close() } }
        }
    }

    fun uninstall(source: SourceId) = synchronized(lock) {
        disable(source)
        store.uninstall(source)
        state.value = state.value - source
    }

    /** Revoke in-flight requests and playback generation after an account change. */
    fun accountChanged(source: SourceId) = synchronized(lock) {
        checkOpen()
        PluginOAuth.cancel(source)
        val old = loaded.remove(source) ?: return@synchronized
        registry.unregisterDynamic(source)
        runCatching { old.resources.close() }
        enable(source)
    }

    internal fun commitAccount(source: SourceId, expected: PluginManifest, action: () -> Unit) = synchronized(lock) {
        checkOpen()
        check(state.value[source]?.manifest == expected && source in loaded) { "plugin changed during login" }
        action()
        accountChanged(source)
    }

    /** Process shutdown frees runtime resources without altering the desired enabled state. */
    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        loaded.forEach { (source, item) ->
            registry.unregisterDynamic(source)
            runCatching { item.resources.close() }
        }
        loaded.clear()
        state.value = emptyMap()
    }

    private fun publish(value: State) { state.value = state.value + (value.manifest.sourceId to value) }
    private fun checkOpen() { check(!closed) { "plugin manager is closed" } }
}
