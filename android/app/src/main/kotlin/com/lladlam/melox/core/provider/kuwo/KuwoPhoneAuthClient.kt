package com.lladlam.melox.core.provider.kuwo

import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val KUWO_AUTH_BASE_URL = "http://ar.i.kuwo.cn/US_NEW/kuwo"
private const val SEND_SMS_PATH = "/send_sms"
private const val LOGIN_SMS_PATH = "/login_sms"
private const val SECRET_SALT = "imbadboy@!153"
private const val REQUEST_DES_KEY = "kwks&@69"
private const val TYPE_REGISTER_LOGIN = "1"

/**
 * Kuwo 手机号登录客户端。
 *
 * 签名算法：
 *   p1 = MD5("imbadboy@!153") 大写
 *   p2 = MD5(mobile + type + tm) 大写
 *   secret = MD5(p1 + p2) 大写
 *
 * 设备参数使用与官方 Android 客户端相近的静态值；登录后返回的 token / uid 保存在
 * [KuwoSessionStore]。
 */
class KuwoPhoneAuthClient(
    private val httpClient: OkHttpClient = MeloXHttpClient.shared,
) {
    private val smsTimestamps = mutableMapOf<String, String>()
    // Kuwo reuses this eight-byte key for both requests and response decryption.
    private val sessionSx = (System.currentTimeMillis().toString() + "12345678").take(8)


    /** 向指定手机号发送短信验证码。 */
    suspend fun sendCode(phone: String) = withContext(Dispatchers.IO) {
        require(phone.length in 5..15) { "请输入有效手机号" }
        val tm = System.currentTimeMillis().toString()
        val params = authParams(mobile = phone, type = TYPE_REGISTER_LOGIN, tm = tm)
        val body = buildString {
            append("mobile=").append(phone)
            append("&type=").append(TYPE_REGISTER_LOGIN)
            append("&tm=").append(tm)
            append("&secret=").append(params.secret)
            appendCommonParams(includeUser = true)
        }
        val response = get(SEND_SMS_PATH, body)
        val code = response.optInt("code", -1)
        val result = response.optString("ret", response.optString("result"))
        if (code != 200 && !result.equals("succ", ignoreCase = true)) {
            throw IOException(response.optString("msg").ifBlank { "验证码发送失败（$code）" })
        }
        synchronized(smsTimestamps) { smsTimestamps[phone] = tm }
    }

    /**
     * 使用短信验证码登录，返回保存后的 [KuwoSession]。
     */
    suspend fun login(phone: String, code: String): KuwoSession = withContext(Dispatchers.IO) {
        require(phone.length in 5..15) { "请输入有效手机号" }
        require(code.length >= 4) { "请输入有效验证码" }
        val tm = synchronized(smsTimestamps) { smsTimestamps[phone] }
            ?: throw IOException("请先获取验证码")
        val body = buildString {
            append("mobile=").append(phone)
            append("&code=").append(code)
            append("&tm=").append(tm)
            appendCommonParams(includeUser = false)
        }
        val response = get(LOGIN_SMS_PATH, body)
        parseLoginResponse(response)
    }

    private fun get(path: String, plaintext: String): JSONObject {
        val encrypted = encryptRequest(plaintext)
        val request = Request.Builder()
            .url("$KUWO_AUTH_BASE_URL$path?f=ar&q=${URLEncoder.encode(encrypted, Charsets.UTF_8.name())}")
            .header("User-Agent", KuwoRequestClient.UserAgent)
            .header("Accept", "application/json, */*")
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("酷我登录请求失败：HTTP ${response.code}")
            }
            val bytes = response.body.bytes()
            decodeResponse(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun decodeResponse(raw: String): JSONObject {
        val text = raw.trim()
        runCatching { JSONObject(text) }.getOrNull()?.let { return it }

        val decrypted = runCatching {
            val encoded = text.removeSurrounding("\"")
            val ciphertext = Base64.getDecoder().decode(encoded)
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(sessionSx.toByteArray(Charsets.UTF_8), "DES"),
            )
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8).trimEnd('\u0000')
        }.getOrElse { throw IOException("酷我登录返回了无法解析的数据", it) }

        runCatching { JSONObject(decrypted) }.getOrNull()?.let { return it }

        val fields = decrypted
            .split('&', '\n', '\r', ';')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
            }
        if (fields.isEmpty()) throw IOException("酷我登录返回了无法解析的数据")
        return JSONObject().apply {
            fields.forEach { (key, value) ->
                put(key, runCatching { JSONObject(value) }.getOrDefault(value))
            }
        }
    }

    private fun encryptRequest(plaintext: String): String {
        val input = plaintext.toByteArray(Charsets.UTF_8)
        // Kuwo's x0.c implementation is not PKCS5: it zero-fills and always
        // appends a complete block, including when the input is block-aligned.
        val paddedLength = input.size + (8 - input.size % 8)
        val padded = ByteArray(paddedLength).also { input.copyInto(it) }
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(REQUEST_DES_KEY.toByteArray(Charsets.UTF_8), "DES"))
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded))
    }

    private fun StringBuilder.appendCommonParams(includeUser: Boolean) {
        append("&src=kwplayer_ar")
        append("&version=12.2.0.0")
        append("&dev_id=").append(KuwoDevice.devId)
        if (includeUser) append("&user=")
        append("&dev_name=Android")
        append("&devType=Pixel")
        append("&sx=").append(sessionSx)
        append("&from=android")
        append("&devResolution=1080*1920")
    }

    internal fun parseLoginResponse(response: JSONObject): KuwoSession {
        val success = response.optBoolean("success", false)
        val status = response.optString("status")
        if (!success || status != "-1") {
            throw IOException(
                response.stringValue("msg") ?: response.stringValue("message")
                    ?: "酷我登录失败（status=$status）",
            )
        }

        val data = response.optJSONObject("userInfo")
            ?: response.optString("userInfo").takeIf(String::isNotBlank)?.let { value ->
                runCatching { JSONObject(value) }.getOrNull()
            }
            ?: throw IOException("登录响应缺少 userInfo")
        val token = data.stringValue("sessionId")
            ?: data.stringValue("sid")
            ?: data.stringValue("loginSid")
            ?: throw IOException("登录响应缺少 sessionId/sid")
        val userId = data.stringValue("uid")
            ?: data.stringValue("loginUid")
            ?: data.stringValue("userid")
            ?: data.stringValue("user_id")
            ?: throw IOException("登录响应缺少 uid")
        val nickname = data.stringValue("nickname")
            ?: data.stringValue("uname")
            ?: ""

        return KuwoSession(token = token, userId = userId, nickname = nickname)
    }

    internal data class AuthParams(
        val secret: String,
    )

    internal companion object {
        fun authParams(mobile: String, type: String, tm: String): AuthParams {
            val p1 = md5Uppercase(SECRET_SALT)
            val p2 = md5Uppercase("$mobile$type$tm")
            val secret = md5Uppercase("$p1$p2")
            return AuthParams(secret)
        }

        private fun md5Uppercase(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02X".format(it.toInt() and 0xff) }
        }
    }
}

private fun JSONObject.stringValue(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

/** Stable device identity for Kuwo login. */
internal object KuwoDevice {
    val devId: String = UUID.randomUUID().toString().replace("-", "")
}
