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
    data class State(val manifest: PluginManifest, val status: Status, val error: String? = null,
        val publisher: PluginPackageTrust.Publisher? = null, val archiveSha256: String = "", val integrityChecked: Boolean = false)

    private val lock = Any()
    private val loaded = mutableMapOf<SourceId, LoadedProvider>()
    private val state = MutableStateFlow<Map<SourceId, State>>(emptyMap())
    val plugins = state.asStateFlow()
    private val damaged = MutableStateFlow<List<PluginPackageStore.Installed>>(emptyList())
    val damagedPackages = damaged.asStateFlow()
    private val recoveryIssue = MutableStateFlow<String?>(null)
    val recoveryError = recoveryIssue.asStateFlow()
    fun resetEnabledState() = synchronized(lock) {
        checkOpen()
        store.setEnabledSources(emptySet())
        loaded.forEach { (source, item) -> registry.unregisterDynamic(source); runCatching { item.resources.close() } }
        loaded.clear()
        recoveryIssue.value = null
        restore()
    }
    fun publisherFingerprint(source: SourceId): String? = synchronized(lock) { store.publisherFingerprint(source) }
    fun dismissDamaged(storageKey: String) = synchronized(lock) {
        store.dismissDamaged(storageKey)
        damaged.value = damaged.value.filterNot { it.storageKey == storageKey }
    }
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

    fun install(archive: ByteArray, approvedPublisherArchive: String? = null): PluginManifest = synchronized(lock) {
        checkOpen()
        val candidate = PluginArchiveReader.read(archive)
        require(store.read(candidate.manifest.sourceId) == null) { "plugin already installed" }
        val remembered = store.publisherFingerprint(candidate.manifest.sourceId)
        require(remembered == null || remembered == (candidate.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED) || approvedPublisherArchive == candidate.archiveSha256) {
            "PUBLISHER_CHANGE_APPROVAL_REQUIRED"
        }
        store.setEnabledSources(store.enabledSources() - candidate.manifest.sourceId)
        val manifest = store.install(archive)
        publish(State(manifest, Status.Disabled))
        manifest
    }

    fun restore(): List<PluginPackageStore.Installed> = synchronized(lock) {
        checkOpen()
        val installed = store.installed()
        damaged.value = installed.filter { it.manifest == null }
        val desired = try { store.enabledSources() } catch (error: Exception) {
            recoveryIssue.value = "插件启用状态无法读取：${error.javaClass.simpleName}。包和用户数据已保留，可重置为全部禁用。"
            emptySet()
        }
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
        val pinned = store.publisherFingerprint(source)
        require(pinned == null || pinned == (plugin.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED)) { "PUBLISHER_RECORD_MISMATCH" }
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

    fun update(source: SourceId, archive: ByteArray, approvedManifest: PluginManifest? = null, approvedPublisherArchive: String? = null): PluginManifest = synchronized(lock) {
        checkOpen()
        PluginOAuth.cancel(source)
        val previous = loaded[source]
        var candidate: LoadedProvider? = null
        try {
            val manifest = store.update(source, archive) { plugin ->
                val priorPackage = store.read(source) ?: error("plugin is not installed")
                PluginPackageTrust.requireUpdate(priorPackage, plugin, approvedPublisherArchive)
                val prior = priorPackage.manifest
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

    private fun publish(value: State) {
        val installed = store.read(value.manifest.sourceId)
        state.value = state.value + (value.manifest.sourceId to value.copy(publisher = installed?.publisher,
            archiveSha256 = installed?.archiveSha256.orEmpty(), integrityChecked = installed?.integrityChecked == true))
    }
    private fun checkOpen() { check(!closed) { "plugin manager is closed" } }
}
