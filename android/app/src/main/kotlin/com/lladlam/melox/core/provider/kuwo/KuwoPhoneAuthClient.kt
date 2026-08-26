package com.lladlam.melox.core.provider.kuwo

import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val KUWO_AUTH_BASE_URL = "http://ar.i.kuwo.cn/US_NEW/kuwo"
private const val SEND_SMS_PATH = "/send_sms"
private const val LOGIN_SMS_PATH = "/login_sms"
private const val SECRET_SALT = "imbadboy@!153"
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

    /** 向指定手机号发送短信验证码。 */
    suspend fun sendCode(phone: String) = withContext(Dispatchers.IO) {
        require(phone.length in 5..15) { "请输入有效手机号" }
        val tm = System.currentTimeMillis().toString()
        val params = authParams(mobile = phone, type = TYPE_REGISTER_LOGIN, tm = tm)
        val body = FormBody.Builder()
            .add("mobile", phone)
            .add("type", TYPE_REGISTER_LOGIN)
            .add("tm", tm)
            .add("secret", params.secret)
            .addAllCommon()
            .build()

        val response = post(SEND_SMS_PATH, body)
        val code = response.optInt("code", -1)
        if (code != 200) {
            throw IOException(response.optString("msg").ifBlank { "验证码发送失败（$code）" })
        }
    }

    /**
     * 使用短信验证码登录，返回保存后的 [KuwoSession]。
     */
    suspend fun login(phone: String, code: String): KuwoSession = withContext(Dispatchers.IO) {
        require(phone.length in 5..15) { "请输入有效手机号" }
        require(code.length >= 4) { "请输入有效验证码" }
        val tm = System.currentTimeMillis().toString()
        val params = authParams(mobile = phone, type = TYPE_REGISTER_LOGIN, tm = tm)
        val body = FormBody.Builder()
            .add("mobile", phone)
            .add("code", code)
            .add("tm", tm)
            .add("secret", params.secret)
            .addAllCommon()
            .build()

        val response = post(LOGIN_SMS_PATH, body)
        parseLoginResponse(response)
    }

    private fun post(path: String, body: FormBody): JSONObject {
        val request = Request.Builder()
            .url("$KUWO_AUTH_BASE_URL$path?f=ar&q=")
            .header("User-Agent", KuwoRequestClient.UserAgent)
            .header("Accept", "application/json, */*")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("酷我登录请求失败：HTTP ${response.code}")
            }
            val bytes = response.body.bytes()
            // Kuwo auth endpoints historically return GBK/GB2312 for some fields.
            val text = bytes.toString(Charsets.UTF_8)
            runCatching { JSONObject(text) }
                .getOrElse { throw IOException("酷我登录返回了无法解析的数据", it) }
        }
    }

    private fun parseLoginResponse(response: JSONObject): KuwoSession {
        val code = response.optInt("code", -1)
        if (code != 200) {
            throw IOException(response.optString("msg").ifBlank { "酷我登录失败（$code）" })
        }

        // The login response usually wraps user info under "data" or directly in the root.
        val data = response.optJSONObject("data") ?: response
        val token = data.stringValue("token")
            ?: data.stringValue("sid")
            ?: throw IOException("登录响应缺少 token")
        val userId = data.stringValue("uid")
            ?: data.stringValue("userid")
            ?: data.stringValue("user_id")
            ?: data.stringValue("id")
            ?: throw IOException("登录响应缺少用户标识")
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
            return BigInteger(1, digest).toString(16).uppercase().padStart(32, '0')
        }
    }
}

/** Common device parameters used by the official Kuwo Android client. */
private fun FormBody.Builder.addAllCommon(): FormBody.Builder = this
    .add("src", "kwplayer_ar")
    .add("version", "12.2.0.0")
    .add("dev_id", KuwoDevice.devId)
    .add("dev_name", "Android")
    .add("devType", "Pixel")
    .add("sx", "0")
    .add("from", "android")
    .add("devResolution", "1080*1920")

private fun JSONObject.stringValue(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

/** Stable device identity for Kuwo login. */
internal object KuwoDevice {
    val devId: String = UUID.randomUUID().toString().replace("-", "")
}
