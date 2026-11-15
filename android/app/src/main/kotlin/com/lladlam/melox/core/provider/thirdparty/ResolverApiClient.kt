package com.lladlam.melox.core.provider.thirdparty

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal const val ResolverResponsePreviewChars = 4_096

internal data class ResolverTestResult(
    val url: String? = null,
    val httpStatus: Int? = null,
    val contentType: String? = null,
    val responseBody: String? = null,
    val responseTruncated: Boolean = false,
    val requestError: String? = null,
)

/** Bounded, cookie-free API traffic; never alters the official providers' HTTP client. */
internal class ResolverApiClient(
    private val http: OkHttpClient = MeloXHttpClient.thirdPartyAndMedia.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    private val now: () -> Long = System::currentTimeMillis,
    private val report: (String) -> Unit = {},
) {
    private data class Entry(val url: String?, val expires: Long)
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Entry>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 128
    }
    private val blocked = object : LinkedHashMap<String, Long>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 16
    }
    private val pending = ConcurrentHashMap<String, CompletableFuture<String?>>()

    /** Explicit tests always make a fresh request and never log response bodies. */
    fun test(settings: ResolverSettings, track: MusicTrack, quality: AudioQualityTier): ResolverTestResult {
        val url = resolverRequestUrl(settings, track, quality) ?: return ResolverTestResult()
        var status: Int? = null
        var contentType: String? = null
        val result = try {
            http.newCall(buildRequest(url)).execute().use { response ->
                status = response.code
                contentType = response.body.contentType()?.let { "${it.type}/${it.subtype}" }
                readResolverTestResponse(response)
            }
        } catch (error: IOException) {
            ResolverTestResult(httpStatus = status, contentType = contentType,
                requestError = "${error.javaClass.simpleName}: ${error.message.orEmpty()}")
        }
        return result.forDisplay(settings)
    }

    fun resolve(settings: ResolverSettings, track: MusicTrack, quality: AudioQualityTier): String? {
        val url = resolverRequestUrl(settings, track, quality) ?: return null
        val identity = settings.cacheIdentity()
        val key = "$identity:${track.id.sourceId.value}:${track.id.value}:${quality.name}"
        synchronized(lock) {
            cache[key]?.takeIf { now() < it.expires }?.let { return it.url }
            if (now() < (blocked[identity] ?: 0L)) return null
        }
        val future = CompletableFuture<String?>()
        val existing = pending.putIfAbsent(key, future)
        if (existing != null) return try {
            existing.get(9, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (_: Exception) { null }

        try {
            // Another owner may have completed between our initial cache check
            // and putIfAbsent. Do not issue the same request a second time.
            synchronized(lock) {
                cache[key]?.takeIf { now() < it.expires }?.let {
                    future.complete(it.url)
                    return it.url
                }
                if (now() < (blocked[identity] ?: 0L)) return null
            }
            val request = buildRequest(url)
            val result = try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in setOf(401, 403, 429) || response.code >= 500) block(identity)
                        report("${settings.kind.label}: HTTP ${response.code}")
                        null
                    } else readResolverResponse(response)
                }
            } catch (_: IOException) {
                block(identity)
                report("${settings.kind.label}: request failed; cooling down")
                null
            }
            synchronized(lock) {
                cache[key] = Entry(result, now() + if (result == null) 30_000L else ResolverUrlTtlMs)
            }
            future.complete(result)
            return result
        } finally {
            // Do not leave concurrent readers hanging if a programming error escapes.
            future.complete(null)
            pending.remove(key, future)
        }
    }

    private fun buildRequest(url: HttpUrl): Request = Request.Builder().url(url).header("User-Agent", "MeloX-Android")
        .header("Accept", "application/json, text/plain, audio/*;q=0.9, */*;q=0.1").build()

    private fun block(identity: String) = synchronized(lock) { blocked[identity] = now() + 60_000L }
}

internal fun readResolverResponse(response: Response): String? = readResolverTestResponse(response).url

private fun readResolverTestResponse(response: Response): ResolverTestResult {
    val mime = response.body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty().lowercase()
    val result = ResolverTestResult(httpStatus = response.code, contentType = mime.ifBlank { null })
    if (mime.startsWith("audio/") || mime in setOf(
            "application/octet-stream", "application/vnd.apple.mpegurl", "application/x-mpegurl", "video/mp4",
        )) {
        // Redirects are handled by upstream's isolated media client. Closing the
        // response immediately is essential: never download a song as JSON/text.
        return result.copy(url = if (response.isSuccessful) ResolverAudioUrl.valid(response.request.url.toString()) else null)
    }
    if (mime.startsWith("image/")) return result
    val limit = 64L * 1024
    val source = response.body.source()
    if (response.body.contentLength() > limit) {
        source.request(ResolverResponsePreviewChars.toLong())
        return result.copy(responseBody = source.buffer.clone().readUtf8(minOf(source.buffer.size, ResolverResponsePreviewChars.toLong())),
            responseTruncated = true)
    }
    if (source.request(limit + 1)) {
        return result.copy(responseBody = source.buffer.clone().readUtf8(limit), responseTruncated = true)
    }
    // PHP APIs sometimes leave the default text/html MIME type even for JSON
    // or a plain URL. Parse the bounded body, not the MIME label; real HTML
    // markup is not a valid resolver payload and will be rejected by the parser.
    val body = response.body.string()
    val url = if (response.isSuccessful) ResolverAudioUrl.parse(body) else null
    return result.copy(url = url, responseBody = if (url == null) body else null)
}

private fun ResolverTestResult.forDisplay(settings: ResolverSettings): ResolverTestResult {
    val sensitiveNames = setOf("auth", "apikey", "api_key", "token", "access_token", "key", "secret")
    val endpointCredentials = settings.endpoint.toHttpUrlOrNull()?.let { endpoint ->
        endpoint.queryParameterNames.filter { it.lowercase() in sensitiveNames }
            .flatMap { endpoint.queryParameterValues(it) }.filterNotNull()
    }.orEmpty()
    val credentials = (listOf(settings.credential) + endpointCredentials).filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    fun redact(text: String) = credentials.fold(text) { result, credential -> redactResolverCredential(result, credential) }
    val body = responseBody?.let(::redact)
    val error = requestError?.let(::redact)
    return copy(responseBody = body?.take(ResolverResponsePreviewChars),
        responseTruncated = responseTruncated || (body?.length ?: 0) > ResolverResponsePreviewChars,
        requestError = error?.take(ResolverResponsePreviewChars))
}

private fun redactResolverCredential(text: String, credential: String): String {
    if (credential.isBlank()) return text
    val encoded = URLEncoder.encode(credential, "UTF-8")
    val variants = listOf(credential, encoded, encoded.replace("+", "%20"),
        JSONObject.quote(credential).removeSurrounding("\"")).distinct().sortedByDescending { it.length }
    return variants.fold(text) { redacted, value -> redacted.replace(value, "[redacted]") }
}
