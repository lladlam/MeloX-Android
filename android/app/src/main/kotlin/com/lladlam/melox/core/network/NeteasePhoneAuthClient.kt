package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

internal class NeteasePhoneAuthClient(
    httpClient: OkHttpClient = MeloXHttpClient.shared,
) {
    private var requestCookie = ""
    private val eapi = NeteaseAuthenticatedEapi(cookieProvider = { requestCookie }, httpClient = httpClient)

    suspend fun sendCode(countryCode: String, phone: String) = withContext(Dispatchers.IO) {
        val response = eapi.postWithResponseCookies(
            uri = "/api/sms/captcha/sent",
            data = JSONObject().put("cellphone", phone).put("ctcode", countryCode),
            authenticated = false,
        )
        requestCookie = mergeNeteaseResponseCookies(requestCookie, response.setCookieHeaders)
        response.authError()?.let { throw IOException(it) }
    }

    suspend fun login(countryCode: String, phone: String, code: String): String = withContext(Dispatchers.IO) {
        val response = eapi.postWithResponseCookies(
            uri = "/api/w/login/cellphone",
            data = JSONObject()
                .put("phone", phone)
                .put("countrycode", countryCode)
                .put("remember", true)
                .put("type", 1)
                .put("captcha", code),
            authenticated = false,
        )
        val bodyCookie = response.body.optString("cookie").takeIf(String::isNotBlank).orEmpty()
        requestCookie = mergeNeteaseResponseCookies(
            mergeNeteaseResponseCookies(requestCookie, bodyCookie.split(';')),
            response.setCookieHeaders,
        )
        response.authError()?.let { throw IOException(it) }
        if (!NeteaseSessionStore.containsMusicU(requestCookie)) {
            throw IOException("登录响应未包含有效会话，请使用网页登录或稍后重试")
        }
        NeteaseSessionStore.normalizeCookie(requestCookie)
    }
}

private fun NeteaseEapiResponse.authError(): String? =
    neteasePhoneAuthError(body) ?: if (httpCode !in 200..299) "网易云请求失败：HTTP $httpCode" else null

internal fun mergeNeteaseResponseCookies(
    cookieHeader: String,
    setCookieHeaders: List<String>,
): String {
    val values = NeteaseSessionStore.parseCookie(cookieHeader).toMutableMap()
    setCookieHeaders.forEach { header ->
        val pair = header.substringBefore(';').trim().split('=', limit = 2)
        if (pair.size != 2 || pair[0].isBlank()) return@forEach
        val key = pair[0].trim()
        val value = pair[1].trim()
        if (value.isEmpty() || header.split(';').any { it.trim().equals("Max-Age=0", ignoreCase = true) }) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }
    return values.toSortedMap().entries.joinToString("; ") { (key, value) -> "$key=$value" }
}

internal fun neteasePhoneAuthError(response: JSONObject): String? {
    val code = response.optInt("code", -1)
    if (code in 200..299) return null
    val challengeData = response.optJSONObject("data")
    val loginExtData = response.optJSONObject("loginExtData")
    val hasChallengeField = listOfNotNull(response, challengeData, loginExtData).any {
        it.optString("redirectUrl").isNotBlank() || it.optString("checkToken").isNotBlank()
    }
    val hasRiskChallenge = code in setOf(702, 8810, 8820, 8830, 8860) ||
        code in 10001..10004 ||
        hasChallengeField
    if (hasRiskChallenge) {
        return "网易云要求额外安全验证，请使用网页登录完成验证"
    }
    return response.optString("message")
        .ifBlank { response.optString("msg") }
        .ifBlank { "网易云请求失败（$code）" }
}
