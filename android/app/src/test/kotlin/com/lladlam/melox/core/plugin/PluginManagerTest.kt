package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.*
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Exercises lifecycle orchestration; Android AtomicFile durability and Binder require device tests. */
class PluginManagerTest {
    private val id = SourceId.parse("plugin:org.example.test")!!
    private val builtin = object : MusicProvider {
        override val source = MusicSource.Netease
        override val displayName = "Built-in"
        override val capabilities = emptySet<MusicCapability>()
    }
    private fun registry(shared: DynamicMusicProviders) = MusicProviderRegistry(listOf(builtin), shared)

    @Test fun installEnableRestoreDisableAndUninstallUpdateSharedRegistry() {
        val repository = MemoryRepository()
        val shared = DynamicMusicProviders()
        val ui = registry(shared)
        val playback = registry(shared)
        var closed = 0
        val factory = PluginManager.ProviderFactory { plugin ->
            val provider = object : MusicProvider {
                override val sourceId = plugin.manifest.sourceId
                override val displayName = plugin.manifest.name
                override val capabilities = emptySet<MusicCapability>()
            }
            PluginManager.LoadedProvider(provider, Closeable { closed++ })
        }
        val manager = PluginManager(repository, ui, factory)
        manager.install(archive("1.0.0"))
        assertNull(playback[id])
        manager.enable(id)
        assertSame(ui[id], playback[id])
        assertEquals(setOf(id), repository.enabledSources())
        manager.update(id, archive("1.1.0"))
        assertEquals("1.1.0", manager.plugins.value[id]?.manifest?.version)
        assertEquals(1, closed)
        manager.close()
        assertNull(playback[id])
        assertEquals(setOf(id), repository.enabledSources())
        val restored = PluginManager(repository, ui, factory)
        restored.restore()
        assertNotNull(playback[id])
        restored.disable(id)
        assertNull(playback[id])
        assertTrue(repository.enabledSources().isEmpty())
        restored.uninstall(id)
        assertNull(repository.read(id))
        assertSame(builtin, playback[MusicSource.Netease])
        restored.close()
    }

    @Test fun failedPreparationLeavesPreviousPackageAndRunningProviderIntact() {
        val repository = MemoryRepository()
        val registry = registry(DynamicMusicProviders())
        val factory = PluginManager.ProviderFactory { plugin ->
            check(plugin.manifest.version != "2.0.0") { "invalid entry" }
            PluginManager.LoadedProvider(object : MusicProvider {
                override val sourceId = id
                override val displayName = "Test"
                override val capabilities = emptySet<MusicCapability>()
            }, Closeable {})
        }
        val manager = PluginManager(repository, registry, factory)
        manager.install(archive("1.0.0")); manager.enable(id)
        val previous = registry[id]
        assertThrows(IllegalStateException::class.java) { manager.update(id, archive("2.0.0")) }
        assertSame(previous, registry[id])
        assertEquals("1.0.0", repository.read(id)?.manifest?.version)
        assertThrows(IllegalArgumentException::class.java) { manager.update(id, archive("1.0.0")) }
        assertThrows(IllegalArgumentException::class.java) { manager.install(archive("1.0.0")) }
        manager.close()
    }

    @Test fun permissionExpansionNeedsApprovalBeforeCandidateExecutesAndCanBeCancelledWithoutMutation() {
        val repository = MemoryRepository()
        val registry = registry(DynamicMusicProviders())
        var preparations = 0
        val manager = PluginManager(repository, registry, PluginManager.ProviderFactory { plugin ->
            preparations++
            PluginManager.LoadedProvider(object : MusicProvider {
                override val sourceId = id
                override val displayName = "Test"
                override val capabilities = emptySet<MusicCapability>()
            }, Closeable {})
        })
        manager.install(archive("1.0.0")); manager.enable(id)
        val running = registry[id]
        val next = archive("2.0.0", setOf("api.example.com"))
        assertThrows(IllegalArgumentException::class.java) { manager.update(id, next) }
        assertEquals(1, preparations)
        assertSame(running, registry[id])
        assertEquals("1.0.0", repository.read(id)?.manifest?.version)
        manager.update(id, next, PluginArchiveReader.read(next).manifest)
        assertEquals(2, preparations)
        assertEquals("2.0.0", repository.read(id)?.manifest?.version)
        manager.close()
    }

