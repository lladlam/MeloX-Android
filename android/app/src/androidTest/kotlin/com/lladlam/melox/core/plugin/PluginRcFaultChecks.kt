package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import com.lladlam.melox.core.music.model.SourceId
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Opt-in destructive-to-test-worker faults only. No user package/account is changed. */
internal object PluginRcFaultChecks {
    fun run(test: Instrumentation, evidence: MutableList<String>) = runBlocking {
        workerDeathAndReplay(test, evidence)
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val manifest = PluginManifestParser.parse("""{"id":"org.melox.rc.fault$suffix","name":"RC faults","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["search"]}""")
        val bodies = mapOf(
            "native synchronous loop" to "while(true) {}",
            "heap exhaustion" to "const a=[]; for(;;) a.push(new Uint8Array(1024*1024));",
            "recursive stack" to "function recur(){return 1+recur()}; recur();",
            "deep JSON result" to "return '['.repeat(40)+'0'+']'.repeat(40);",
        )
        bodies.forEach { (name, body) ->
            val script = "globalThis.meloxPlugin={search:()=>{$body}};"
            PluginRuntime(test.targetContext, PluginArchiveReader.Package(manifest, script)).use { runtime ->
                val attempt = runCatching { runtime.invoke("search", JSONArray().put("fixture").put(1).put(10)) }
                // Deep string is a legal JSON string, but not a search result. Validate the returned
                // structural contract as well as the native runtime boundary.
                check(attempt.isFailure || runCatching { PluginWireContract.check("search", attempt.getOrThrow()) }.isFailure) { "$name was accepted" }
            }
            val healthy = "globalThis.meloxPlugin={search:()=>({items:[],page:1,pageSize:10,hasMore:false})};"
            PluginRuntime(test.targetContext, PluginArchiveReader.Package(manifest, healthy)).use { runtime ->
                PluginWireContract.check("search", runtime.invoke("search", JSONArray().put("recovery").put(1).put(10)))
            }
            evidence += "PASS isolated fault: $name rejected and subsequent fresh worker usable"
        }
        storage(test, evidence, suffix)
    }

    private fun workerDeathAndReplay(test: Instrumentation, evidence: MutableList<String>) {
        val token = UUID.randomUUID().toString()
        val ready = java.util.concurrent.CountDownLatch(1)
        val died = java.util.concurrent.CountDownLatch(1)
        val remote = java.util.concurrent.atomic.AtomicReference<android.os.Messenger>()
        val binderRef = java.util.concurrent.atomic.AtomicReference<android.os.IBinder>()
        val thread = android.os.HandlerThread("rc-worker-death").apply { start() }
        val handler = android.os.Handler(thread.looper)
        val reply = android.os.Messenger(android.os.Handler(thread.looper) { message ->
            if (message.what == PluginIsolatedService.LOG && message.data.getString("invocation") == token && message.data.getString("message") == "ready") ready.countDown()
            true
        })
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) {
                binderRef.set(binder)
                binder.linkToDeath({ died.countDown() }, 0)
                val worker = android.os.Messenger(binder)
                remote.set(worker)
                worker.send(android.os.Message.obtain(null, PluginIsolatedService.INVOKE).apply {
                    replyTo = reply
                    data = android.os.Bundle().apply {
                        putString("invocation", token); putString("method", "search"); putString("args", "[]")
                        putString("script", "globalThis.meloxPlugin={search:()=>{__hostLog('ready');while(true){}}};")
                    }
                })
            }
            override fun onServiceDisconnected(name: android.content.ComponentName) { died.countDown() }
        }
        val context = test.targetContext
        var bound = false
        try {
            bound = context.bindService(android.content.Intent(context, PluginIsolatedService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound && ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "worker did not enter native loop" }
            fun cancel(invocation: String) = remote.get().send(android.os.Message.obtain(null, PluginIsolatedService.CANCEL).apply {
                data = android.os.Bundle().apply { putString("invocation", invocation) }
            })
            cancel(UUID.randomUUID().toString())
            check(!died.await(250, java.util.concurrent.TimeUnit.MILLISECONDS) && binderRef.get().isBinderAlive) { "replayed cancel killed current worker" }
            cancel(token)
            check(died.await(3, java.util.concurrent.TimeUnit.SECONDS)) { "authorized cancel did not SIGKILL native-loop worker" }
            evidence += "PASS actual Binder: wrong-token cancel ignored, current-token cancel kills native-loop isolated process (binder death observed)"
        } finally {
            if (bound) context.unbindService(connection)
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }
    }

    private fun storage(test: Instrumentation, evidence: MutableList<String>, suffix: String) {
        val root = File(test.targetContext.cacheDir, "rc-storage-$suffix").apply { mkdirs() }
        val sandbox = object : ContextWrapper(test.targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = root
        }
        val first = SourceId.parse("plugin:org.melox.rc.storage$suffix")!!
        val second = SourceId.parse("plugin:org.melox.rc.other$suffix")!!
        val legacyKey = PluginPackageTrust.sha256((first.value+":account:legacy").toByteArray())
        try {
            File(root, "melox-plugin-storage-v1/$legacyKey.json").apply { parentFile!!.mkdirs(); writeText("""{"kept":"legacy-value"}""") }
            val legacy = PluginStorage(sandbox, first, "legacy")
            check(legacy.execute("storage.get", "kept") == "legacy-value")
            legacy.execute("storage.set", "new", "migrated")
            check(!File(root, "melox-plugin-storage-v1/$legacyKey.json").exists())
            PluginStorage(sandbox, first, "deleted-account").execute("storage.set", "kept", "orphan-test")
            PluginStorage(sandbox, second).execute("storage.set", "kept", "other-source")
            PluginStorage.clear(sandbox, first, emptyList())
            check(PluginStorage(sandbox, first, "deleted-account").execute("storage.get", "kept") == org.json.JSONObject.NULL)
            check(PluginStorage(sandbox, second).execute("storage.get", "kept") == "other-source")
            evidence += "PASS storage v1 migration, v2 deleted-account cleanup and other-source preservation"
        } finally { root.deleteRecursively() }
    }
}
