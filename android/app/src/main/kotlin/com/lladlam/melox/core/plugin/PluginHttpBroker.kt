package com.lladlam.melox.core.plugin

import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import org.json.JSONObject

/** Per-invocation broker. Never shares the app's cookies, interceptors, proxy or authenticators. */
class PluginHttpBroker internal constructor(
    private val manifest: PluginManifest,
    private val credential: PluginAccountVault.Lease? = null,
    private val accountCurrent: () -> Boolean = { true },
) : java.io.Closeable {
    init { require(credential == null || credential.source == manifest.sourceId) { "credential source mismatch" } }
    private val requests = AtomicInteger()
    private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()
    @Volatile private var closed = false
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
        .dns(PluginPublicDns(manifest.networkDomains)).build()

    fun request(json: String): String {
        check(!closed) { "plugin request cancelled" }
        require(json.toByteArray(Charsets.UTF_8).size <= 48 * 1024) { "request too large" }
        PluginJson.check(json, 48 * 1024)
        val input = JSONObject(json)
        require(input.opt("url") is String && (!input.has("method") || input.opt("method") is String) &&
            (!input.has("body") || input.opt("body") is String) && (!input.has("headers") || input.opt("headers") is JSONObject)) { "invalid HTTP request model" }
        val wantsCredential = if (input.has("credential")) {
            require(input.getString("credential") == "active") { "unknown credential reference" }
            true
        } else false
        var url = input.getString("url").toHttpUrl()
        var method = input.optString("method", "GET")
        require(method == "GET" || method == "POST") { "unsupported HTTP method" }
        val body = input.optString("body", "")
        require(body.toByteArray(Charsets.UTF_8).size <= 32 * 1024) { "request body too large" }
        var headers = input.optJSONObject("headers") ?: JSONObject()
        require(headers.length() <= 32) { "too many request headers" }
        repeat(4) { redirect ->
            authorize(url)
            check(accountCurrent()) { "plugin account changed" }
            require(requests.incrementAndGet() <= 8) { "plugin request quota exceeded" }
            val builder = Request.Builder().url(url)
            headers.keys().forEach { name ->
                require(headers.opt(name) is String) { "HTTP header must be a string" }
                require(name.lowercase(java.util.Locale.ROOT) !in FORBIDDEN_HEADERS) { "forbidden request header" }
                val value = headers.getString(name)
                require(value.length <= 4096) { "request header too large" }
                builder.header(name, value)
            }
            if (wantsCredential) {
                val (name, value) = credentialHeader(url)
                builder.header(name, value)
            }
            if (method == "POST") builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            val call = client.newCall(builder.build())
            calls.add(call)
            try {
                check(!closed) { "plugin request cancelled" }
                call.execute().use { response ->
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        require(redirect < 3) { "too many redirects" }
                        val next = url.resolve(response.header("Location") ?: error("redirect missing location"))
                            ?: error("invalid redirect")
                        authorize(next)
                        if (next.host != url.host) headers = JSONObject()
                        if (response.code == 303 || (method == "POST" && response.code in setOf(301, 302))) method = "GET"
                        url = next
                    } else {
                        val out = java.io.ByteArrayOutputStream()
                        response.body?.byteStream()?.use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= 64 * 1024) { "response body too large" }
                                out.write(buffer, 0, count)
                            }
                        }
                        val responseHeaders = JSONObject()
                        response.headers.names().filterNot { it.equals("set-cookie", ignoreCase = true) }.forEach {
                            responseHeaders.put(it, response.header(it))
                        }
                        check(accountCurrent()) { "plugin account changed" }
                        return JSONObject().put("status", response.code).put("headers", responseHeaders)
                            .put("body", out.toString("UTF-8")).toString()
                    }
                }
            } finally { calls.remove(call) }
        }
        error("too many redirects")
    }

    private fun authorize(url: HttpUrl) {
        require(url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host in manifest.networkDomains && url.toString().length <= 4096
        ) { "PERMISSION_DENIED" }
    }

    fun authorizeMediaUrl(url: String) = authorize(url.toHttpUrl())

    /** Images never receive account credentials, cookies or cross-origin redirects. */
    internal fun imageBytes(url: String): ByteArray {
        val destination = url.toHttpUrl()
        authorize(destination)
        check(!closed && accountCurrent())
        val call = client.newCall(Request.Builder().url(destination).build())
        calls.add(call)
        try {
            check(!closed && accountCurrent()) { "CANCELLED" }
            call.execute().use { response ->
                require(response.code == 200 && response.header("Content-Type")?.substringBefore(';')?.lowercase() in
                    setOf("image/png", "image/jpeg", "image/webp")) { "unsupported artwork response" }
                val body = requireNotNull(response.body)
                require(body.contentLength() <= 2 * 1024 * 1024) { "artwork too large" }
                val out = java.io.ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        require(out.size() + count <= 2 * 1024 * 1024) { "artwork too large" }
                        check(!closed && accountCurrent())
                        out.write(buffer, 0, count)
                    }
                }
                return out.toByteArray()
            }
        } finally { calls.remove(call) }
    }

    /** Host-only OAuth exchange; plugin JS has no access to form fields or the token response. */
    internal fun tokenRequest(url: String, fields: Map<String, String>): JSONObject {
        val destination = url.toHttpUrl()
        authorize(destination)
        check(!closed && accountCurrent()) { "plugin session revoked" }
        require(destination.toString() == manifest.authentication?.oauth?.tokenEndpoint) { "invalid token endpoint" }
        val body = okhttp3.FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
        val call = client.newCall(Request.Builder().url(destination).post(body).build())
        calls.add(call)
        try {
            check(!closed && accountCurrent()) { "CANCELLED" }
            call.execute().use { response ->
                check(response.code == 200) { "OAuth token exchange failed" }
                val output = java.io.ByteArrayOutputStream()
                requireNotNull(response.body).byteStream().use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        require(output.size() + count <= 16 * 1024) { "OAuth response too large" }
                        output.write(buffer, 0, count)
                    }
                }
                check(!closed && accountCurrent()) { "plugin session revoked" }
                val raw = output.toString("UTF-8")
                PluginJson.check(raw, 16 * 1024)
                return JSONObject(raw)
            }
        } finally { calls.remove(call) }
    }

    /** Progressive media can stream longer than API requests; redirects stay disabled. */
    private fun credentialHeader(url: HttpUrl): Pair<String, String> {
        check(accountCurrent()) { "plugin account changed" }
        val auth = manifest.authentication ?: error("credential permission denied")
        require(auth.type in setOf("apiToken", "oauth2Pkce") && url.host in auth.domains) { "PERMISSION_DENIED" }
        val lease = credential ?: error("LOGIN_REQUIRED")
        require(lease.policy == auth) { "AUTH_EXPIRED" }
        check(lease.expiresAt == null || lease.expiresAt > System.currentTimeMillis()) { "AUTH_EXPIRED" }
        return auth.header to (auth.prefix + lease.token)
    }

    fun mediaClient(credentialRequested: Boolean = false): OkHttpClient = client.newBuilder().callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).addInterceptor { chain ->
            val call = chain.call()
            calls.add(call)
            try {
                check(!closed && accountCurrent()) { "plugin media session revoked" }
                authorize(chain.request().url)
                val response = chain.proceed(chain.request())
                val body = response.body
                if (body == null) {
                    calls.remove(call)
                    response
                } else {
                    val guarded = object : okio.ForwardingSource(body.source()) {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long {
                            if (closed || !accountCurrent()) {
                                call.cancel()
                                throw java.io.IOException("plugin media session revoked")
                            }
                            return try {
                                 val count = super.read(sink, byteCount)
                                 if (closed || !accountCurrent()) {
                                     call.cancel()
                                     throw java.io.IOException("plugin media session revoked")
                                 }
                                if (count == -1L) calls.remove(call)
                                count
                            } catch (error: Throwable) { calls.remove(call); throw error }
                        }
                        override fun close() {
                            try { super.close() } finally { calls.remove(call) }
                        }
                    }.buffer()
                    response.newBuilder().body(object : okhttp3.ResponseBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = guarded
                    }).build()
                }
            } catch (error: Throwable) { calls.remove(call); throw error }
        }.addNetworkInterceptor { chain ->
            authorize(chain.request().url)
            check(!closed && accountCurrent()) { "plugin account changed" }
            val request = if (credentialRequested) {
                val (name, value) = credentialHeader(chain.request().url)
                chain.request().newBuilder().header(name, value).build()
            } else chain.request()
            val response = chain.proceed(request)
            if (closed || !accountCurrent()) {
                response.close()
                throw java.io.IOException("plugin media session revoked")
            }
            if (PluginMediaFormats.unsupportedMime(response.header("Content-Type").orEmpty())) {
                response.close()
                throw java.io.IOException("UNSUPPORTED_MEDIA_FORMAT")
            }
            response
        }.build()

    override fun close() {
        closed = true
        calls.forEach(Call::cancel)
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private val FORBIDDEN_HEADERS = setOf("host", "connection", "content-length", "transfer-encoding", "proxy-authorization", "proxy-connection", "upgrade")
        fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address.map { it.toInt() and 255 }
            if (bytes.size == 16) {
                // Only global unicast; exclude documentation, Teredo and 6to4 tunnelling ranges.
                return bytes[0] in 0x20..0x3f &&
                    !(bytes[0] == 0x20 && bytes[1] == 0x01 &&
                        ((bytes[2] == 0x0d && bytes[3] == 0xb8) || bytes[2] < 2)) &&
                    !(bytes[0] == 0x20 && bytes[1] == 0x02) && !(bytes[0] == 0x3f && bytes[1] == 0xff)
            }
            if (bytes.size != 4) return false
            val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
            return a !in setOf(0, 10, 127) && a < 224 &&
                !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && (b == 168 || b == 0 || b == 88 && c == 99)) &&
                !(a == 198 && (b in 18..19 || b == 51 && c == 100)) && !(a == 203 && b == 0 && c == 113)
        }
    }
}