    @Test fun accountCommitWithdrawsAndClosesPreviousProviderBeforeCredentialMutation() {
        val repository = MemoryRepository()
        val registry = registry(DynamicMusicProviders())
        var closed = 0
        var account = "first"
        val manager = PluginManager(repository, registry, PluginManager.ProviderFactory {
            PluginManager.LoadedProvider(object : MusicProvider {
                override val sourceId = id
                override val displayName = account
                override val capabilities = emptySet<MusicCapability>()
            }, Closeable { closed++ })
        })
        val manifest = manager.install(archive("1.0.0"))
        manager.enable(id)
        val previous = registry[id]
        manager.commitAccount(id, manifest) {
            assertNull(registry[id])
            assertEquals(1, closed)
            account = "second"
        }
        assertNotSame(previous, registry[id])
        assertEquals("second", registry[id]?.displayName)
        assertEquals(setOf(id), repository.enabledSources())
        manager.close()
    }

    @Test fun partiallyFailedAccountMutationNeverRepublishesPreviousProviderAndPreservesPrimaryFailure() {
        val repository = MemoryRepository()
        val registry = registry(DynamicMusicProviders())
        var failPrepare = false
        val manager = PluginManager(repository, registry, PluginManager.ProviderFactory {
            check(!failPrepare) { "restore failed" }
            PluginManager.LoadedProvider(object : MusicProvider {
                override val sourceId = id
                override val displayName = "Test"
                override val capabilities = emptySet<MusicCapability>()
            }, Closeable {})
        })
        val manifest = manager.install(archive("1.0.0"))
        manager.enable(id)
        val primary = IllegalStateException("selection write failed")
        val failure = assertThrows(IllegalStateException::class.java) {
            manager.changeAccount(id, manifest) {
                assertNull(registry[id])
                failPrepare = true
                throw primary
            }
        }
        assertSame(primary, failure)
        assertEquals("restore failed", failure.suppressed.single().message)
        assertNull(registry[id])
        assertEquals(PluginManager.Status.Failed, manager.plugins.value[id]?.status)
        assertEquals(setOf(id), repository.enabledSources())
        manager.close()
    }

    @Test fun disabledAccountMutationStaysDisabledAndStaleManifestDoesNotRunAction() {
        val repository = MemoryRepository()
        val registry = registry(DynamicMusicProviders())
        val manager = PluginManager(repository, registry, PluginManager.ProviderFactory { error("must not prepare") })
        val manifest = manager.install(archive("1.0.0"))
        var mutations = 0
        manager.changeAccount(id, manifest) { mutations++ }
        assertEquals(1, mutations)
        assertNull(registry[id])
        assertTrue(repository.enabledSources().isEmpty())
        assertThrows(IllegalStateException::class.java) {
            manager.commitAccount(id, manifest) { mutations++ }
        }
        val stale = PluginArchiveReader.read(archive("2.0.0")).manifest
        assertThrows(IllegalStateException::class.java) {
            manager.changeAccount(id, stale) { mutations++ }
        }
        assertEquals(1, mutations)
        manager.close()
    }

    private fun archive(version: String, domains: Set<String> = emptySet()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val manifest = """{"id":"org.example.test","name":"Test","version":"$version","apiVersion":1,"entry":"index.js","capabilities":[],"networkDomains":${org.json.JSONArray(domains.toList())}}"""
            listOf("manifest.json" to manifest, "index.js" to "globalThis.meloxPlugin = {}").forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private class MemoryRepository : PluginRepository {
        private val packages = mutableMapOf<SourceId, PluginArchiveReader.Package>()
        private var enabled = emptySet<SourceId>()
        override fun install(archive: ByteArray): PluginManifest {
            val plugin = PluginArchiveReader.read(archive)
            require(plugin.manifest.sourceId !in packages)
            packages[plugin.manifest.sourceId] = plugin
            return plugin.manifest
        }
        override fun read(sourceId: SourceId) = packages[sourceId]
        override fun update(sourceId: SourceId, archive: ByteArray, prepare: (PluginArchiveReader.Package) -> Unit): PluginManifest {
            val old = packages[sourceId] ?: error("missing package")
            val plugin = PluginArchiveReader.read(archive)
            require(plugin.manifest.sourceId == sourceId && PluginVersion.compare(plugin.manifest.version, old.manifest.version) > 0)
            prepare(plugin)
            packages[sourceId] = plugin
            return plugin.manifest
        }
        override fun installed() = packages.values.map { PluginPackageStore.Installed(it.manifest.sourceId, it.manifest, null) }
        override fun enabledSources() = enabled
        override fun setEnabledSources(sources: Set<SourceId>) { enabled = sources }
        override fun uninstall(sourceId: SourceId) { packages.remove(sourceId) }
    }
}
