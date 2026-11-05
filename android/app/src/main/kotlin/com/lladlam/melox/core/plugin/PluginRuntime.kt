package com.lladlam.melox.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject

/** Fresh isolated JS context per call. Global JS state is deliberately not persistent. */
fun interface PluginInvoker {
    suspend fun invoke(method: String, args: JSONArray): String
}

class PluginRuntime(context: Context, private val plugin: PluginArchiveReader.Package) : Closeable, PluginInvoker {
    private val app = context.applicationContext
    private val closed = AtomicBoolean()
    private val active = AtomicReference<PluginHttpBroker?>()
    private val accounts = if (plugin.manifest.authentication?.type in setOf("apiToken", "oauth2Pkce")) PluginAccountVault(app, plugin.manifest.sourceId) else null
    data class LogEntry(val level: String, val message: String)
    private val logEntries = java.util.concurrent.ConcurrentLinkedDeque<LogEntry>()
    fun logs(): List<LogEntry> = logEntries.toList()
    internal fun mediaBroker(): PluginHttpBroker {
        check(!closed.get()) { "plugin runtime is closed" }
        val selected = accounts?.sessionStamp()
        val lease = runCatching { accounts?.lease() }.getOrNull()
        return PluginHttpBroker(plugin.manifest, lease) { !closed.get() && accounts?.sessionStamp() == selected }
    }

    suspend fun invoke(method: String): String = invoke(method, JSONArray())
    override suspend fun invoke(method: String, args: JSONArray): String = runInterruptible(Dispatchers.IO) {
        gate.lockInterruptibly()
        try {
            check(!closed.get()) { "plugin runtime is closed" }
            invokeBlocking(method, args)
        } finally { gate.unlock() }
    }

