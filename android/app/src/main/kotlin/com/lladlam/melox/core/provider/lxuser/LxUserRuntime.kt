package com.lladlam.melox.core.provider.lxuser

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import com.whl.quickjs.android.QuickJSLoader
import org.json.JSONObject

data class LxUserScript(
    val source: String,
    val metadata: LxUserScriptMetadata = LxUserScriptMetadata.parse(source),
)

data class LxRequestResult(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    fun asScriptValue(): Map<String, Any> = mapOf(
        "statusCode" to statusCode,
        "headers" to headers,
        "body" to body,
    )
}

/**
 * First-version LX Music user API runtime.
 *
 * This deliberately exposes a synchronous subset. QuickJS promises and the
 * full LX module loader are not available here yet; scripts using callbacks or
 * synchronous request implementations can nevertheless resolve musicUrl.
 */
class LxUserRuntime(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : Closeable {
    private val context = createContext()
    private val listeners = ConcurrentHashMap<String, MutableList<JSFunction>>()
    private val sentEvents = mutableListOf<Pair<String, Any?>>()

    init {
        installBridge()
    }

    fun load(script: LxUserScript): LxUserScriptMetadata {
        context.evaluate("var module = { exports: {} }; var exports = module.exports;", "lx-module.js")
        context.globalObject.getJSObjectProperty("lx")?.let { lx ->
            val info = script.metadata
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
        return script.metadata
    }

    /** Invokes global musicUrl or module.exports.musicUrl with the supplied song. */
    fun musicUrl(song: Map<String, Any?>): String? {
        val global = context.globalObject
        val globalFunction = global.getJSFunctionProperty("musicUrl")
        val module = global.getJSObjectProperty("module")
        val exported = module?.getJSObjectProperty("exports")
        val function = globalFunction ?: exported?.getJSFunctionProperty("musicUrl")
            ?: throw IllegalStateException("LX script does not export musicUrl")
        return urlFrom(resolveResult(function.call(song)))
    }

    /** Small action dispatcher matching the provider-facing LX action name. */
    fun callAction(action: String, args: Map<String, Any?>): Any? = when (action) {
        "musicUrl" -> {
            val global = context.globalObject
            val exported = global.getJSObjectProperty("module")?.getJSObjectProperty("exports")
            val function = global.getJSFunctionProperty("musicUrl")
                ?: exported?.getJSFunctionProperty("musicUrl")
            if (function != null) {
                resolveResult(function.call(args))
            } else {
                val source = args["source"]?.toString() ?: "kw"
                val info = mapOf(
                    "type" to (args["type"] ?: "128k"),
                    "musicInfo" to (args["musicInfo"] ?: args),
                )
                listeners["request"].orEmpty().firstOrNull()?.call(
                    mapOf("source" to source, "action" to action, "info" to info),
                )
            }
        }
        else -> throw IllegalArgumentException("Unsupported LX action: $action")
    }

    fun sentEvents(): List<Pair<String, Any?>> = synchronized(sentEvents) { sentEvents.toList() }

    private fun installBridge() {
        val lx = context.createNewJSObject()
        lx.setProperty("request", JSCallFunction { args ->
            val result = request(args.getOrNull(0), args.getOrNull(1))
            (args.getOrNull(2) as? JSFunction)?.call(null, result, result["body"])
            result
        })
        lx.setProperty("send", JSCallFunction { args ->
            val event = args.firstOrNull()?.toString().orEmpty()
            val value = args.getOrNull(1)
            synchronized(sentEvents) { sentEvents += event to value }
            listeners[event].orEmpty().forEach { it.call(value) }
            context.evaluate("Promise.resolve()")
        })
        lx.setProperty("on", JSCallFunction { args ->
            val event = args.firstOrNull()?.toString().orEmpty()
            val callback = args.getOrNull(1) as? JSFunction
            if (callback != null) listeners.computeIfAbsent(event) { mutableListOf() }.add(callback)
            context.evaluate("Promise.resolve()")
        })
        val eventNames = context.createNewJSObject().apply {
            setProperty("request", "request")
            setProperty("inited", "inited")
            setProperty("updateAlert", "updateAlert")
        }
        lx.setProperty("EVENT_NAMES", eventNames)
        lx.setProperty("version", "2.0.0")
        lx.setProperty("env", "mobile")
        lx.setProperty("currentScriptInfo", context.createNewJSObject())
        val utils = context.createNewJSObject()
        val crypto = context.createNewJSObject()
        crypto.setProperty("md5", JSCallFunction { args ->
            val input = args.firstOrNull()?.toString().orEmpty()
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
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
            val keyBytes = Base64.getDecoder().decode(args.getOrNull(1)?.toString().orEmpty())
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            Cipher.getInstance("RSA/ECB/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
            }.doFinal(data)
        })
        utils.setProperty("crypto", crypto)
        val buffer = context.createNewJSObject()
        buffer.setProperty("from", JSCallFunction { args -> bytes(args.getOrNull(0), args.getOrNull(1)?.toString()) })
        buffer.setProperty("bufToString", JSCallFunction { args ->
            val data = bytes(args.getOrNull(0))
            when (args.getOrNull(1)?.toString()) {
                "hex" -> data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "base64" -> Base64.getEncoder().encodeToString(data)
                else -> data.toString(Charsets.UTF_8)
            }
        })
        utils.setProperty("buffer", buffer)
        lx.setProperty("utils", utils)
        context.globalObject.setProperty("lx", lx)
    }

    private fun request(urlValue: Any?, optionsValue: Any?): Map<String, Any> {
        val options = when (optionsValue) {
            is QuickJSObject -> optionsValue.toMap()
            is Map<*, *> -> optionsValue
            else -> emptyMap<String, Any?>()
        }
        val url = (options["url"] ?: urlValue)?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("lx.request requires url")
        val method = options["method"]?.toString()?.uppercase() ?: "GET"
        val headers = (options["headers"] as? Map<*, *>).orEmpty()
        val bodyText = options["body"]?.let { value ->
            when (value) {
                is QuickJSObject -> JSONObject(value.toMap()).toString()
                is Map<*, *> -> JSONObject(value).toString()
                else -> value.toString()
            }
        }
        val form = (options["form"] as? QuickJSObject)?.toMap()
            ?: (options["form"] as? Map<*, *>)
        val request = Request.Builder().url(url).apply {
            headers.forEach { (key, headerValue) -> if (key != null && headerValue != null) addHeader(key.toString(), headerValue.toString()) }
            if (method != "GET" && method != "HEAD") {
                if (form != null) {
                    val formBody = okhttp3.FormBody.Builder().apply {
                        form.forEach { (key, value) ->
                            if (key != null && value != null) add(key.toString(), value.toString())
                        }
                    }.build()
                    method(method, formBody)
                } else {
                    method(method, bodyText?.toRequestBody("application/json".toMediaTypeOrNull()))
                }
            }
        }.build()
        httpClient.newCall(request).execute().use { response ->
            return LxRequestResult(response.code, response.headers.toMultimap().mapValues { it.value.joinToString(",") }, response.body.string()).asScriptValue()
        }
    }

    private fun urlFrom(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is QuickJSObject -> urlFrom(value.toMap())
        is Map<*, *> -> urlFrom(value["url"] ?: (value["data"] as? Map<*, *>)?.get("url"))
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

    /** QuickJS does not expose a public job-loop method in this wrapper. Pumping
     * a tiny evaluation loop is enough to settle promises created by async LX
     * handlers while keeping the native request bridge synchronous. */
    private fun resolveResult(value: Any?): Any? {
        if (value !is QuickJSObject) return value
        val then = runCatching { value.getJSFunctionProperty("then") }.getOrNull() ?: return value
        val result = AtomicReference<Any?>()
        val failure = AtomicReference<Throwable?>()
        val done = CountDownLatch(1)
        val resolve = JSCallFunction { args ->
            result.set(args.firstOrNull())
            done.countDown()
            null
        }
        val reject = JSCallFunction { args ->
            failure.set(IllegalStateException(args.firstOrNull()?.toString() ?: "LX promise rejected"))
            done.countDown()
            null
        }
        runCatching { then.call(resolve, reject) }.onFailure { return value }
        repeat(200) {
            if (done.count == 0L) return@repeat
            context.evaluate("void 0")
            done.await(5, TimeUnit.MILLISECONDS)
        }
        failure.get()?.let { throw it }
        return if (done.count == 0L) result.get() else value
    }

    override fun close() {
        context.close()
    }

    private companion object {
        fun createContext(): QuickJSContext {
            QuickJSLoader.init()
            return QuickJSContext.create()
        }
    }
}
