package com.lladlam.melox.core.plugin

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import com.lladlam.melox.core.music.model.SourceId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/** No user credentials or external API. Invoke using the documented runner Gradle property. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PluginRcDeviceRunner : Instrumentation() {
    private var online = false
    private var catalog = false
    private var faults = false
    private var crashStage: String? = null
    private var recoverStage: String? = null
    private var crashKind = "unsigned"
    private var oauthOrigin: String? = null
    private var appService = false
    private var previousCommand = false
    private var wireFields = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        online = arguments?.getString("online") == "true"
        catalog = arguments?.getString("catalog") == "true"
        faults = arguments?.getString("faults") == "true"
        crashStage = arguments?.getString("storageCrashStage")
        recoverStage = arguments?.getString("storageCrashRecover")
        crashKind = arguments?.getString("storageCrashKind") ?: "unsigned"
        oauthOrigin = arguments?.getString("oauthOrigin")
        appService = arguments?.getString("appService") == "true"
        previousCommand = arguments?.getString("previous") == "true"
        wireFields = arguments?.getString("wireFields") == "true"
        start()
    }
    override fun onStart() {
        val evidence = mutableListOf<String>()
        try {
            crashStage?.let { PluginRcCrashChecks.interrupt(this, it, crashKind) }
            recoverStage?.let { stage ->
                finish(Activity.RESULT_OK, Bundle().apply { putString("rcStatus", "PASS"); putString("result", PluginRcCrashChecks.recover(this@PluginRcDeviceRunner, stage, crashKind)) })
                return
            }
            // Restoring an enabled package validates it in the same isolated service.
            // Raw Binder probes must wait for that invocation before bypassing the host gate.
            MeloXPlugins.awaitRestored()
            PluginRuntime.withWorkerGate { messengerIdentity(evidence) }
            packageLifecycle(evidence)
            queuePersistence(evidence)
            runtimeAndStorage(evidence)
            vaultIsolation(evidence)
            if (faults) PluginRcFaultChecks.run(this, evidence) else evidence += "NOT_RUN isolated resource/storage fault matrix (use -e faults true)"
            oauthOrigin?.let {
                PluginRcNetworkFaultChecks.run(it, evidence)
                PluginRcMediaRevocationChecks.run(this, it, evidence)
                PluginRcDownloadChecks.run(this, it, evidence)
                PluginRcOAuthChecks.run(this, it, evidence)
            }
            if (appService) PluginRcAppServiceChecks.run(this, evidence)
            if (previousCommand) PluginRcPlaybackControlChecks.run(this, evidence)
            if (wireFields) PluginRcWireChecks.run(this, evidence)
            if (catalog) PluginRcCatalogChecks.run(this, evidence) else evidence += "NOT_RUN SDK catalog fixture (set MELOX_RC_CATALOG)"
            if (online) PluginRcOnlineChecks.run(this, evidence) else evidence += "NOT_RUN online Coil/Media3 fixtures (use -e online true)"
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", evidence.joinToString("\n")); putString("rcStatus", "PASS") })
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("rcStatus", "FAIL"); putString("completed", evidence.joinToString("\n"))
                putString("error", failure.javaClass.simpleName + ": " + failure.message?.take(300))
            })
        }
    }

    private fun queuePersistence(evidence: MutableList<String>) {
        val name = "plugin-rc-queue-${UUID.randomUUID()}"
        val sandbox = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences = targetContext.getSharedPreferences(name, mode)
        }
        val raw = org.json.JSONObject("""{"source":"plugin:org.example.missing","id":"opaque","title":"Track",
            "artists":[{"name":"Artist","artistSource":"plugin:org.example.missing","artistId":"artist","future":"keep"}],
            "album":{"name":"Album","albumSource":"plugin:org.example.missing","albumId":"album"}}""")
        val track = requireNotNull(com.lladlam.melox.core.music.model.MeloXTrackJson.decode(raw))
        val item = com.lladlam.melox.playback.ProviderPlaybackCommands.mediaItemFor(track)
        val player = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(androidx.media3.common.Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getMediaItemCount" -> 1
                "getMediaItemAt" -> item
                "getCurrentMediaItemIndex" -> 0
                "getCurrentPosition" -> 123L
                else -> error("unexpected Player call")
            }
        } as androidx.media3.common.Player
        try {
            com.lladlam.melox.playback.MeloXPlaybackQueueStore.save(sandbox, player, flush = true)
            val queue = requireNotNull(com.lladlam.melox.playback.MeloXPlaybackQueueStore.read(sandbox))
            val restored = requireNotNull(com.lladlam.melox.playback.PlaybackTrackIdentity.restoredTrack(queue.items.single()))
            check(restored.id == track.id && restored.album?.id == track.album?.id && restored.artists.single().id == track.artists.single().id)
            check(com.lladlam.melox.core.music.model.MeloXTrackJson.encode(restored).getJSONArray("artists").getJSONObject(0).getString("future") == "keep")
            check(queue.positionMs == 123L)
            evidence += "PASS actual queue preferences roundtrip preserves missing SourceId, nested identities/extensions and position (fake Player; not playback)"
        } finally { targetContext.deleteSharedPreferences(name) }
    }

    private fun packageLifecycle(evidence: MutableList<String>) {
        val root = java.io.File(targetContext.cacheDir, "plugin-rc-${UUID.randomUUID()}").apply { mkdirs() }
        val sandbox = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): java.io.File = root
        }
        val source = SourceId.parse("plugin:org.melox.rc.lifecycle")!!
        fun archive(version: String, script: String = "globalThis.meloxPlugin={};"): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip ->
                mapOf("manifest.json" to """{"id":"org.melox.rc.lifecycle","name":"RC","version":"$version","apiVersion":1,"entry":"index.js","capabilities":[]}""",
                    "index.js" to script).forEach { (name, content) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
            }
            return out.toByteArray()
        }
        val builtin = object : com.lladlam.melox.core.music.provider.MusicProvider {
            override val source = com.lladlam.melox.core.music.model.MusicSource.Netease
            override val displayName = "Test builtin"
            override val capabilities = emptySet<com.lladlam.melox.core.music.provider.MusicCapability>()
        }
        val registry = com.lladlam.melox.core.music.provider.MusicProviderRegistry(listOf(builtin))
        val store = PluginPackageStore(sandbox)
        val factory = PluginManager.ProviderFactory { pkg ->
            val runtime = PluginRuntime(sandbox, pkg)
            try {
                runBlocking { runtime.invoke("__validate") }
                PluginManager.LoadedProvider(PluginMusicProvider(pkg.manifest, runtime), runtime)
            } catch (error: Throwable) { runtime.close(); throw error }
        }
        try {
            PluginManager(store, registry, factory).use { manager ->
                manager.install(archive("1.0.0")); manager.enable(source)
                val active = registry[source]
                check(runCatching { manager.update(source, archive("2.0.0", "throw new Error('fixture');")) }.isFailure)
                check(registry[source] === active && store.read(source)?.manifest?.version == "1.0.0")
                manager.update(source, archive("2.0.0"))
                check(registry[source] !== active)
            }
            PluginManager(store, registry, factory).use { restored ->
                restored.restore(); check(registry[source] != null)
                restored.disable(source); check(registry[source] == null)
                val path = java.io.File(root, "melox-plugin-packages-v1/${PluginPackageTrust.sha256(source.value.toByteArray())}.plugin")
                val backup = java.io.File(path.path + ".bak")
                path.copyTo(backup); path.writeText("incomplete-write")
                check(store.read(source)?.manifest?.version == "2.0.0") // Actual AtomicFile backup recovery.
                path.writeText("corrupt-package")
                check(store.installed().any { it.manifest == null && it.storageKey != null })
                check(!path.exists())
                restored.install(archive("2.0.0")); check(source !in store.enabledSources())
                restored.uninstall(source); check(store.read(source) == null)
                check(store.publisherFingerprint(source) == PluginPackageTrust.UNSIGNED)
            }
            evidence += "PASS package install/enable/update, failed candidate rollback, restart restore, AtomicFile backup, quarantine/reimport, uninstall and retained trust"
        } finally { root.deleteRecursively() }
    }

    private fun messengerIdentity(evidence: MutableList<String>) {
        val done = CountDownLatch(1)
        val received = AtomicReference<Message>()
        val token = UUID.randomUUID().toString()
        val thread = HandlerThread("plugin-rc-test").apply { start() }
        val reply = Messenger(Handler(thread.looper) { received.set(Message.obtain(it)); done.countDown(); true })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Messenger(binder).send(Message.obtain(null, PluginIsolatedService.INVOKE).apply {
                    // Framework must overwrite this forged value using the actual Binder caller.
                    sendingUid = 12345
                    replyTo = reply
                    data = Bundle().apply {
                        putString("invocation", token); putString("method", "__validate"); putString("args", "[]")
                        putString("script", "globalThis.meloxPlugin = {search: () => null};")
                    }
                })
            }
            override fun onServiceDisconnected(name: ComponentName) { done.countDown() }
        }
        var bound = false
        try {
            val component = ComponentName(targetContext, PluginIsolatedService::class.java)
            val service = targetContext.packageManager.getServiceInfo(component, 0)
            check(!service.exported)
            bound = targetContext.bindService(Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
            check(bound && done.await(15, TimeUnit.SECONDS)) { "Messenger timeout" }
            val result = requireNotNull(received.get())
            check(result.what == PluginIsolatedService.RESULT && result.data.getString("invocation") == token)
            check(result.data.getString("json") == "[\"search\"]") {
                "worker rejected real caller or JS failed: ${result.data.getString("error")?.take(64)}"
            }
            check(result.sendingUid != Process.myUid() && result.sendingUid >= 0 && result.sendingUid != 12345)
            check(targetContext.checkPermission(android.Manifest.permission.INTERNET, -1, result.sendingUid) == PackageManager.PERMISSION_DENIED)
            evidence += "PASS Messenger framework UID=${result.sendingUid}, host UID=${Process.myUid()}, INTERNET denied, service not exported"
        } finally { if (bound) targetContext.unbindService(connection); thread.quitSafely() }
    }

    private fun runtimeAndStorage(evidence: MutableList<String>) = runBlocking {
        val id = "org.melox.rc.${UUID.randomUUID().toString().replace("-", "") }"
        val manifest = PluginManifestParser.parse("""{"id":"$id","name":"RC","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["search"]}""")
        val script = """globalThis.meloxPlugin = { search: async (q,p,s,host) => {
          if (q === 'hang') return new Promise(() => {});
          await host.storage.set('roundtrip','isolated');
          return {items:[],page:p,pageSize:s,hasMore:false,stored:await host.storage.get('roundtrip'),
            directNetwork: typeof fetch, javaBridge: typeof Java};
        }};"""
        try {
            PluginRuntime(targetContext, PluginArchiveReader.Package(manifest, script)).use { runtime ->
                val result = org.json.JSONObject(runtime.invoke("search", JSONArray().put("ok").put(1).put(10)))
                check(result.getString("stored") == "isolated" && result.getString("directNetwork") == "undefined" && result.getString("javaBridge") == "undefined")
                check(runCatching { runtime.invoke("search", JSONArray().put("hang").put(1).put(10)) }.isFailure)
                val recovered = runtime.invoke("search", JSONArray().put("after-timeout").put(1).put(10))
                check(org.json.JSONObject(recovered).getString("stored") == "isolated")
                val cancelled = runCatching { kotlinx.coroutines.withTimeout(200) {
                    runtime.invoke("search", JSONArray().put("hang").put(1).put(10))
                } }.exceptionOrNull()
                check(cancelled is kotlinx.coroutines.TimeoutCancellationException) { "runtime cancellation did not propagate" }
                val afterCancel = runtime.invoke("search", JSONArray().put("after-cancel").put(1).put(10))
                check(org.json.JSONObject(afterCancel).getString("stored") == "isolated")
            }
            evidence += "PASS QuickJS Promise, storage Broker, no fetch/Java bridge, watchdog, coroutine cancellation and rebind"
        } finally { PluginStorage.clear(targetContext, manifest.sourceId, emptyList()) }
    }

    private fun vaultIsolation(evidence: MutableList<String>) {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val first = PluginAccountVault(targetContext, SourceId.parse("plugin:org.melox.rc.a$suffix")!!)
        val second = PluginAccountVault(targetContext, SourceId.parse("plugin:org.melox.rc.b$suffix")!!)
        val policy = PluginAuthentication("apiToken", domains = setOf("example.com"))
        try {
            val a = first.importToken("Synthetic A", "one-time-test-a", policy)
            val b = first.importToken("Synthetic B", "one-time-test-b", policy)
            first.activate(a.id); val lease = requireNotNull(first.lease())
            check(lease.token == "one-time-test-a")
            first.activate(b.id); check(first.lease()?.token == "one-time-test-b")
            first.activate(a.id); check(first.sessionStamp() != lease.selectionStamp)
            check(second.accounts().isEmpty() && runCatching { second.activate(a.id) }.isFailure)
            evidence += "PASS AndroidKeyStore encrypt/decrypt, plugin/account isolation, A-B-A session revocation"
        } finally { first.clear(); second.clear() }
    }
}
