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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val HTTP_TIMEOUT_MS = 13_000L

/**
 * Low-to-high LX quality identifiers. `jyeffect`/`sky` are MeloX/NetEase names;
 * LX scripts declare `atmos`/`atmos_plus` instead, so both must survive the
 * `inited` handshake or Master/Hi-Res/Surround fall back to plain FLAC.
 */
internal val LX_QUALITY_ORDER = listOf(
    "128k", "320k", "flac", "flac24bit", "hires", "atmos", "atmos_plus", "master",
)

/** Best quality declared by a source at or below [requested]. */
internal fun negotiateLxQuality(supported: List<String>, requested: String): String {
    if (supported.isEmpty() || requested in supported) return requested
    val requestedIndex = LX_QUALITY_ORDER.indexOf(requested)
    if (requestedIndex < 0) return requested
    return LX_QUALITY_ORDER.asReversed()
        .firstOrNull { it in supported && LX_QUALITY_ORDER.indexOf(it) <= requestedIndex }
        ?: supported.firstOrNull()
        ?: requested
}

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
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.thirdPartyAndMedia,
) : Closeable {
    private val runtimeThread = LxRuntimeThread()
    private lateinit var context: QuickJSContext
    private val requestHandlers = mutableListOf<JSFunction>()
    private val recordedEvents = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Any?>>()
    private var initedSent = false
    private var updateAlertSent = false
    private val sourceQualities = mutableMapOf<String, List<String>>()
    private val sourceActions = mutableMapOf<String, Set<String>>()
    private val activeCalls = ConcurrentHashMap<Int, Call>()
    private var nextCallId = 1
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
        try {
            runtimeThread.call {
                context = createContext()
                installGlobals()
            }
        } catch (error: Throwable) {
            runtimeThread.close { if (::context.isInitialized) context.close() }
            throw error
        }
    }

    private var loaded = false

    fun load(script: LxUserScript): LxUserScriptMetadata = runtimeThread.call { loadOnRuntimeThread(script) }

    private fun loadOnRuntimeThread(script: LxUserScript): LxUserScriptMetadata {
        if (loaded) return script.metadata
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
        loaded = true
        Log.d(TAG, "load done name=${info.name.orEmpty()} handlers=${requestHandlers.size} qualities=${sourceQualities}")
        return info
    }

    /** Invokes a V5 action (musicUrl, lyric, or pic) and returns its raw result. */
    fun callAction(action: String, args: Map<String, Any?>): Any? = try {
        runtimeThread.call { callActionOnRuntimeThread(action, args) }
    } catch (error: TimeoutException) {
        // Invalidate the context so late HTTP/timer callbacks cannot settle the
        // next song's request. A future session starts from a clean script.
        close()
        throw error
    } catch (error: InterruptedException) {
        close()
        throw error
    }

    private fun callActionOnRuntimeThread(action: String, args: Map<String, Any?>): Any? {
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
        if (handler != null && sourceActions.isNotEmpty() && action !in sourceActions[source].orEmpty()) {
            throw IllegalStateException("LX source $source does not declare $action")
        }
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
    fun qualityFor(source: String, requested: String): String = runtimeThread.call {
        negotiateLxQuality(sourceQualities[source].orEmpty(), requested)
    }

    /**
     * True when the script declared support for the source. Scripts that never
     * sent an inited payload keep the old permissive behaviour, but a script that
     * explicitly lists its sources no longer gets probed on sources it does not
     * serve, which previously wasted a request per unsupported source.
     */
    fun supportsSource(source: String): Boolean = runtimeThread.call {
        sourceQualities.isEmpty() || sourceQualities.containsKey(source)
    }

    /**
     * Scripts that never declared actions stay permissive. Once a script lists
     * its actions, callers skip lyric and artwork probes the script cannot serve.
     */
    fun supportsAction(source: String, action: String): Boolean = runtimeThread.call {
        sourceActions.isEmpty() || action in sourceActions[source].orEmpty()
    }

    fun sentEvents(): List<Pair<String, Any?>> = recordedEvents.toList()

    private fun resolvePromise(): Any? = context.evaluate("Promise.resolve()")

    private fun rejectPromise(message: String): Any? =
        context.evaluate("Promise.reject(new Error(${JSONObject.quote(message)})))")

    private fun installGlobals() {
        // LX streams script console output into its log UI; route ours to
        // logcat (redacted) so exported MeloX logs show what a script printed.
        val console = context.createNewJSObject()
        listOf(
            "log" to "d", "info" to "d", "debug" to "d", "group" to "d", "groupEnd" to "d",
            "table" to "d", "time" to "d", "timeEnd" to "d",
            "warn" to "w", "error" to "e",
        ).forEach { (name, level) ->
            console.setProperty(name, JSCallFunction { args ->
                val message = args.joinToString(" ") { jsArgForLog(it) }.safeLogMessage().take(400)
                when (level) {
                    "w" -> Log.w(SCRIPT_TAG, message)
                    "e" -> Log.e(SCRIPT_TAG, message)
                    else -> Log.d(SCRIPT_TAG, message)
                }
                null
            })
        }
        context.globalObject.setProperty("console", console)

        context.globalObject.setProperty("setTimeout", JSCallFunction { args ->
            val callback = args.firstOrNull() as? JSFunction ?: return@JSCallFunction 0
            // LX timers have no upper bound; only negative delays clamp to 0.
            val delayMs = (args.getOrNull(1) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
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
            val callId = executeHttpRequest(url, options, callback)
            JSCallFunction {
                activeCalls.remove(callId)?.cancel()
                null
            }
        })
        lx.setProperty("on", JSCallFunction { args ->
            val event = args.getOrNull(0)?.toString().orEmpty()
            val handler = args.getOrNull(1) as? JSFunction
            if (event != "request") return@JSCallFunction rejectPromise("The event is not supported: $event")
            if (handler != null) requestHandlers.add(handler)
            Log.d(TAG, "event on name=$event registered=${handler != null} total=${requestHandlers.size}")
            resolvePromise()
        })
        lx.setProperty("send", JSCallFunction { args ->
            val event = args.firstOrNull()?.toString().orEmpty()
            when (event) {
                "inited" -> {
                    // LX allows a single inited payload per script.
                    if (initedSent) return@JSCallFunction rejectPromise("Script is inited")
                    initedSent = true
                    recordSourceQualities(args.getOrNull(1))
                    recordedEvents.add("inited" to null)
                    Log.d(TAG, "event send name=inited sources=${sourceQualities.keys}")
                    resolvePromise()
                }
                "updateAlert" -> {
                    // Mirrors LX: one alert per script, log is mandatory, and
                    // a malformed over-long updateUrl is dropped.
                    if (updateAlertSent) return@JSCallFunction rejectPromise("The update alert can only be called once.")
                    val payload = args.getOrNull(1).asHostMap()
                    val alertLog = payload["log"]?.toString()
                    if (alertLog.isNullOrBlank()) return@JSCallFunction rejectPromise("log is required.")
                    var updateUrl = payload["updateUrl"]?.toString()
                    if (updateUrl != null && !UPDATE_URL_PATTERN.matches(updateUrl) && updateUrl.length > 1024) {
                        updateUrl = null
                    }
                    updateAlertSent = true
                    val entry = mapOf("log" to alertLog.take(1024), "updateUrl" to updateUrl)
                    recordedEvents.add("updateAlert" to entry)
                    Log.i(TAG, "script updateAlert log=${alertLog.take(200).safeLogMessage()} " +
                        "updateUrl=${updateUrl?.toSafeEndpoint().orEmpty()}")
                    resolvePromise()
                }
                else -> rejectPromise("The event is not supported: $event")
            }
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

              // Mask host/native function sources the same way LX does so
              // scripts cannot fingerprint the bridge via toString().
              const lxToString = Function.prototype.toString
              Function.prototype.toString = function() {
                const descriptor = Object.getOwnPropertyDescriptors(this).name
                const fnName = this && this.name ? this.name : ''
                return descriptor.configurable
                  ? lxToString.apply(this)
                  : 'function ' + fnName + '() { [native code] }'
              }

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
            val encoded = java.net.URLEncoder.encode(args.firstOrNull()?.toString().orEmpty(), Charsets.UTF_8)
                .replace("+", "%20")
            val input = java.net.URLDecoder.decode(encoded, Charsets.UTF_8)
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        })
        crypto.setProperty("randomBytes", JSCallFunction { args ->
            val size = (args.firstOrNull() as? Number)?.toInt()?.coerceIn(0, 65_536) ?: 0
            jsBytes(ByteArray(size) { (Math.random() * 256).toInt().toByte() })
        })
        crypto.setProperty("aesEncrypt", JSCallFunction { args ->
            val data = android.util.Base64.decode(lxBase64(args.getOrNull(0)), android.util.Base64.DEFAULT)
            val mode = args.getOrNull(1)?.toString().orEmpty()
            val key = android.util.Base64.decode(lxBase64(args.getOrNull(2)), android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(lxBase64(args.getOrNull(3)), android.util.Base64.DEFAULT)
            // LX only accepts these two modes and throws for anything else.
            val transformation = when (mode) {
                "aes-128-cbc" -> "AES/CBC/PKCS7Padding"
                "aes-128-ecb" -> "AES/ECB/NoPadding"
                else -> throw IllegalArgumentException("Unsupported AES mode: $mode")
            }
            val cbc = transformation.contains("CBC")
            val encrypted = Cipher.getInstance(transformation).apply {
                if (cbc) {
                    val paddedIv = ByteArray(16)
                    iv.copyInto(paddedIv, endIndex = minOf(iv.size, 16))
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(paddedIv))
                } else {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
                }
            }.doFinal(data)
            jsBytes(android.util.Base64.decode(
                android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP),
                android.util.Base64.DEFAULT,
            ))
        })
        crypto.setProperty("rsaEncrypt", JSCallFunction { args ->
            val data = android.util.Base64.decode(lxBase64(args.getOrNull(0)), android.util.Base64.DEFAULT)
            val keyText = args.getOrNull(1)?.toString()
                ?.replace("-----BEGIN PUBLIC KEY-----", "")
                ?.replace("-----END PUBLIC KEY-----", "")
                .orEmpty()
            val keyBytes = android.util.Base64.decode(keyText.trim(), android.util.Base64.DEFAULT)
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val encrypted = Cipher.getInstance("RSA/ECB/NoPadding")
                .apply { init(Cipher.ENCRYPT_MODE, key) }
                .doFinal(data)
            jsBytes(android.util.Base64.decode(
                android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP),
                android.util.Base64.DEFAULT,
            ))
        })
        utils.setProperty("crypto", crypto)

        val buffer = context.createNewJSObject()
        buffer.setProperty("from", JSCallFunction { args ->
            jsBytes(bytes(args.getOrNull(0), args.getOrNull(1)?.toString()))
        })
        buffer.setProperty("bufToString", JSCallFunction { args ->
            val data = bytes(args.getOrNull(0))
            when (args.getOrNull(1)?.toString()) {
                "hex" -> data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "base64" -> lxBase64(data)
                "binary" -> jsBytes(data)
                else -> data.toString(Charsets.UTF_8)
            }
        })
        utils.setProperty("buffer", buffer)
        return utils
    }

    private fun executeHttpRequest(url: String, optionsValue: Any?, callback: JSFunction?): Int {
        val callId = nextCallId++
        if (callback == null) return callId
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            enqueueHttpResponse(HttpResponse(callback, "Unsupported URL scheme: $url", null, null))
            return callId
        }
        val options = when (optionsValue) {
            is QuickJSObject -> optionsValue.toMap()
            is Map<*, *> -> optionsValue
            else -> emptyMap<String, Any?>()
        }
        val method = (options["method"] as? String)?.uppercase() ?: "GET"
        val headers = options["headers"].asHostMap().toMutableMap()
        val bodyValue = options["body"]
        val form = options["form"].asHostMap().takeIf { it.isNotEmpty() }
        val formData = options["formData"].asHostMap().takeIf { it.isNotEmpty() }
        val binary = options["binary"] == true
        if (!headers.keys.any { it?.toString().equals("Accept", ignoreCase = true) }) {
            headers["Accept"] = "application/json"
        }
        if (!headers.keys.any { it?.toString().equals("User-Agent", ignoreCase = true) }) {
            headers["User-Agent"] = LX_USER_AGENT
        }
        if (method == "POST" && !headers.keys.any { it?.toString().equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = when {
                form != null -> "application/x-www-form-urlencoded"
                formData != null -> "multipart/form-data"
                else -> "application/json"
            }
        }
        val timeoutMs = (options["timeout"] as? Number)?.toLong()?.coerceIn(1_000L, 60_000L) ?: HTTP_TIMEOUT_MS
        Log.d(TAG, "http start endpoint=${url.toSafeEndpoint()} method=$method headers=${headers.keys.joinToString(",")} " +
            "form=${form != null} formData=${formData != null} binary=$binary body=${bodyValue != null} timeoutMs=$timeoutMs")

        httpPool.execute {
            var call: Call? = null
            try {
                if (closed.get()) return@execute
                val requestBuilder = Request.Builder().url(url)
                headers.forEach { (key, value) ->
                    if (key != null && value != null) requestBuilder.addHeader(key.toString(), value.toString())
                }
                if (method != "GET" && method != "HEAD") {
                    val contentType = headers.entries.firstOrNull {
                        it.key?.toString().equals("Content-Type", ignoreCase = true)
                    }?.value?.toString().orEmpty()
                    val requestBody = when {
                        form != null -> form.entries
                            .filter { it.key != null && it.value != null }
                            .joinToString("&") {
                                java.net.URLEncoder.encode(it.key.toString(), Charsets.UTF_8) + "=" +
                                    java.net.URLEncoder.encode(it.value.toString(), Charsets.UTF_8)
                            }
                            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                        formData != null -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                            formData.forEach { (key, value) ->
                                if (key == null || value == null) return@forEach
                                val bytes = bytes(value)
                                if (bytes.isNotEmpty() && value !is String) {
                                    addFormDataPart(key.toString(), "blob", bytes.toRequestBody("application/octet-stream".toMediaType()))
                                } else {
                                    addFormDataPart(key.toString(), value.toString())
                                }
                            }
                        }.build()
                        contentType.startsWith("application/json", ignoreCase = true) && bodyValue != null ->
                            (if (bodyValue is String) bodyValue else JSONObject(bodyValue.asHostMap()).toString())
                                .toRequestBody("application/json".toMediaType())
                        bodyValue is ByteArray || bodyValue is List<*> || bodyValue is QuickJSObject ->
                            bytes(bodyValue).toRequestBody(
                                contentType.substringBefore(";").ifBlank { "application/octet-stream" }.toMediaType(),
                            )
                        bodyValue is String -> bodyValue.toRequestBody(
                            contentType.substringBefore(";").ifBlank { "text/plain" }.toMediaType(),
                        )
                        else -> ByteArray(0).toRequestBody(null)
                    }
                    requestBuilder.method(method, requestBody)
                }
                call = httpClient.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
                    .newCall(requestBuilder.build())
                activeCalls[callId] = call
                if (closed.get()) {
                    call.cancel()
                    return@execute
                }
                call.execute().use { response ->
                    if (call.isCanceled()) return@execute
                    val rawBytes = response.body.bytes()
                    val parsedBody: Any? = if (binary) jsBytes(rawBytes) else parseResponseBody(rawBytes.toString(Charsets.UTF_8))
                    Log.i(
                        TAG,
                        "LX HTTP status=${response.code} contentType=${response.header("Content-Type").orEmpty()} " +
                            "body=${responseShape(parsedBody)}",
                    )
                    Log.d(TAG, "http response endpoint=${url.toSafeEndpoint()} code=${response.code} urlAvailable=${responseShape(parsedBody).contains("url")}")
                    val result = mapOf(
                        "statusCode" to response.code,
                        "statusMessage" to response.message,
                        // LX exposes fetch-shaped headers: lower-cased names,
                        // comma-joined string values (never lists).
                        "headers" to flattenLxResponseHeaders(response.headers.toMultimap()),
                        "body" to parsedBody,
                        "url" to response.request.url.toString(),
                        "ok" to response.isSuccessful,
                    )
                    enqueueHttpResponse(HttpResponse(callback, null, result, parsedBody))
                }
            } catch (error: Throwable) {
                if (call?.isCanceled() == true || closed.get()) return@execute
                Log.w(TAG, "LX HTTP failed error=${error.javaClass.simpleName}: ${error.message.safeLogMessage()}")
                enqueueHttpResponse(HttpResponse(callback, error.message ?: "request failed", null, null))
            } finally {
                activeCalls.remove(callId, call)
            }
        }
        return callId
    }

    private fun enqueueHttpResponse(response: HttpResponse) {
        if (closed.get()) return
        pendingHttpResponses.add(response)
        synchronized(drainSignal) { drainSignal.notifyAll() }
    }

    private fun processPendingHttpResponses() {
        if (closed.get()) {
            pendingHttpResponses.clear()
            return
        }
        while (true) {
            val response = pendingHttpResponses.poll() ?: break
            if (response.error != null) {
                // LX hands scripts a real Error so `err.message` works.
                val error = runCatching {
                    context.evaluate("new Error(${JSONObject.quote(response.error)})")
                }.getOrNull() ?: response.error
                response.callback.call(error, null, null)
            } else {
                val resultObj = response.result?.let { createJsObject(it) }
                val bodyObj = response.body?.let { toJsValue(it) }
                response.callback.call(null, resultObj, bodyObj)
            }
        }
    }

    private fun parseResponseBody(body: String): Any? {
        val trimmed = body.trimStart()
        return runCatching {
            when (trimmed.firstOrNull()) {
                '{' -> JSONObject(trimmed).toHostValue().let { value ->
                    // Additive compatibility: some user scripts read payload
                    // keys directly (body.url) instead of body.data.url. Only
                    // missing top-level keys are filled in; real top-level
                    // values are never overwritten.
                    val nested = value["data"]
                    val additions: Map<String, Any?> = when {
                        value["url"] != null -> emptyMap()
                        nested is Map<*, *> -> nested.entries
                            .associate { it.key.toString() to it.value }
                            .filterKeys { it !in value.keys }
                        nested is String && nested.startsWith("http") -> mapOf("url" to nested)
                        else -> emptyMap()
                    }
                    value + additions
                }
                '[' -> JSONArray(trimmed).toHostValue()
                // Match LX's JSON.parse-where-possible handling: bare numbers,
                // booleans, quoted strings and null become their parsed value.
                null -> body
                else -> jsonValue(JSONArray("[$trimmed]").opt(0))
            }
        }.getOrDefault(body)
    }

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
            val source = name?.toString()
            // Mirror LX's host filter: only known sources declaring type
            // 'music' register, and their qualities/actions are intersected
            // with what this host supports.
            if (source == null || source !in LX_SOURCES) return@forEach
            val info = when (rawInfo) {
                is QuickJSObject -> rawInfo.toMap()
                is Map<*, *> -> rawInfo
                else -> return@forEach
            }
            if (info["type"]?.toString() != "music") return@forEach
            val declaredQualities = when (val raw = info["qualitys"]) {
                is QuickJSObject -> raw.toArray().mapNotNull { it?.toString() }
                is List<*> -> raw.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            val declaredActions = when (val raw = info["actions"]) {
                is QuickJSObject -> raw.toArray().mapNotNull { it?.toString() }
                is List<*> -> raw.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            val qualities = SUPPORT_LX_QUALITIES.filter { it in declaredQualities }
            val actions = SUPPORT_LX_ACTIONS.filter { it in declaredActions }
            if (qualities.isNotEmpty()) sourceQualities[source] = qualities
            if (actions.isNotEmpty()) sourceActions[source] = actions.toSet()
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
        runCatching { then.call(resolve, reject) }.onFailure(onError)
    }

    private fun drainUntil(done: CountDownLatch) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ACTION_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            check(!closed.get()) { "LX runtime is closed" }
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
        if (done.count != 0L) throw TimeoutException("LX script action timed out")
    }

    private fun drainScriptInitialization() {
        repeat(600) { iteration ->
            check(!closed.get()) { "LX runtime is closed" }
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

    /**
     * Matches LX's native Base64 bridge: strings are UTF-8 encoded, while byte
     * arrays are first decoded as Latin-1 text and then re-encoded as UTF-8.
     */
    private fun lxBase64(value: Any?): String {
        val text = when (value) {
            is String -> value
            else -> String(bytes(value).map { (it.toInt() and 0xff).toByte() }.toByteArray(), Charsets.ISO_8859_1)
        }
        return android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    }

    private fun jsBytes(data: ByteArray): List<Int> = data.map { it.toInt() and 0xff }

    private fun bytes(value: Any?, encoding: String? = null): ByteArray = when (value) {
        is ByteArray -> value
        is QuickJSObject -> value.toArray().mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        is List<*> -> value.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        is String -> when (encoding?.lowercase()) {
            "base64" -> android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            "hex" -> value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            else -> value.toByteArray(Charsets.UTF_8)
        }
        else -> ByteArray(0)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            activeCalls.values.forEach { it.cancel() }
            activeCalls.clear()
            synchronized(drainSignal) { drainSignal.notifyAll() }
            runtimeThread.close {
                pendingHttpResponses.clear()
                timers.clear()
                context.close()
            }
        }
    }

    companion object {
        private val sessions = ConcurrentHashMap<String, LxUserRuntime>()
        private val sessionLock = Any()
        private val httpPool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "melox-lx-http").apply { isDaemon = true }
        }
        private val LX_SOURCES = setOf("kw", "kg", "tx", "wy", "mg", "local")
        private val SUPPORT_LX_QUALITIES = LX_QUALITY_ORDER
        // LX only lets 'local' serve lyric/pic; MeloX keeps those actions for
        // any declared source as an intentional, documented superset.
        private val SUPPORT_LX_ACTIONS = listOf("musicUrl", "lyric", "pic")
        private val UPDATE_URL_PATTERN = Regex("^https?://[^\\s$.?#].[^\\s]*$")
        private const val ACTION_TIMEOUT_MS = HTTP_TIMEOUT_MS + 2_000L
        private const val LX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"

        /**
         * Keeps one QuickJS runtime per installed script. LX user sources fetch
         * their remote configuration during startup, so rebuilding the runtime
         * for every song repeats that work and drops the initialized state.
         */
        fun session(id: String, source: String): LxUserRuntime = synchronized(sessionLock) {
            sessions[id]?.takeUnless { it.closed.get() } ?: LxUserRuntime().also { runtime ->
                try {
                    runtime.load(LxUserScript(source))
                    sessions[id] = runtime
                } catch (error: Throwable) {
                    runtime.close()
                    throw error
                }
            }
        }

        fun evict(id: String) {
            val runtime = synchronized(sessionLock) { sessions.remove(id) }
            runtime?.close()
        }

        fun evictAll() {
            sessions.keys.toList().forEach(::evict)
        }

        private fun createContext(): QuickJSContext {
            QuickJSLoader.init()
            return QuickJSContext.create()
        }
    }
}

private const val TAG = "MeloXLxRuntime"
private const val SCRIPT_TAG = "MeloXLxScript"

private fun jsArgForLog(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is Number, is Boolean -> value.toString()
    is com.whl.quickjs.wrapper.QuickJSObject -> runCatching { value.toString() }.getOrDefault("[object]")
    else -> value.toString()
}

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