    private fun invokeBlocking(method: String, args: JSONArray): String {
        require(PluginIpcPolicy.methodAllowed(plugin.manifest, method)) { "UNSUPPORTED_OPERATION" }
        require(plugin.script.toByteArray(Charsets.UTF_8).size <= PluginIsolatedService.MAX_BYTES) { "script too large" }
        require(args.toString().toByteArray(Charsets.UTF_8).size <= PluginIsolatedService.MAX_BYTES) { "arguments too large" }
        val selectedSession = accounts?.sessionStamp()
        val selectedAccount = selectedSession?.substringBefore('\n')?.takeIf(String::isNotEmpty)
        val storage = PluginStorage(app, plugin.manifest.sourceId, selectedAccount)
        // Public operations stay usable if a saved account's key is unavailable.
        // Explicit credential requests still fail; they never silently become anonymous.
        var lease = runCatching { accounts?.lease() }.getOrNull()
        val currentLease = lease
        if (method != "__validate" && currentLease != null && currentLease.policy == plugin.manifest.authentication &&
            plugin.manifest.authentication?.type == "oauth2Pkce" && currentLease.expiresAt?.let { it <= System.currentTimeMillis() + 30_000 } == true) {
            PluginHttpBroker(plugin.manifest, currentLease) { !closed.get() && accounts?.sessionStamp() == selectedSession }.use { refreshBroker ->
                active.set(refreshBroker)
                try {
                    val tokens = PluginOAuth.refresh(plugin.manifest, currentLease, refreshBroker)
                    lease = accounts?.replaceTokens(currentLease, tokens)
                } catch (_: Exception) {
                    // Retain the expired account for reauthorization; credential requests fail explicitly.
                } finally { active.compareAndSet(refreshBroker, null) }
            }
        }
        val handlerThread = HandlerThread("melox-plugin-ipc").apply { start() }
        val http = Executors.newSingleThreadExecutor()
        val broker = PluginHttpBroker(plugin.manifest, lease) { !closed.get() && accounts?.sessionStamp() == selectedSession }
        active.set(broker)
        val done = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val failure = AtomicReference<Exception?>()
        val remote = AtomicReference<Messenger?>()
        val invocationId = java.util.UUID.randomUUID().toString()
        val workerUid = java.util.concurrent.atomic.AtomicInteger(-1)
        val hostCalls = java.util.concurrent.atomic.AtomicInteger()
        var hostLogCount = 0
        val seenCalls = mutableSetOf<Int>()
        val accepting = AtomicBoolean(true)
        val reply = Messenger(Handler(handlerThread.looper) { message ->
            try {
            if (!accepting.get() || !PluginIpcPolicy.validToken(invocationId, message.data.getString("invocation")) ||
                message.sendingUid < 0 || message.sendingUid == android.os.Process.myUid()) return@Handler true
            workerUid.compareAndSet(-1, message.sendingUid)
            if (workerUid.get() != message.sendingUid) return@Handler true
            when (message.what) {
                PluginIsolatedService.RESULT -> {
                    accepting.set(false)
                    val json = message.data.getString("json")
                    val error = message.data.getString("error")
                    if (error != null) failure.set(IllegalStateException(PluginIpcPolicy.errorCode(error)))
                    else if (json == null || json.toByteArray(Charsets.UTF_8).size > PluginIsolatedService.MAX_BYTES)
                        failure.set(IllegalStateException("INVALID_PLUGIN_RESULT"))
                    else result.set(json)
                    done.countDown()
                }
                PluginIsolatedService.HTTP_REQUEST -> {
                    val id = message.arg1
                    val request = message.data.getString("json").orEmpty()
                    if (!PluginIpcPolicy.validHostCall(id, hostCalls.incrementAndGet(), request.toByteArray(Charsets.UTF_8).size) || !seenCalls.add(id)) {
                        accepting.set(false); failure.set(IllegalStateException("PLUGIN_HOST_CALL_LIMIT")); done.countDown()
                        return@Handler true
                    }
                    if (!http.isShutdown) runCatching { http.execute {
                        val response = try {
                            check(accepting.get() && !closed.get() && accounts?.sessionStamp() == selectedSession) { "plugin session revoked" }
                            PluginJson.check(request, 48 * 1024)
                             val input = JSONObject(request)
                             require(input.opt("operation") is String) { "invalid host operation" }
                             val operation = input.getString("operation")
                             val value = if (operation == "http") JSONObject(broker.request(input.getJSONObject("request").toString()))
                                 else {
                                     require(input.opt("key") is String && (operation != "storage.set" || input.opt("value") is String)) { "invalid storage model" }
                                     storage.execute(operation, input.getString("key"), if (operation == "storage.set") input.getString("value") else null)
                                 }
                            JSONObject().put("ok", true).put("value", value)
                        } catch (error: Exception) {
                            val code = error.message?.takeIf { it in setOf("LOGIN_REQUIRED", "AUTH_EXPIRED") } ?: "NETWORK_REQUEST_FAILED"
                            JSONObject().put("ok", false).put("error", code)
                        }
                        runCatching { remote.get()?.send(Message.obtain(null, PluginIsolatedService.HTTP_RESULT).apply {
                            arg1 = id
                            data = Bundle().apply { putString("json", response.toString()) }
                            data.putString("invocation", invocationId)
                        }) }
                    } }
                }
                PluginIsolatedService.LOG -> {
                    val level = message.data.getString("level").orEmpty()
                    if (++hostLogCount <= 32 && level in setOf("info", "warn", "error")) {
                        // Credential-bearing scripts may echo server responses. Keep event metadata only.
                        logEntries.add(LogEntry(level, if (plugin.manifest.authentication != null) "认证插件日志内容已隐藏" else "插件日志事件（内容未保留）"))
                        while (logEntries.size > 100) logEntries.pollFirst()
                    }
                }
            }
            } catch (_: Exception) {
                accepting.set(false)
                failure.set(IllegalStateException("INVALID_PLUGIN_IPC"))
                done.countDown()
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    val messenger = Messenger(binder)
                    remote.set(messenger)
                    messenger.send(Message.obtain(null, PluginIsolatedService.INVOKE).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString("script", plugin.script)
                            putString("method", method)
                            putString("args", args.toString())
                            putString("invocation", invocationId)
                            putString("account", lease?.account?.let { JSONObject().put("id", it.id).put("displayName", it.displayName).toString() } ?: "null")
                        }
                    })
                } catch (error: Exception) { failure.set(error); done.countDown() }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                failure.set(IllegalStateException("plugin worker disconnected")); done.countDown()
            }
            override fun onNullBinding(name: ComponentName) {
                failure.set(IllegalStateException("plugin worker refused binding")); done.countDown()
            }
            override fun onBindingDied(name: ComponentName) {
                failure.set(IllegalStateException("plugin worker binding died")); done.countDown()
            }
        }
        var bound = false
        try {
            bound = app.bindService(Intent(app, PluginIsolatedService::class.java), connection, Context.BIND_AUTO_CREATE)
            check(bound) { "cannot bind isolated plugin worker" }
            check(done.await(12, TimeUnit.SECONDS)) { "plugin call timed out" }
            check(!closed.get()) { "plugin runtime is closed" }
            check(accounts?.sessionStamp() == selectedSession) { "plugin account changed" }
            failure.get()?.let { throw it }
            return (result.get() ?: error("plugin returned no result")).also { PluginJson.check(it) }
        } finally {
            accepting.set(false)
            if (bound) runCatching { app.unbindService(connection) }
            broker.close()
            active.compareAndSet(broker, null)
            http.shutdownNow()
            handlerThread.quitSafely()
        }
    }

    override fun close() { closed.set(true); active.getAndSet(null)?.close() }

    private companion object {
        // The API-24 compatible isolated service is shared, so invocations are serialized across plugins.
        val gate = ReentrantLock(true)
    }
}
