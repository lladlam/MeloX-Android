package com.lladlam.melox.core.provider.kugou

import java.io.IOException
import java.security.MessageDigest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class KugouRequestClient(
    private val sessionProvider: () -> KugouSession,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    companion object {
        const val AppId = 1005
        const val ClientVersion = 20489
        private const val AndroidSignatureSalt = "OIlwieks28dk2k092lksi2UIkp"
        internal const val UserAgent = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    }

    fun get(
        baseUrl: String = "https://gateway.kugou.com",
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        includeDefaults: Boolean = true,
        sign: Boolean = true,
    ): JSONObject = execute(
        method = "GET",
        baseUrl = baseUrl,
        path = path,
        params = params,
        body = null,
        headers = headers,
        includeDefaults = includeDefaults,
        sign = sign,
    )

    fun post(
        baseUrl: String = "https://gateway.kugou.com",
        path: String,
        params: Map<String, String> = emptyMap(),
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
        includeDefaults: Boolean = true,
        sign: Boolean = true,
    ): JSONObject = execute(
        method = "POST",
        baseUrl = baseUrl,
        path = path,
        params = params,
        body = body.toString(),
        headers = headers,
        includeDefaults = includeDefaults,
        sign = sign,
    )

    private fun execute(
        method: String,
        baseUrl: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        headers: Map<String, String>,
        includeDefaults: Boolean,
        sign: Boolean,
    ): JSONObject {
        val session = sessionProvider()
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val requestParams = linkedMapOf<String, String>()
        if (includeDefaults) {
            requestParams["dfid"] = session.dfid.ifBlank { "-" }
            requestParams["mid"] = session.mid
            requestParams["uuid"] = "-"
            requestParams["appid"] = AppId.toString()
            requestParams["clientver"] = ClientVersion.toString()
            requestParams["clienttime"] = clientTime
            if (session.token.isNotBlank()) requestParams["token"] = session.token
            if (session.userId > 0L) requestParams["userid"] = session.userId.toString()
        }
        requestParams.putAll(params)
        if (sign && "signature" !in requestParams) {
            requestParams["signature"] = androidSignature(requestParams, body.orEmpty())
        }

        val url = buildUrl(baseUrl, path, requestParams)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("dfid", session.dfid.ifBlank { "-" })
            .header("mid", session.mid)
            .header("clienttime", requestParams["clienttime"] ?: clientTime)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .header("Cookie", cookieHeader(session))
        headers.forEach(builder::header)

        val request = if (method == "POST") {
            builder.post(
                body.orEmpty().toRequestBody("application/json; charset=utf-8".toMediaType()),
            ).build()
        } else {
            builder.get().build()
        }

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("酷狗音乐请求失败：HTTP ${response.code}")
            }
            if (responseBody.isBlank()) throw IOException("酷狗音乐返回了空响应")
            val result = runCatching { JSONObject(responseBody) }
                .getOrElse { throw IOException("酷狗音乐返回了无法解析的数据", it) }
            val status = result.optInt("status", 1)
            val errorCode = result.optInt("error_code", 0)
            if (status == 0 || errorCode != 0) {
                val message = result.optString("error_msg")
                    .ifBlank { result.optString("msg") }
                    .ifBlank { result.optString("message") }
                    .ifBlank { "请求失败" }
                throw IOException("酷狗音乐请求失败：$message")
            }
            return result
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

    private fun cookieHeader(session: KugouSession): String =
        session.asCookieMap().entries.joinToString("; ") { (key, value) -> "$key=$value" }

    internal fun androidSignature(params: Map<String, String>, body: String = ""): String {
        val parameterString = params.keys.sorted().joinToString("") { key -> "$key=${params[key].orEmpty()}" }
        return md5Hex("$AndroidSignatureSalt$parameterString$body$AndroidSignatureSalt")
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
