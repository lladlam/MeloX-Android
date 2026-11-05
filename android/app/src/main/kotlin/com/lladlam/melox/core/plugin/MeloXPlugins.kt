package com.lladlam.melox.core.plugin

import android.content.Context
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/** One lifecycle owner in the application process, using the same directory as playback registries. */
object MeloXPlugins {
    @Volatile private var manager: PluginManager? = null

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
