package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val QQ_MUSIC_PHONE_AUTH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
private const val QQ_MUSIC_LOGIN_MODULE = "music.login.LoginServer"

class QQMusicSecurityChallengeException(
    val securityUrl: String,
) : IOException("QQ音乐要求完成安全验证，验证后请返回并重试")

class QQMusicPhoneAuthClient(
    private val httpClient: OkHttpClient = MeloXHttpClient.shared,
) {
    private var requestCookie = ""

    suspend fun sendCode(countryCode: String, phone: String) = withContext(Dispatchers.IO) {
        require(countryCode == "86") { "QQ音乐手机号登录目前仅支持中国大陆号码" }
        val response = post(buildQQMusicSendCodePayload(phone))
        requestCookie = mergeQQMusicResponseCookies(requestCookie, response.setCookieHeaders)
        requestCookie = parseQQMusicPhoneAuthResponse(
            response = response.body,
            existingCookie = requestCookie,
            requireSession = false,
        )
    }

    suspend fun login(countryCode: String, phone: String, code: String): String = withContext(Dispatchers.IO) {
        require(countryCode == "86") { "QQ音乐手机号登录目前仅支持中国大陆号码" }
        val response = post(buildQQMusicPhoneLoginPayload(phone, code))
        requestCookie = mergeQQMusicResponseCookies(requestCookie, response.setCookieHeaders)
        parseQQMusicPhoneAuthResponse(
            response = response.body,
            existingCookie = requestCookie,
            requireSession = true,
        ).also { requestCookie = it }
    }

    private fun post(payload: JSONObject): QQMusicPhoneHttpResponse {
        val request = Request.Builder()
            .url(QQ_MUSIC_PHONE_AUTH_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .apply { if (requestCookie.isNotBlank()) header("Cookie", requestCookie) }
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("QQ音乐请求失败：HTTP ${response.code}")
            val bodyText = response.body.string()
            val body = runCatching { JSONObject(bodyText) }
                .getOrElse { throw IOException("QQ音乐返回了无法解析的响应") }
            QQMusicPhoneHttpResponse(body, response.headers.values("Set-Cookie"))
        }
    }
}

private data class QQMusicPhoneHttpResponse(
    val body: JSONObject,
    val setCookieHeaders: List<String>,
)

internal fun buildQQMusicSendCodePayload(phone: String): JSONObject =
    buildQQMusicPhonePayload(
        method = "SendPhoneAuthCode",
        param = JSONObject()
            .put("tmeAppid", "qqmusic")
            .put("phoneNo", phone)
            .put("areaCode", "86"),
    )

internal fun buildQQMusicPhoneLoginPayload(phone: String, code: String): JSONObject =
    buildQQMusicPhonePayload(
        method = "Login",
        param = JSONObject()
            .put("code", code)
            .put("phoneNo", phone)
            .put("loginMode", 1),
    )

private fun buildQQMusicPhonePayload(method: String, param: JSONObject): JSONObject = JSONObject()
    .put(
        "comm",
        JSONObject()
            .put("ct", 24)
            .put("cv", 20050009)
            .put("v", 20050009)
            .put("format", "json"),
    )
    .put(
        QQ_MUSIC_LOGIN_MODULE,
        JSONObject()
            .put("module", QQ_MUSIC_LOGIN_MODULE)
            .put("method", method)
            .put("param", param),
    )

internal fun parseQQMusicPhoneAuthResponse(
    response: JSONObject,
    existingCookie: String = "",
    setCookieHeaders: List<String> = emptyList(),
    requireSession: Boolean,
): String {
    val mergedCookies = mergeQQMusicResponseCookies(existingCookie, setCookieHeaders)
    val outerCode = response.intValue("code")
        ?: throw IOException("QQ音乐响应缺少状态码")
    if (outerCode != 0) throw IOException(response.errorMessage("QQ音乐请求失败（$outerCode）"))

    val business = response.findBusinessObject()
        ?: throw IOException("QQ音乐响应缺少登录结果")
    val businessCode = business.intValue("code")
        ?: throw IOException("QQ音乐登录结果缺少状态码")
    val data = business.objectValue("data") ?: JSONObject()
    if (businessCode != 0) {
        val securityUrl = data.stringValue("securityURL")
            .ifBlank { business.stringValue("securityURL") }
        if (businessCode == 20276 || securityUrl.isNotBlank()) {
            val safeUrl = securityUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
            if (safeUrl != null) throw QQMusicSecurityChallengeException(safeUrl)
            throw IOException("QQ音乐要求安全验证，请使用网页登录")
        }
        throw IOException(business.errorMessage(response.errorMessage("QQ音乐请求失败（$businessCode）"), data))
    }
    if (!requireSession) return mergedCookies

    val credentialObjects = buildList {
        add(data)
        listOf("data", "result").forEach { name -> data.objectValue(name)?.let(::add) }
    }
    val cookies = parseCookieHeader(mergedCookies).toMutableMap()
    val uin = cookies.firstMatchingValue(::isUsableUin, "qqmusic_uin", "uin")
        ?: credentialObjects.firstMatchingValue(::isUsableUin, "musicid", "str_musicid", "encryptUin")
        ?: throw IOException("登录响应缺少有效 QQ音乐账号标识，请使用网页登录")
    val musicKey = cookies.valueFor("qm_keyst", "qqmusic_key", "musickey")
        ?.takeIf(String::isNotBlank)
        ?: credentialObjects.firstValue("musickey", "qm_keyst", "qqmusic_key").takeIf(String::isNotBlank)
        ?: throw IOException("登录响应缺少 QQ音乐音乐密钥，请使用网页登录")

    cookies.putCanonical("qqmusic_uin", uin)
    cookies.putCanonical("qm_keyst", musicKey)
    listOf("token", "refresh_key").forEach { name ->
        credentialObjects.firstValue(name).takeIf(String::isNotBlank)?.let { cookies.putCanonical(name, it) }
    }
    return normalizeCookieHeader(cookies)
}

