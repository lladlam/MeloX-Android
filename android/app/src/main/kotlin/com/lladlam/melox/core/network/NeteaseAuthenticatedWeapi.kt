package com.lladlam.melox.core.network

import com.lladlam.melox.core.account.NeteaseSessionStore
import java.io.IOException
import java.math.BigInteger
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Authenticated WEAPI transport matching the upstream MeloX/Netease API behavior.
 * Listen Together status is one of the routes that explicitly requires WEAPI.
 */
internal class NeteaseAuthenticatedWeapi(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    private val random = SecureRandom()
    private val syntheticDeviceId = randomHex(26).uppercase()
    private val syntheticWnmcid = "${randomString(6, LOWERCASE)}.${System.currentTimeMillis()}.01.0"
    private val publicKey: RSAPublicKey by lazy {
        val spec = X509EncodedKeySpec(Base64.getDecoder().decode(WEAPI_PUBLIC_KEY_BASE64))
        KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    fun post(uri: String, data: JSONObject = JSONObject()): JSONObject {
        val rawCookie = cookieProvider()
        if (!NeteaseSessionStore.containsMusicU(rawCookie)) throw IOException("请先登录网易云音乐")
        return postInternal(uri, data, rawCookie)
    }

    private fun postInternal(uri: String, data: JSONObject, rawCookie: String): JSONObject {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                return requestOnce(uri, data, rawCookie)
            } catch (error: IOException) {
                lastError = error
                if (!error.message.orEmpty().contains("空响应") || attempt == 2) throw error
                Thread.sleep(180L * (attempt + 1))
            }
        }
        throw IOException("网易云 WEAPI 请求失败", lastError)
    }

    private fun requestOnce(uri: String, data: JSONObject, rawCookie: String): JSONObject {
        val cookies = NeteaseSessionStore.parseCookie(rawCookie).toMutableMap()
        val csrf = cookies["__csrf"].orEmpty()
        val payload = JSONObject(data.toString()).put("csrf_token", csrf)
        val secret = randomString(16, BASE62)
        val firstPass = aesCbc(payload.toString().toByteArray(), PRESET_KEY.toByteArray())
        val firstBase64 = Base64.getEncoder().encodeToString(firstPass)
        val secondPass = aesCbc(firstBase64.toByteArray(), secret.toByteArray())
        val params = Base64.getEncoder().encodeToString(secondPass)
        val encSecKey = rsaEncrypt(secret)
        val path = uri.replace("/api/", "/weapi/")

        val request = Request.Builder()
            .url("https://music.163.com$path")
            .header("Accept", "*/*")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Referer", "https://music.163.com")
            .header("Cookie", processedCookie(cookies, uri))
            .apply { if (isNonIdempotentNeteaseApi(uri)) header(RetryInterceptor.NO_RETRY_HEADER, "true") }
            .post(FormBody.Builder().add("params", params).add("encSecKey", encSecKey).build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("网易云请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("网易云返回了空响应")
            val result = JSONObject(body)
            val code = result.optInt("code", response.code)
            if (code !in 200..299) {
                throw IOException(
                    result.optString("message")
                        .ifBlank { result.optString("msg") }
                        .ifBlank { "请求失败（$code）" } +
                        " | ${body.take(300)}",
                )
            }
            return result
        }
    }

    private fun processedCookie(values: MutableMap<String, String>, uri: String): String {
        val profile = cookieProfile(values["os"])
        val generatedNuid = randomHex(32)
        values["__remember_me"] = "true"
        values["ntes_kaola_ad"] = "1"
        values.putIfBlank("_ntes_nuid", generatedNuid)
        values.putIfBlank("_ntes_nnid", "$generatedNuid,${System.currentTimeMillis()}")
        values.putIfBlank("WNMCID", syntheticWnmcid)
        values.putIfBlank("WEVNSM", "1.0.0")
        values.putIfBlank("osver", profile.osVersion)
        values.putIfBlank("deviceId", syntheticDeviceId)
        values.putIfBlank("os", profile.os)
        values.putIfBlank("channel", profile.channel)
        values.putIfBlank("appver", profile.appVersion)
        if (!uri.contains("login")) values["NMTID"] = randomHex(16)
        return values.keys.sorted().joinToString("; ") { key ->
            "${encode(key)}=${encode(values[key].orEmpty())}"
        }
    }

    private fun MutableMap<String, String>.putIfBlank(key: String, value: String) {
        if (this[key].isNullOrBlank()) this[key] = value
    }

    private data class CookieProfile(
        val os: String,
        val appVersion: String,
        val osVersion: String,
        val channel: String,
    )

    private fun cookieProfile(os: String?): CookieProfile = when (os) {
        "linux" -> CookieProfile("linux", "1.2.1.0428", "Deepin 20.9", "netease")
        "android" -> CookieProfile("android", "8.20.20.231215173437", "14", "xiaomi")
        "iphone", "iPhone OS", "ios" -> CookieProfile("iPhone OS", "9.0.90", "16.2", "distribution")
        else -> CookieProfile("pc", "3.1.17.204416", "Microsoft-Windows-10-Professional-build-19045-64bit", "netease")
    }

    private fun aesCbc(data: ByteArray, key: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(WEAPI_IV.toByteArray()))
            doFinal(data)
        }

    private fun rsaEncrypt(secret: String): String {
        val message = BigInteger(1, secret.reversed().toByteArray())
        val encrypted = message.modPow(publicKey.publicExponent, publicKey.modulus)
        val hexLength = (publicKey.modulus.bitLength() + 3) / 4
        return encrypted.toString(16).padStart(hexLength, '0')
    }

    private fun randomString(length: Int, alphabet: String): String = buildString(length) {
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
        const val WEAPI_IV = "0102030405060708"
        const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val WEB_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
        const val WEAPI_PUBLIC_KEY_BASE64 = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    }
}
