package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Shared EAPI transport used by feature modules. */
internal class NeteaseAuthenticatedEapi(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val syntheticDeviceId = randomHex(26).uppercase()

    fun post(
        uri: String,
        data: JSONObject = JSONObject(),
        authenticated: Boolean = true,
        domain: String = "https://interface.music.163.com",
        cookieOs: String? = null,
    ): JSONObject = postWithResponseCookies(uri, data, authenticated, domain, cookieOs).let { response ->
        if (response.httpCode !in 200..299) throw IOException("网易云请求失败：HTTP ${response.httpCode}")
        val result = response.body
        val code = result.optInt("code", 200)
        if (code !in 200..299) throw IOException(result.optString("message").ifBlank { result.optString("msg") }.ifBlank { "请求失败（$code）" })
        result
    }

    fun postWithResponseCookies(
        uri: String,
        data: JSONObject = JSONObject(),
        authenticated: Boolean = true,
        domain: String = "https://interface.music.163.com",
        cookieOs: String? = null,
    ): NeteaseEapiResponse {
        val cookie = cookieProvider()
        if (authenticated && !NeteaseSessionStore.containsMusicU(cookie)) throw IOException("请先登录网易云音乐")
        val now = System.currentTimeMillis()
        val cookies = NeteaseSessionStore.parseCookie(cookie)
        val header = if (authenticated) authenticatedHeader(cookies, now, cookieOs) else JSONObject()
            .put("os", cookieOs ?: "ios").put("appver", "9.0.90").put("osver", "18.0")
            .put("buildver", (now / 1_000L).toString()).put("channel", "distribution")
            .put("requestId", "${now}_0000").put("__csrf", "")
        val payload = JSONObject(data.toString()).put("header", header).put("e_r", false)
        val json = payload.toString()
        val digest = md5Hex("nobody${uri}use${json}md5forencrypt")
        val encrypted = "$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params = aes(encrypted.toByteArray(), "e82ckenh8dichen8".toByteArray()).toHex()
        val requestBuilder = Request.Builder()
            .url("${domain.trimEnd('/')}${uri.replace("/api/", "/eapi/")}")
            .header("Accept", "*/*")
            .header("User-Agent", if (cookieOs == "osx") "NeteaseMusic 3.0.18 (Macintosh; Intel Mac OS X 14_5)" else if (authenticated) "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)" else "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148")
        if (authenticated) {
            requestBuilder.header("Cookie", encodedCookie(header))
        } else if (cookie.isNotBlank()) {
            requestBuilder.header("Cookie", NeteaseSessionStore.normalizeCookie(cookie))
        }
        val request = requestBuilder.post(FormBody.Builder().add("params", params).build()).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (body.isBlank()) {
                if (!response.isSuccessful) throw IOException("网易云请求失败：HTTP ${response.code}")
                throw IOException("网易云返回了空响应")
            }
            return NeteaseEapiResponse(
                body = JSONObject(body),
                setCookieHeaders = response.headers.values("Set-Cookie"),
                httpCode = response.code,
            )
        }
    }

    private fun authenticatedHeader(cookies: Map<String, String>, now: Long, cookieOs: String?) = JSONObject()
        .put("osver", cookies["osver"] ?: if (cookieOs == "osx") "14.5" else "16.2")
        .put("deviceId", cookies["deviceId"] ?: syntheticDeviceId).put("os", cookieOs ?: cookies["os"] ?: "iPhone OS")
        .put("appver", cookies["appver"] ?: "9.0.90").put("versioncode", cookies["versioncode"] ?: "140")
        .put("buildver", cookies["buildver"] ?: (now / 1000L).toString()).put("resolution", cookies["resolution"] ?: "1170x2532")
        .put("__csrf", cookies["__csrf"] ?: "").put("channel", cookies["channel"] ?: "distribution")
        .put("requestId", "${now}_${randomDigits(4)}").apply { cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { put("MUSIC_U", it) } }
    private fun encodedCookie(value: JSONObject): String = buildList { val keys = value.keys(); while (keys.hasNext()) add(keys.next()) }.sorted().joinToString("; ") { key -> "${encode(key)}=${encode(value.optString(key))}" }
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun randomHex(count: Int): String { val bytes = ByteArray(count); SecureRandom().nextBytes(bytes); return bytes.joinToString("") { "%02x".format(it) } }
    private fun randomDigits(count: Int) = buildString(count) { repeat(count) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) } }
    private fun md5Hex(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun aes(data: ByteArray, key: ByteArray) = Cipher.getInstance("AES/ECB/PKCS5Padding").run { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data) }
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
}

internal data class NeteaseEapiResponse(
    val body: JSONObject,
    val setCookieHeaders: List<String>,
    val httpCode: Int,
)
