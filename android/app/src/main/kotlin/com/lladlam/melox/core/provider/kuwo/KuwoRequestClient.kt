package com.lladlam.melox.core.provider.kuwo

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class KuwoRequestClient(
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    companion object {
        internal const val UserAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }

    fun get(
        baseUrl: String,
        path: String = "",
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val url = buildUrl(baseUrl, path, params)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("酷我音乐请求失败：HTTP ${response.code}")
            }
            val bodyBytes = response.body.bytes()
            // search.kuwo.cn declares GB2312 even though the payload is UTF-8.
            val raw = bodyBytes.toString(Charsets.UTF_8)
            val json = KuwoJsonNormalizer.normalize(raw)
            return runCatching { JSONObject(json) }
                .getOrElse { throw IOException("酷我音乐返回了无法解析的数据", it) }
        }
    }

    fun getBytes(
        baseUrl: String,
        path: String = "",
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val url = buildUrl(baseUrl, path, params)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", "*/*")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("酷我音乐请求失败：HTTP ${response.code}")
            }
            return response.body.bytes()
        }
    }

    private fun buildUrl(baseUrl: String, path: String, params: Map<String, String>): HttpUrl {
        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val relativePath = path.removePrefix("/")
        return normalizedBase.toHttpUrl().newBuilder()
            .addPathSegments(relativePath)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
    }
}
