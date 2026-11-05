package com.lladlam.melox.core.plugin

import android.content.Context
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.json.JSONArray

/** One lifecycle owner in the application process, using the same directory as playback registries. */
object MeloXPlugins {
    @Volatile private var manager: PluginManager? = null
    @Volatile private var restoration: java.util.concurrent.CompletableFuture<Unit>? = null
    private val restoreScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Install the startup gate before activities/services can resolve a persisted plugin queue. */
    fun start(context: Context) = synchronized(this) {
        if (restoration != null) return@synchronized
        val ready = java.util.concurrent.CompletableFuture<Unit>()
        restoration = ready
        val owner = get(context)
        restoreScope.launch {
            try { owner.restore(); ready.complete(Unit) }
            catch (error: Throwable) { ready.completeExceptionally(error) }
        }
    }

    /** Resolver IO threads only. Failure is explicit; it never selects a built-in substitute. */
    fun awaitRestored() {
        try { restoration?.get(20, java.util.concurrent.TimeUnit.SECONDS) }
        catch (error: Exception) { throw java.io.IOException("plugin startup restore is unavailable", error) }
    }

    fun get(context: Context): PluginManager = manager ?: synchronized(this) {
        manager ?: create(context.applicationContext).also { manager = it }
    }

    private fun create(app: Context): PluginManager = PluginManager(
        PluginPackageStore(app), MeloXMusicProviders.create(app),
        PluginManager.ProviderFactory { plugin ->
            val runtime = PluginRuntime(app, plugin)
            try {
                val methods = runBlocking(Dispatchers.IO) { JSONArray(runtime.invoke("__validate")) }
                val available = (0 until methods.length()).map { methods.getString(it) }.toSet()
                plugin.manifest.capabilities.forEach { capability ->
                    require(available.containsAll(capability.methods)) { "declared plugin method is missing: ${capability.wireName}" }
                }
                PluginManager.LoadedProvider(PluginMusicProvider(plugin.manifest, runtime), runtime)
            } catch (error: Throwable) { runtime.close(); throw error }
        },
    )
}
