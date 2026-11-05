package com.lladlam.melox.core.plugin

import android.app.Service
import android.content.Intent
import android.os.*
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** No app UID, permissions, cookies, files, credentials or native host objects are supplied. */
class PluginIsolatedService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val busy = AtomicBoolean()
    @Volatile private var invocation: String? = null
    private val responses = ConcurrentLinkedQueue<Pair<Int, String>>()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        // Messenger dispatch happens after the Binder transaction: use sendingUid, not getCallingUid().
        if (message.sendingUid != applicationInfo.uid) return@Handler true
        when (message.what) {
            INVOKE -> {
                val reply = message.replyTo ?: return@Handler true
                if (!busy.compareAndSet(false, true)) {
                    send(reply, RESULT, Bundle().apply { putString("error", "BUSY") }, token = message.data.getString("invocation"))
                } else {
                    val data = message.data
                    invocation = data.getString("invocation")
                    executor.execute { execute(data, reply) }
                }
            }
            HTTP_RESULT -> if (busy.get() && invocation?.let { PluginIpcPolicy.validToken(it, message.data.getString("invocation")) } == true &&
                message.arg1 in 1..PluginIpcPolicy.MAX_HOST_CALLS && message.data.getString("json").orEmpty().toByteArray(Charsets.UTF_8).size <= MAX_BYTES && responses.size < 32)
                responses.add(message.arg1 to message.data.getString("json").orEmpty())
        }
        true
    })

    override fun onBind(intent: Intent?) = messenger.binder

    private fun execute(data: Bundle, reply: Messenger) {
        val token = data.getString("invocation")
        // Independent Java thread can terminate an infinite native JS loop. Never kill the app process.
        val timeout = watchdog.schedule({ android.os.Process.killProcess(android.os.Process.myPid()) }, 8, TimeUnit.SECONDS)
        var context: QuickJSContext? = null
        var terminal = Bundle().apply { putString("error", "PLUGIN_EXECUTION_FAILED") }
        try {
            responses.clear()
            val script = data.getString("script") ?: error("missing script")
            val method = data.getString("method") ?: error("missing method")
            val args = data.getString("args") ?: "[]"
            require(token != null && token.length in 1..64 && method.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")))
            require(script.toByteArray(Charsets.UTF_8).size <= MAX_BYTES && args.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            QuickJSLoader.init()
            val js = QuickJSContext.create()
            context = js
            js.setMemoryLimit(16 * 1024 * 1024)
            js.setMaxStackSize(256 * 1024)
            var result: String? = null
            var failed = false
            var httpCount = 0
            var logCount = 0
            js.globalObject.setProperty("__finish", JSCallFunction { values ->
                val value = values.getOrNull(1)?.toString() ?: "null"
                require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "result too large" }
                failed = values.firstOrNull() == true
                result = value
                null
            })
            js.globalObject.setProperty("__http", JSCallFunction { values ->
                require(++httpCount <= 32) { "host call quota exceeded" }
                val id = (values.firstOrNull() as Number).toInt()
                val json = values.getOrNull(1)?.toString() ?: error("invalid HTTP request")
                require(json.toByteArray(Charsets.UTF_8).size <= 48 * 1024) { "request too large" }
                send(reply, HTTP_REQUEST, Bundle().apply { putString("json", json) }, id)
                null
            })
            js.globalObject.setProperty("__log", JSCallFunction { values ->
                require(++logCount <= 32) { "log quota exceeded" }
                send(reply, LOG, Bundle().apply {
                    putString("level", values.firstOrNull()?.toString().orEmpty())
                    putString("message", values.getOrNull(1)?.toString().orEmpty().take(400))
                })
                null
            })
            js.evaluate(BOOTSTRAP, "melox-host-v1.js")
            js.evaluate(script, "plugin.js")
            val account = data.getString("account") ?: "null"
            require(account.toByteArray(Charsets.UTF_8).size <= 4096)
            // Parse/serialize before embedding so Binder input is never JavaScript source.
            val accountJson = if (account == "null") "null" else JSONObject(account).toString()
            PluginJson.check(args)
            val argumentsJson = org.json.JSONArray(args).toString()
            js.evaluate("__invoke(${JSONObject.quote(method)}, $argumentsJson, $accountJson)", "melox-call.js")
            while (result == null) {
                responses.poll()?.let { (id, json) ->
                    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
                    PluginJson.check(json)
                    val envelope = JSONObject(json).toString()
                    js.evaluate("__settle($id, $envelope)")
                }
                // The wrapper drains QuickJS pending Promise jobs at evaluation boundaries.
                js.evaluate("void 0")
                if (result == null) Thread.sleep(2)
            }
            terminal = Bundle().apply { putString(if (failed) "error" else "json", result) }
        } catch (_: Throwable) {
            // Do not send arbitrary script exceptions, URLs or credentials to global app logs.
            terminal = Bundle().apply { putString("error", "PLUGIN_EXECUTION_FAILED") }
        } finally {
            runCatching { context?.close() }
            timeout.cancel(false)
            responses.clear()
            invocation = null
            busy.set(false)
        }
        send(reply, RESULT, terminal, token = token)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
        watchdog.shutdownNow()
        if (busy.get()) android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun send(to: Messenger, what: Int, data: Bundle, id: Int = 0, token: String? = invocation) {
        data.putString("invocation", token)
        runCatching { to.send(Message.obtain(null, what).apply { this.data = data; arg1 = id }) }
    }

    companion object {
        const val INVOKE = 1
        const val HTTP_REQUEST = 2
        const val HTTP_RESULT = 3
        const val RESULT = 4
        const val LOG = 5
        const val MAX_BYTES = 128 * 1024
        private val BOOTSTRAP = """
            (() => {
              let next = 0;
              let account = null;
              const pending = new Map();
              const callHost = value => new Promise((resolve, reject) => {
                const id = ++next; pending.set(id, {resolve, reject}); __http(id, JSON.stringify(value));
              });
              const host = Object.freeze({
                apiVersion: 1, platform: 'android',
                get account() { return account; },
                http: Object.freeze({request: request => callHost({operation:'http', request})}),
                storage: Object.freeze({
                  get: key => callHost({operation:'storage.get', key}),
                  set: (key, value) => callHost({operation:'storage.set', key, value}),
                  remove: key => callHost({operation:'storage.remove', key})
                }),
                log: Object.freeze({info: message => __log('info', String(message)),
                  warn: message => __log('warn', String(message)), error: message => __log('error', String(message))})
              });
              globalThis.__settle = (id, value) => {
                const task = pending.get(id); if (!task) return;
                pending.delete(id); value.ok ? task.resolve(value.value) : task.reject({code:value.error});
              };
              globalThis.__invoke = (method, args, selectedAccount) => {
                account = selectedAccount ? Object.freeze(selectedAccount) : null;
                Promise.resolve().then(() => {
                  const plugin = globalThis.meloxPlugin;
                  if (!plugin || typeof plugin !== 'object') throw {code:'INVALID_PLUGIN'};
                  if (method === '__validate') return Object.keys(plugin).filter(key => typeof plugin[key] === 'function');
                  if (!Object.prototype.hasOwnProperty.call(plugin, method) || typeof plugin[method] !== 'function')
                    throw {code:'UNSUPPORTED_OPERATION'};
                  return plugin[method](...args, host);
                }).then(value => __finish(false, JSON.stringify(value ?? null)),
                        error => __finish(true, JSON.stringify({code: typeof error?.code === 'string' ? error.code : 'PLUGIN_ERROR'})));
              };
            })();
        """.trimIndent()
    }
}
