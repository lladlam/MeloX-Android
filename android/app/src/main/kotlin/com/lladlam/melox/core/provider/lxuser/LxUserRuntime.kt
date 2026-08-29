package com.lladlam.melox.core.provider.lxuser

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import android.util.Log
import java.io.Closeable
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val HTTP_TIMEOUT_MS = 13_000L

data class LxUserScript(
    val source: String,
    val metadata: LxUserScriptMetadata = LxUserScriptMetadata.parse(source),
)

/**
 * Self-contained LX Music user API runtime.
 *
 * Exposes the standard JavaScript surface that real LX user sources depend on:
 * - globalThis.lx.request(url, options, callback)
 * - globalThis.lx.on(globalThis.lx.EVENT_NAMES.request, async handler)
 * - globalThis.lx.send(globalThis.lx.EVENT_NAMES.inited, info)
 * - globalThis.lx.utils.crypto / buffer
 * - globalThis.lx.currentScriptInfo, version, env
 * - console.log / warn / error
 * - setTimeout
 * - Promise draining so async handlers resolve before the caller continues.
 */
class LxUserRuntime(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : Closeable {
    private val context = createContext()
    private val requestHandlers = mutableListOf<JSFunction>()
    private val sourceQualities = mutableMapOf<String, List<String>>()
    private val timers = ConcurrentHashMap<Int, Pair<Long, JSCallFunction>>()
    private var nextTimerId = 1
    private val closed = AtomicBoolean(false)

    private data class HttpResponse(
        val callback: JSFunction,
        val error: String?,
        val result: Map<String, Any?>?,
        val body: Any?,
    )

    private val pendingHttpResponses = ConcurrentLinkedQueue<HttpResponse>()
    private val drainSignal = Object()

    init {
        installGlobals()
    }

    fun load(script: LxUserScript): LxUserScriptMetadata {
        val info = script.metadata
        Log.d(TAG, "load start name=${info.name.orEmpty()} bytes=${script.source.toByteArray().size}")
        context.evaluate("var module = { exports: {} }; var exports = module.exports;", "lx-module.js")
        context.globalObject.getJSObjectProperty("lx")?.let { lx ->
            lx.getJSObjectProperty("currentScriptInfo")?.apply {
                setProperty("name", info.name.orEmpty())
                setProperty("version", info.version.orEmpty())
                setProperty("author", info.author.orEmpty())
                setProperty("description", info.description.orEmpty())
                setProperty("homepage", info.homepage.orEmpty())
                setProperty("rawScript", script.source)
            }
        }
        context.evaluate(script.source, "lx-user.js")
        // v5 sources commonly fetch remote configuration before registering the
        // request listener. Drain that initialization before the first action.
        drainScriptInitialization()
        Log.d(TAG, "load done name=${info.name.orEmpty()} handlers=${requestHandlers.size} qualities=${sourceQualities}")
        return info
    }

    /** Invokes a V5 action (musicUrl, lyric, or pic) and returns its raw result. */
    fun callAction(action: String, args: Map<String, Any?>): Any? {
        require(action == "musicUrl" || action == "lyric" || action == "pic") {
            "Unsupported LX action: $action"
        }
        val source = args["source"]?.toString() ?: "kw"
        val info = mapOf(
            "type" to (args["type"] ?: "128k"),
            "musicInfo" to (args["musicInfo"] ?: args),
        )

        val resultRef = AtomicReference<Any?>()
        val errorRef = AtomicReference<Throwable?>()
        val done = CountDownLatch(1)

        val requestArg = createJsObject(mapOf("source" to source, "action" to action, "info" to info))
        val handler = requestHandlers.firstOrNull()
        Log.d(TAG, "action start source=$source quality=${args["type"]} handler=${handler != null} export=${handler == null}")
        val returned = if (handler != null) {
            handler.call(requestArg)
        } else {
            val global = context.globalObject
            val globalFunction = global.getJSFunctionProperty("musicUrl")
            val module = global.getJSObjectProperty("module")
            val exported = module?.getJSObjectProperty("exports")
            val function = globalFunction ?: exported?.getJSFunctionProperty("musicUrl")
            if (function == null) {
                throw IllegalStateException("LX request handler is not registered")
            }
            val musicInfo = (args["musicInfo"] as? Map<*, *>)
                ?.let { createJsObject(it.toStringKeyedMap()) }
                ?: createJsObject(args)
            function.call(musicInfo, args["type"]?.toString() ?: "128k")
        }
        settle(returned, onSuccess = { value ->
            resultRef.set(value)
            done.countDown()
        }, onError = { error ->
            errorRef.set(error)
            done.countDown()
        })

        drainUntil(done)
        errorRef.get()?.let { throw it }
        val resolved = resultRef.get()
        if (action != "musicUrl") {
            val hostResolved = if (resolved is QuickJSObject) resolved.toMap() else resolved
            Log.d(TAG, "action done source=$source action=$action result=${responseShape(hostResolved)}")
            return hostResolved
        }
        val url = if (resolved is String) resolved else urlFrom(resolved)
        Log.d(TAG, "action done source=$source result=${if (url.isNullOrBlank()) "empty" else "url"}")
        return url
    }

    /** Returns the best quality this source declared through lx.send(inited, ...). */
    fun qualityFor(source: String, requested: String): String {
        val supported = sourceQualities[source].orEmpty()
        if (supported.isEmpty() || requested in supported) return requested
        val requestedIndex = QUALITY_ORDER.indexOf(requested).takeIf { it >= 0 } ?: QUALITY_ORDER.lastIndex
        return QUALITY_ORDER.asReversed()
            .firstOrNull { it in supported && QUALITY_ORDER.indexOf(it) <= requestedIndex }
            ?: supported.firstOrNull()
            ?: requested
    }

    fun sentEvents(): List<Pair<String, Any?>> = emptyList()

    private fun installGlobals() {
        val console = context.createNewJSObject()
        listOf("log", "info", "warn", "error", "debug", "group", "groupEnd", "table", "time", "timeEnd").forEach { name ->
            console.setProperty(name, JSCallFunction { null })
        }
        context.globalObject.setProperty("console", console)

        context.globalObject.setProperty("setTimeout", JSCallFunction { args ->
            val callback = args.firstOrNull() as? JSFunction ?: return@JSCallFunction 0
            val delayMs = (args.getOrNull(1) as? Number)?.toLong()?.coerceIn(0L, 60_000L) ?: 0L
            val params = args.drop(2)
            val id = nextTimerId++
            timers[id] = (System.currentTimeMillis() + delayMs) to JSCallFunction { callback.call(*params.toTypedArray()) }
            id
        })
        context.globalObject.setProperty("clearTimeout", JSCallFunction { args ->
            (args.firstOrNull() as? Number)?.toInt()?.let(timers::remove)
            null
        })

        val lx = context.createNewJSObject()
        val eventNames = context.createNewJSObject().apply {
            setProperty("request", "request")
            setProperty("inited", "inited")
            setProperty("updateAlert", "updateAlert")
        }
        lx.setProperty("EVENT_NAMES", eventNames)
        lx.setProperty("request", JSCallFunction { args ->
            val url = args.getOrNull(0)?.toString().orEmpty()
            val options = args.getOrNull(1)
            val callback = args.getOrNull(2) as? JSFunction
            executeHttpRequest(url, options, callback)
            null
        })
        lx.setProperty("on", JSCallFunction { args ->
            val event = args.getOrNull(0)?.toString().orEmpty()
            val handler = args.getOrNull(1) as? JSFunction
            if (event == "request" && handler != null) requestHandlers.add(handler)
            Log.d(TAG, "event on name=$event registered=${handler != null} total=${requestHandlers.size}")
            context.evaluate("Promise.resolve()")
        })
        lx.setProperty("send", JSCallFunction { args ->
            val event = args.firstOrNull()?.toString().orEmpty()
            if (event == "inited") recordSourceQualities(args.getOrNull(1))
            Log.d(TAG, "event send name=$event")
            context.evaluate("Promise.resolve()")
        })
        lx.setProperty("utils", createUtils())
        lx.setProperty("currentScriptInfo", context.createNewJSObject())
        lx.setProperty("version", "2.0.0")
        lx.setProperty("env", "mobile")
        context.globalObject.setProperty("lx", lx)

        lockdownSandbox()
    }

    private fun lockdownSandbox() {
        context.evaluate(
            """
            (function() {
              'use strict'
              const noop = function() {}
              // Disable dynamic code execution.
              globalThis.eval = function() { throw new Error('eval is not available') }
              const proxyFunctionConstructor = new Proxy(Function.prototype.constructor, {
                apply() { throw new Error('Dynamic code execution is not allowed.') },
                construct() { throw new Error('Dynamic code execution is not allowed.') }
              })
              Object.defineProperty(Function.prototype, 'constructor', {
                value: proxyFunctionConstructor,
                writable: false,
                configurable: false,
                enumerable: false
              })
              globalThis.Function = proxyFunctionConstructor

              // Remove dangerous globals if present.
              delete globalThis.java
              delete globalThis.Java
              delete globalThis.JNI
              delete globalThis.importClass
              delete globalThis.importPackage

              // Make the LX object non-writable so scripts cannot replace it.
              try {
                Object.defineProperty(globalThis, 'lx', {
                  value: globalThis.lx,
                  writable: false,
                  configurable: false,
                  enumerable: true
                })
              } catch (e) {}

              // Freeze console/setTimeout to prevent tampering.
              [globalThis.console, globalThis.setTimeout, globalThis.clearTimeout].forEach(function(obj) {
                if (obj && typeof obj === 'object') try { Object.freeze(obj) } catch (e) {}
              })
            })()
            """.trimIndent(),
            "sandbox-lockdown.js",
        )
    }

    private fun createUtils(): JSObject {
        val utils = context.createNewJSObject()
        val crypto = context.createNewJSObject()
        crypto.setProperty("md5", JSCallFunction { args ->
            val input = args.firstOrNull()?.toString().orEmpty()
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        })
        crypto.setProperty("randomBytes", JSCallFunction { args ->
            ByteArray((args.firstOrNull() as? Number)?.toInt()?.coerceIn(0, 65_536) ?: 0).also {
                java.security.SecureRandom().nextBytes(it)
            }
        })
        crypto.setProperty("aesEncrypt", JSCallFunction { args ->
            val data = bytes(args.getOrNull(0))
            val mode = args.getOrNull(1)?.toString().orEmpty()
            val key = bytes(args.getOrNull(2))
            val iv = bytes(args.getOrNull(3))
            val transformation = if (mode == "aes-128-cbc") "AES/CBC/PKCS5Padding" else "AES/ECB/NoPadding"
            Cipher.getInstance(transformation).apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    if (transformation.contains("CBC")) IvParameterSpec(iv) else null,
                )
            }.doFinal(data)
        })
        crypto.setProperty("rsaEncrypt", JSCallFunction { args ->
            val data = bytes(args.getOrNull(0))
            val keyText = args.getOrNull(1)?.toString()?.replace("-----BEGIN PUBLIC KEY-----", "")
                ?.replace("-----END PUBLIC KEY-----", "") ?: ""
            val keyBytes = Base64.getDecoder().decode(keyText)
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            Cipher.getInstance("RSA/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(data)
        })
        utils.setProperty("crypto", crypto)

        val buffer = context.createNewJSObject()
        buffer.setProperty("from", JSCallFunction { args ->
            bytes(args.getOrNull(0), args.getOrNull(1)?.toString())
        })
        buffer.setProperty("bufToString", JSCallFunction { args ->
            val data = bytes(args.getOrNull(0))
            when (args.getOrNull(1)?.toString()) {
                "hex" -> data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "base64" -> Base64.getEncoder().encodeToString(data)
                else -> data.toString(Charsets.UTF_8)
            }
        })
        utils.setProperty("buffer", buffer)
        return utils
    }

    private fun executeHttpRequest(url: String, optionsValue: Any?, callback: JSFunction?) {
        if (callback == null) return
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            enqueueHttpResponse(HttpResponse(callback, "Unsupported URL scheme: $url", null, null))
            return
        }
        val options = when (optionsValue) {
            is QuickJSObject -> optionsValue.toMap()
            is Map<*, *> -> optionsValue
            else -> emptyMap<String, Any?>()
        }
        val method = (options["method"] as? String)?.uppercase() ?: "GET"
        val headers = options["headers"].asHostMap()
        val bodyValue = options["body"]
        val form = options["form"].asHostMap().takeIf { it.isNotEmpty() }
        val timeoutMs = (options["timeout"] as? Number)?.toLong()?.coerceIn(1_000L, 60_000L) ?: HTTP_TIMEOUT_MS
        Log.d(TAG, "http start endpoint=${url.toSafeEndpoint()} method=$method headers=${headers.keys.joinToString(",")} " +
            "form=${form != null} body=${bodyValue != null} timeoutMs=$timeoutMs")

        Thread {
            try {
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (key, value) ->
                    if (key != null && value != null) requestBuilder.addHeader(key.toString(), value.toString())
                }
                if (method != "GET" && method != "HEAD") {
                    when {
                        form != null -> {
                            val formBody = okhttp3.FormBody.Builder().apply {
                                form.forEach { (key, value) ->
                                    if (key != null && value != null) add(key.toString(), value.toString())
                                }
                            }.build()
                            requestBuilder.method(method, formBody)
                        }
                        bodyValue != null -> requestBuilder.method(
                            method,
                            JSONObject(bodyValue as? Map<*, *> ?: emptyMap<Any?, Any?>()).toString()
                                .toRequestBody("application/json".toMediaTypeOrNull()),
                        )
                        else -> requestBuilder.method(method, null)
                    }
                }
                httpClient.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
                    .newCall(requestBuilder.build()).execute().use { response ->
                        val rawBody = response.body.string()
                        val parsedBody = parseResponseBody(rawBody)
                        Log.i(
                            TAG,
                            "LX HTTP status=${response.code} contentType=${response.header("Content-Type").orEmpty()} " +
                                "body=${responseShape(parsedBody)}",
                        )
                        Log.d(TAG, "http response endpoint=${url.toSafeEndpoint()} code=${response.code} urlAvailable=${responseShape(parsedBody).contains("url")}")
                        val result = mapOf(
                            "statusCode" to response.code,
                            "statusMessage" to response.message,
                            "headers" to response.headers.toMultimap().mapValues { it.value.joinToString(",") },
                            "body" to parsedBody,
                            "url" to response.request.url.toString(),
                            "ok" to response.isSuccessful,
                        )
                        enqueueHttpResponse(HttpResponse(callback, null, result, parsedBody))
                    }
            } catch (error: Throwable) {
                Log.w(TAG, "LX HTTP failed error=${error.javaClass.simpleName}: ${error.message.safeLogMessage()}")
                enqueueHttpResponse(HttpResponse(callback, error.message ?: "request failed", null, null))
            }
        }.start()
    }

    private fun enqueueHttpResponse(response: HttpResponse) {
        pendingHttpResponses.add(response)
        synchronized(drainSignal) { drainSignal.notifyAll() }
    }

    private fun processPendingHttpResponses() {
        while (true) {
            val response = pendingHttpResponses.poll() ?: break
            if (response.error != null) {
                response.callback.call(response.error, null, null)
            } else {
                val resultObj = response.result?.let { createJsObject(it) }
                val bodyObj = response.body?.let { toJsValue(it) }
                response.callback.call(null, resultObj, bodyObj)
            }
        }
    }

    private fun parseResponseBody(body: String): Any = runCatching {
        when (body.trimStart().firstOrNull()) {
            '{' -> JSONObject(body).toHostValue().let { value ->
                // Some LX endpoints wrap the actual payload in `data`, while
                // older user scripts read `body.url` directly.
                val nested = value["data"]
                when {
                    value["url"] != null -> value
                    nested is Map<*, *> -> value + nested.entries.associate { it.key.toString() to it.value }
                    nested is String && nested.startsWith("http") -> value + ("url" to nested)
                    else -> value
                }
            }
            '[' -> JSONArray(body).toHostValue()
            else -> body
        }
    }.getOrDefault(body)

    private fun recordSourceQualities(value: Any?) {
        val data = when (value) {
            is QuickJSObject -> value.toMap()
            is Map<*, *> -> value
            else -> return
        }
        val sources = data["sources"]
        val sourceMap = when (sources) {
            is QuickJSObject -> sources.toMap()
            is Map<*, *> -> sources
            else -> return
        }
        sourceMap.forEach { (name, rawInfo) ->
            val info = when (rawInfo) {
                is QuickJSObject -> rawInfo.toMap()
                is Map<*, *> -> rawInfo
                else -> return@forEach
            }
            val qualities = when (val raw = info["qualitys"]) {
                is QuickJSObject -> raw.toArray().mapNotNull { it?.toString() }
                is List<*> -> raw.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            if (name != null && qualities.isNotEmpty()) sourceQualities[name.toString()] = qualities
        }
    }

    private fun settle(value: Any?, onSuccess: (Any?) -> Unit, onError: (Throwable) -> Unit) {
        if (value !is QuickJSObject) {
            onSuccess(value)
            return
        }
        val then = runCatching { value.getJSFunctionProperty("then") }.getOrNull()
        if (then == null) {
            onSuccess(value)
            return
        }
        val resolve = JSCallFunction { args ->
            settle(args.firstOrNull(), onSuccess, onError)
            null
        }
        val reject = JSCallFunction { args ->
            onError(IllegalStateException(args.firstOrNull()?.toString() ?: "LX promise rejected"))
            null
        }
        runCatching { then.call(resolve, reject) }.onFailure { onSuccess(value) }
    }

    private fun drainUntil(done: CountDownLatch) {
        repeat(400) {
            if (done.count == 0L) return
            dispatchTimers()
            processPendingHttpResponses()
            runCatching { context.evaluate("void 0") }
            if (done.count == 0L) return
            synchronized(drainSignal) {
                if (done.count == 0L || pendingHttpResponses.isNotEmpty()) return@synchronized
                drainSignal.wait(5)
            }
        }
    }

    private fun drainScriptInitialization() {
        repeat(600) { iteration ->
            dispatchTimers()
            processPendingHttpResponses()
            runCatching { context.evaluate("void 0") }
            if (requestHandlers.isNotEmpty()) return
            synchronized(drainSignal) {
                if (requestHandlers.isEmpty() && pendingHttpResponses.isEmpty()) drainSignal.wait(5)
            }
            if (iteration >= 20 && pendingHttpResponses.isEmpty() && timers.isEmpty()) Thread.sleep(5)
        }
    }

    private fun dispatchTimers() {
        val now = System.currentTimeMillis()
        val ready = timers.filterValues { (deadline) -> deadline <= now }.keys.toList()
        ready.forEach { id -> timers.remove(id)?.second?.call() }
    }

    /** Converts Kotlin maps/lists into QuickJS objects by round-tripping through JSON. */
    private fun createJsObject(data: Map<String, Any?>): QuickJSObject {
        val json = jsonValueToJson(data)
        return context.evaluate("($json)") as QuickJSObject
    }

    private fun createJsArray(data: List<Any?>): QuickJSObject {
        val json = jsonValueToJson(data)
        return context.evaluate("($json)") as QuickJSObject
    }

    private fun jsonValueToJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> {
            "{" + value.entries.joinToString(",") { (k, v) ->
                "${JSONObject.quote(k.toString())}:${jsonValueToJson(v)}"
            } + "}"
        }
        is List<*> -> {
            "[" + value.joinToString(",") { jsonValueToJson(it) } + "]"
        }
        else -> JSONObject.quote(value.toString())
    }

    private fun Any?.asHostMap(): Map<*, *> = when (this) {
        is QuickJSObject -> this.toMap()
        is Map<*, *> -> this
        else -> emptyMap<Any?, Any?>()
    }

    private fun responseShape(value: Any?): String = when (value) {
        is Map<*, *> -> {
            val keys = value.keys.joinToString(",") { it.toString() }.take(120)
            val code = value["code"]?.toString()?.take(32)
            val message = value["msg"]?.toString()?.safeLogMessage()?.take(80)
            "object:$keys" + if (code != null || message != null) " code=$code msg=$message" else ""
        }
        is List<*> -> "array:${value.size}"
        is String -> "text:${value.length}"
        null -> "null"
        else -> value.javaClass.simpleName
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.toStringKeyedMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key.toString() to value }

    private fun toJsValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> createJsObject(value as Map<String, Any?>)
        is List<*> -> createJsArray(value)
        else -> value
    }

    private fun urlFrom(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is QuickJSObject -> urlFrom(value.toMap())
        is Map<*, *> -> {
            val direct = value["url"]?.toString()
            if (!direct.isNullOrBlank() && (direct.startsWith("http://") || direct.startsWith("https://"))) return direct
            val nested = (value["data"] as? Map<*, *>)?.get("url")?.toString()
            if (!nested.isNullOrBlank() && (nested.startsWith("http://") || nested.startsWith("https://"))) return nested
            null
        }
        else -> null
    }

    private fun bytes(value: Any?, encoding: String? = null): ByteArray = when (value) {
        is ByteArray -> value
        is QuickJSObject -> value.toArray().mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        is List<*> -> value.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        is String -> when (encoding?.lowercase()) {
            "base64" -> Base64.getDecoder().decode(value)
            "hex" -> value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            else -> value.toByteArray(Charsets.UTF_8)
        }
        else -> ByteArray(0)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.close()
        }
    }

    private companion object {
        val QUALITY_ORDER = listOf("128k", "320k", "flac", "flac24bit")
        fun createContext(): QuickJSContext {
            QuickJSLoader.init()
            return QuickJSContext.create()
        }
    }
}

private const val TAG = "MeloXLxRuntime"

private fun String.toSafeEndpoint(): String = runCatching {
    val uri = android.net.Uri.parse(this)
    buildString {
        append(uri.scheme.orEmpty())
        append("://")
        append(uri.host.orEmpty())
        append(uri.path.orEmpty())
        if (!uri.query.isNullOrBlank()) append("?<redacted>")
    }
}.getOrDefault("<invalid-url>")

private fun String?.safeLogMessage(): String = this.orEmpty()
    .replace(Regex("https?://\\S+"), "<url>")
    .replace(Regex("(?i)(apikey|api_key|token|key)=([^&\\s]+)"), "$1=<redacted>")
    .replace('\n', ' ')
    .take(240)

private fun JSONObject.toHostValue(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    jsonValue(opt(key))
}

private fun JSONArray.toHostValue(): List<Any?> = (0 until length()).map { index ->
    jsonValue(opt(index))
}

private fun jsonValue(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> value.toHostValue()
    is JSONArray -> value.toHostValue()
    else -> value
}