internal fun mergeQQMusicResponseCookies(
    cookieHeader: String,
    setCookieHeaders: List<String>,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val values = parseCookieHeader(cookieHeader).toMutableMap()
    val url = QQ_MUSIC_PHONE_AUTH_URL.toHttpUrl()
    setCookieHeaders.forEach { header ->
        val firstPair = header.substringBefore(';').split('=', limit = 2)
        if (firstPair.size != 2 || firstPair[0].isBlank()) return@forEach
        val name = firstPair[0].trim()
        val parsed = Cookie.parse(url, header)
        if (parsed == null || parsed.value.isEmpty() || parsed.expiresAt <= nowMillis) {
            values.removeCaseInsensitive(name)
        } else {
            values.putCanonical(parsed.name, parsed.value)
        }
    }
    return normalizeCookieHeader(values)
}

private fun JSONObject.findBusinessObject(): JSONObject? {
    val firstLevel = listOf(this) + listOf("data", "response", "result").mapNotNull(::objectValue)
    firstLevel.forEach { root -> root.objectValue(QQ_MUSIC_LOGIN_MODULE)?.let { return it } }
    firstLevel.drop(1).forEach { wrapper ->
        listOf("data", "response", "result").forEach { name ->
            wrapper.objectValue(name)?.objectValue(QQ_MUSIC_LOGIN_MODULE)?.let { return it }
        }
    }
    return null
}

private fun JSONObject.errorMessage(fallback: String, extra: JSONObject? = null): String =
    listOfNotNull(extra, this).firstValue("errMsg", "errTip", "message").ifBlank { fallback }

private fun List<JSONObject>.firstValue(vararg names: String): String {
    for (value in this) {
        for (name in names) value.stringValue(name).takeIf(String::isNotBlank)?.let { return it }
    }
    return ""
}

private fun List<JSONObject>.firstMatchingValue(
    predicate: (String) -> Boolean,
    vararg names: String,
): String? {
    for (value in this) {
        for (name in names) value.stringValue(name).takeIf(predicate)?.let { return it }
    }
    return null
}

private fun JSONObject.stringValue(name: String): String {
    val key = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: return ""
    val value = opt(key)
    return if (value == null || value == JSONObject.NULL || value is JSONObject) "" else value.toString().trim()
}

private fun JSONObject.intValue(name: String): Int? = stringValue(name).toIntOrNull()

private fun JSONObject.objectValue(name: String): JSONObject? {
    val key = keys().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: return null
    return optJSONObject(key)
}

private fun parseCookieHeader(cookie: String): Map<String, String> = buildMap {
    cookie.split(';').forEach { item ->
        val pair = item.trim().split('=', limit = 2)
        if (pair.size == 2 && pair[0].isNotBlank() && pair[1].isNotBlank()) put(pair[0].trim(), pair[1].trim())
    }
}

private fun MutableMap<String, String>.putCanonical(name: String, value: String) {
    removeCaseInsensitive(name)
    put(name, value)
}

private fun MutableMap<String, String>.removeCaseInsensitive(name: String) {
    keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(::remove)
}

private fun Map<String, String>.valueFor(vararg names: String): String? {
    for (name in names) entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.let { return it }
    return null
}

private fun Map<String, String>.firstMatchingValue(
    predicate: (String) -> Boolean,
    vararg names: String,
): String? {
    for (name in names) entries.firstOrNull {
        it.key.equals(name, ignoreCase = true) && predicate(it.value)
    }?.value?.let { return it }
    return null
}

private fun normalizeCookieHeader(values: Map<String, String>): String = values
    .filterValues(String::isNotBlank)
    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    .entries
    .joinToString("; ") { (key, value) -> "$key=$value" }

private fun isUsableUin(value: String): Boolean = value.isNotBlank() && value.all(Char::isDigit) && value.any { it != '0' }
