package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val QQ_QR_LOGIN_LIFETIME_MS = 2 * 60 * 1_000L
private const val WECHAT_QR_LOGIN_LIFETIME_MS = 5 * 60 * 1_000L
private const val QQ_QR_APP_ID = "716027609"
private const val QQ_MUSIC_OAUTH_APP_ID = "100497308"
private const val QQ_LOGIN_DAID = "383"
private const val QQ_MUSIC_WECHAT_APP_ID = "wx48db31d50e334801"
private const val QQ_MUSIC_WECHAT_REDIRECT_URL =
    "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/"
private const val QQ_LOGIN_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "Chrome/124.0.0.0 Safari/537.36 MeloX/0.5"

internal enum class QQMusicQrLoginEvent {
    Waiting,
    Scanned,
    Connected,
    Expired,
    Rejected,
}

internal data class QQMusicPtuiResult(
    val event: QQMusicQrLoginEvent,
    val message: String,
    val uin: String = "",
    val sigX: String = "",
    val authorizationCode: String = "",
)

internal data class QQMusicQrCredential(
    val musicId: String,
    val musicKey: String,
    val openId: String,
    val accessToken: String,
    val refreshToken: String,
    val unionId: String,
    val encryptedUin: String,
    val loginType: Int,
)

enum class QQMusicQrLoginMethod {
    QQ,
    WeChat,
}

class QQMusicQrLoginSession internal constructor(
    val method: QQMusicQrLoginMethod,
    val imageBytes: ByteArray,
    val imageMimeType: String,
    internal val qrToken: String,
    internal val expiresAtMillis: Long,
    internal val httpClient: OkHttpClient,
    internal val cookieJar: QQMusicLoginCookieJar,
) {
    internal var lastWechatStatus: Int? = null
}

sealed interface QQMusicQrLoginState {
    data object Waiting : QQMusicQrLoginState
    data object Scanned : QQMusicQrLoginState
    data object Expired : QQMusicQrLoginState
    data object Rejected : QQMusicQrLoginState
    data class Authorized(val cookie: String) : QQMusicQrLoginState
}

/** Native QQ QR authorization flow, ported from OmniMusic without a WebView. */
class QQMusicQrLoginClient(
    private val baseHttpClient: OkHttpClient = MeloXHttpClient.shared,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun createSession(
        method: QQMusicQrLoginMethod = QQMusicQrLoginMethod.QQ,
    ): QQMusicQrLoginSession = withContext(Dispatchers.IO) {
        val cookieJar = QQMusicLoginCookieJar()
        val client = baseHttpClient.newBuilder()
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(35, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
        when (method) {
            QQMusicQrLoginMethod.QQ -> createQQSession(client, cookieJar)
            QQMusicQrLoginMethod.WeChat -> createWechatSession(client, cookieJar)
        }
    }

    private fun createQQSession(
        client: OkHttpClient,
        cookieJar: QQMusicLoginCookieJar,
    ): QQMusicQrLoginSession {
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow".toHttpUrl().newBuilder()
            .addQueryParameter("appid", QQ_QR_APP_ID)
            .addQueryParameter("e", "2")
            .addQueryParameter("l", "M")
            .addQueryParameter("s", "3")
            .addQueryParameter("d", "72")
            .addQueryParameter("v", "4")
            .addQueryParameter("t", (nowMillis() * 1_000_000L).toString())
            .addQueryParameter("daid", QQ_LOGIN_DAID)
            .addQueryParameter("pt_3rd_aid", QQ_MUSIC_OAUTH_APP_ID)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("获取 QQ 登录二维码返回 ${response.code}")
            }
            val qrSignature = cookieJar.loadForRequest(url)
                .firstOrNull { it.name == "qrsig" }
                ?.value
                ?: throw IOException("QQ 登录服务没有返回二维码标识")
            val image = response.body.bytes()
            if (image.isEmpty()) throw IOException("读取 QQ 登录二维码失败")
            QQMusicQrLoginSession(
                method = QQMusicQrLoginMethod.QQ,
                imageBytes = image,
                imageMimeType = "image/png",
                qrToken = qrSignature,
                expiresAtMillis = nowMillis() + QQ_QR_LOGIN_LIFETIME_MS,
                httpClient = client,
                cookieJar = cookieJar,
            )
        }
    }

    private fun createWechatSession(
        client: OkHttpClient,
        cookieJar: QQMusicLoginCookieJar,
    ): QQMusicQrLoginSession {
        val loginUrl = "https://open.weixin.qq.com/connect/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("appid", QQ_MUSIC_WECHAT_APP_ID)
            .addQueryParameter("redirect_uri", QQ_MUSIC_WECHAT_REDIRECT_URL)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", "snsapi_login")
            .addQueryParameter("state", "STATE")
            .build()
        val pageRequest = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        val uuid = client.newCall(pageRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("获取微信登录二维码返回 ${response.code}")
            }
            parseQQMusicWechatQrUuid(response.body.string())
        }
        val imageUrl = "https://open.weixin.qq.com/connect/qrcode/$uuid".toHttpUrl()
        val imageRequest = Request.Builder()
            .url(imageUrl)
            .header("Referer", loginUrl.toString())
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        return client.newCall(imageRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("读取微信登录二维码返回 ${response.code}")
            }
            val image = response.body.bytes()
            if (image.isEmpty()) throw IOException("读取微信登录二维码失败")
            QQMusicQrLoginSession(
                method = QQMusicQrLoginMethod.WeChat,
                imageBytes = image,
                imageMimeType = "image/jpeg",
                qrToken = uuid,
                expiresAtMillis = nowMillis() + WECHAT_QR_LOGIN_LIFETIME_MS,
                httpClient = client,
                cookieJar = cookieJar,
            )
        }
    }

    suspend fun checkSession(session: QQMusicQrLoginSession): QQMusicQrLoginState =
        withContext(Dispatchers.IO) {
            if (nowMillis() >= session.expiresAtMillis) return@withContext QQMusicQrLoginState.Expired
            val result = when (session.method) {
                QQMusicQrLoginMethod.QQ -> pollQQ(session)
                QQMusicQrLoginMethod.WeChat -> pollWechat(session)
            }
            when (result.event) {
                QQMusicQrLoginEvent.Waiting -> QQMusicQrLoginState.Waiting
                QQMusicQrLoginEvent.Scanned -> QQMusicQrLoginState.Scanned
                QQMusicQrLoginEvent.Expired -> QQMusicQrLoginState.Expired
                QQMusicQrLoginEvent.Rejected -> QQMusicQrLoginState.Rejected
                QQMusicQrLoginEvent.Connected -> {
                    val credential = when (session.method) {
                        QQMusicQrLoginMethod.QQ -> authorizeQQ(session, result.uin, result.sigX)
                        QQMusicQrLoginMethod.WeChat -> exchangeWechatCode(
                            session.httpClient,
                            result.authorizationCode,
                        )
                    }
                    QQMusicQrLoginState.Authorized(buildQQMusicQrCredentialCookie(credential))
                }
            }
        }

    private fun pollQQ(session: QQMusicQrLoginSession): QQMusicPtuiResult {
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin".toHttpUrl().newBuilder()
            .addQueryParameter("u1", "https://graph.qq.com/oauth2.0/login_jump")
            .addQueryParameter("ptqrtoken", qqMusicHash33(session.qrToken, 0).toString())
            .addQueryParameter("ptredirect", "0")
            .addQueryParameter("h", "1")
            .addQueryParameter("t", "1")
            .addQueryParameter("g", "1")
            .addQueryParameter("from_ui", "1")
            .addQueryParameter("ptlang", "2052")
            .addQueryParameter("action", "0-0-${nowMillis()}")
            .addQueryParameter("js_ver", "20102616")
            .addQueryParameter("js_type", "1")
            .addQueryParameter("pt_uistyle", "40")
            .addQueryParameter("aid", QQ_QR_APP_ID)
            .addQueryParameter("daid", QQ_LOGIN_DAID)
            .addQueryParameter("pt_3rd_aid", QQ_MUSIC_OAUTH_APP_ID)
            .addQueryParameter("has_onekey", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        return session.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("查询 QQ 扫码状态返回 ${response.code}")
            parseQQMusicPtuiCallback(response.body.string())
        }
    }

    private fun pollWechat(session: QQMusicQrLoginSession): QQMusicPtuiResult {
        val urlBuilder = "https://long.open.weixin.qq.com/connect/l/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", session.qrToken)
            .addQueryParameter("_", nowMillis().toString())
        session.lastWechatStatus?.let { urlBuilder.addQueryParameter("last", it.toString()) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Referer", "https://open.weixin.qq.com/")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        return try {
            session.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("查询微信扫码状态返回 ${response.code}")
                parseQQMusicWechatPoll(response.body.string()).also { result ->
                    session.lastWechatStatus = when (result.event) {
                        QQMusicQrLoginEvent.Scanned -> 404
                        else -> null
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            QQMusicPtuiResult(QQMusicQrLoginEvent.Waiting, "请使用微信扫一扫扫码")
        }
    }

    private fun authorizeQQ(
        session: QQMusicQrLoginSession,
        uin: String,
        sigX: String,
    ): QQMusicQrCredential {
        val checkUrl = "https://ssl.ptlogin2.graph.qq.com/check_sig".toHttpUrl().newBuilder()
            .addQueryParameter("uin", uin)
            .addQueryParameter("pttype", "1")
            .addQueryParameter("service", "ptqrlogin")
            .addQueryParameter("nodirect", "0")
            .addQueryParameter("ptsigx", sigX)
            .addQueryParameter("s_url", "https://graph.qq.com/oauth2.0/login_jump")
            .addQueryParameter("ptlang", "2052")
            .addQueryParameter("ptredirect", "100")
            .addQueryParameter("aid", QQ_QR_APP_ID)
            .addQueryParameter("daid", QQ_LOGIN_DAID)
            .addQueryParameter("j_later", "0")
            .addQueryParameter("low_login_hour", "0")
            .addQueryParameter("regmaster", "0")
            .addQueryParameter("pt_login_type", "3")
            .addQueryParameter("pt_aid", "0")
            .addQueryParameter("pt_aaid", "16")
            .addQueryParameter("pt_light", "0")
            .addQueryParameter("pt_3rd_aid", QQ_MUSIC_OAUTH_APP_ID)
            .build()
        val checkRequest = Request.Builder()
            .url(checkUrl)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        session.httpClient.newCall(checkRequest).execute().use { response ->
            if (response.code >= 400) throw IOException("QQ 扫码凭据校验返回 ${response.code}")
        }

        val graphUrl = "https://graph.qq.com/".toHttpUrl()
        val pSKey = session.cookieJar.loadForRequest(graphUrl)
            .firstOrNull { it.name == "p_skey" }
            ?.value
            .orEmpty()
        if (pSKey.isBlank()) throw IOException("QQ 扫码校验没有返回授权票据")

        val form = FormBody.Builder()
            .add("response_type", "code")
            .add("client_id", QQ_MUSIC_OAUTH_APP_ID)
            .add("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/")
            .add("scope", "get_user_info,get_app_friends")
            .add("state", "state")
            .add("switch", "")
            .add("from_ptlogin", "1")
            .add("src", "1")
            .add("update_auth", "1")
            .add("openapi", "1010_1030")
            .add("g_tk", qqMusicHash33(pSKey, 5381).toString())
            .add("auth_time", (nowMillis() / 1_000L * 1_000L).toString())
            .add("ui", randomHex(16))
            .build()
        val authorizeRequest = Request.Builder()
            .url("https://graph.qq.com/oauth2.0/authorize")
            .post(form)
            .header("Referer", "https://graph.qq.com/oauth2.0/login_jump")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        val code = session.httpClient.newCall(authorizeRequest).execute().use { response ->
            val location = response.header("Location")
                ?: throw IOException("QQ 音乐授权没有返回登录码")
            location.toHttpUrlOrNull()?.queryParameter("code")
                ?.takeIf(String::isNotBlank)
                ?: throw IOException("QQ 音乐授权没有返回登录码")
        }
        return exchangeQQCode(session.httpClient, code)
    }

    private fun exchangeQQCode(client: OkHttpClient, code: String): QQMusicQrCredential {
        val payload = JSONObject()
            .put(
                "comm",
                JSONObject()
                    .put("ct", 24)
                    .put("cv", 4_747_474)
                    .put("platform", "yqq.json")
                    .put("chid", "0")
                    .put("uin", 0)
                    .put("g_tk", 5381)
                    .put("g_tk_new_20200303", 5381)
                    .put("format", "json")
                    .put("inCharset", "utf-8")
                    .put("outCharset", "utf-8")
                    .put("notice", 0)
                    .put("needNewCode", 1)
                    .put("tmeLoginType", 2),
            )
            .put(
                "req_0",
                JSONObject()
                    .put("module", "QQConnectLogin.LoginServer")
                    .put("method", "QQLogin")
                    .put("param", JSONObject().put("code", code)),
            )
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            val root = runCatching { JSONObject(response.body.string()) }
                .getOrElse { throw IOException("QQ 音乐登录响应解析失败", it) }
            val requestResult = root.optJSONObject("req_0") ?: JSONObject()
            val resultCode = requestResult.optInt("code", -1)
            if (!response.isSuccessful || root.optInt("code", -1) != 0 || resultCode != 0) {
                throw IOException("QQ 音乐拒绝了扫码登录（错误码 $resultCode）")
            }
            val credential = parseQQMusicQrCredential(
                requestResult.optJSONObject("data") ?: JSONObject(),
            )
            if (credential.musicId.isBlank() || credential.musicKey.isBlank()) {
                throw IOException("QQ 音乐登录成功但没有返回可用播放凭据")
            }
            credential
        }
    }

    private fun exchangeWechatCode(client: OkHttpClient, code: String): QQMusicQrCredential {
        if (code.isBlank()) throw IOException("微信扫码授权没有返回登录码")
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .post(
                buildQQMusicWechatLoginPayload(code).toString()
                    .toRequestBody("application/x-www-form-urlencoded".toMediaType()),
            )
            .header("Accept", "*/*")
            .header("Referer", QQ_MUSIC_WECHAT_REDIRECT_URL)
            .header("User-Agent", QQ_LOGIN_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            val root = runCatching { JSONObject(response.body.string()) }
                .getOrElse { throw IOException("QQ 音乐微信登录响应解析失败", it) }
            val requestResult = root.optJSONObject("req") ?: JSONObject()
            val resultCode = requestResult.optInt("code", -1)
            if (!response.isSuccessful || root.optInt("code", -1) != 0 || resultCode != 0) {
                throw IOException("QQ 音乐拒绝了微信扫码登录（错误码 $resultCode）")
            }
            val credential = parseQQMusicQrCredential(
                requestResult.optJSONObject("data") ?: JSONObject(),
            )
            if (credential.musicId.isBlank() || credential.musicKey.isBlank()) {
                throw IOException("QQ 音乐微信登录成功但没有返回可用播放凭据")
            }
            credential
        }
    }
}

internal fun parseQQMusicPtuiCallback(raw: String): QQMusicPtuiResult {
    val callback = Regex("""ptuiCB\((.*)\)""").find(raw.trim())
        ?: throw IOException("无法解析 QQ 扫码状态")
    val args = Regex("""'((?:\\.|[^'])*)'""")
        .findAll(callback.groupValues[1])
        .map { match ->
            match.groupValues[1]
                .replace("\\'", "'")
                .replace("\\\\", "\\")
        }
        .toList()
    val code = args.firstOrNull()?.toIntOrNull()
        ?: throw IOException("QQ 扫码状态码无效")
    return when (code) {
        0, 405 -> {
            val callbackUrl = args.getOrNull(2)?.toHttpUrlOrNull()
                ?: throw IOException("QQ 授权地址无效")
            val uin = callbackUrl.queryParameter("uin").orEmpty()
            val sigX = callbackUrl.queryParameter("ptsigx").orEmpty()
            if (uin.isBlank() || sigX.isBlank()) throw IOException("QQ 授权响应缺少账号参数")
            QQMusicPtuiResult(QQMusicQrLoginEvent.Connected, "扫码授权成功", uin, sigX)
        }
        66, 408 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Waiting, "请使用手机 QQ 扫码")
        67, 404 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Scanned, "已扫码，请在手机 QQ 上确认")
        65, 402 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Expired, "二维码已过期，请重新获取")
        68, 403 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Rejected, "你已取消授权，请重新扫码")
        else -> throw IOException("未知的 QQ 扫码状态：$code")
    }
}

internal fun parseQQMusicWechatQrUuid(html: String): String {
    val uuid = Regex("""/connect/qrcode/([A-Za-z0-9_-]+)""")
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    if (uuid.isBlank()) throw IOException("微信登录服务没有返回二维码标识")
    return uuid
}

internal fun parseQQMusicWechatPoll(raw: String): QQMusicPtuiResult {
    val status = Regex("""window\.wx_errcode\s*=\s*(\d+)""")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: throw IOException("无法解析微信扫码状态")
    return when (status) {
        405 -> {
            val code = Regex("""window\.wx_code\s*=\s*['\"]([^'\"]+)['\"]""")
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            if (code.isBlank()) throw IOException("微信扫码授权没有返回登录码")
            QQMusicPtuiResult(
                event = QQMusicQrLoginEvent.Connected,
                message = "微信扫码授权成功",
                authorizationCode = code,
            )
        }
        408 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Waiting, "请使用微信扫一扫扫码")
        404 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Scanned, "已扫码，请在微信中确认")
        403 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Rejected, "你已取消微信授权，请重新扫码")
        402 -> QQMusicPtuiResult(QQMusicQrLoginEvent.Expired, "微信二维码已过期，请重新获取")
        500 -> throw IOException("微信登录服务暂时繁忙，请重新生成二维码")
        else -> throw IOException("未知的微信扫码状态：$status")
    }
}

internal fun buildQQMusicWechatLoginPayload(code: String): JSONObject = JSONObject()
    .put(
        "comm",
        JSONObject()
            .put("tmeAppID", "qqmusic")
            .put("tmeLoginType", "1")
            .put("g_tk", 5381)
            .put("platform", "yqq")
            .put("ct", 24)
            .put("cv", 0),
    )
    .put(
        "req",
        JSONObject()
            .put("module", "music.login.LoginServer")
            .put("method", "Login")
            .put(
                "param",
                JSONObject()
                    .put("strAppid", QQ_MUSIC_WECHAT_APP_ID)
                    .put("code", code),
            ),
    )

internal fun qqMusicHash33(value: String, seed: Int): Int {
    var hash = seed.toLong()
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        hash += (hash shl 5) + (byte.toInt() and 0xff)
    }
    return (hash and 0x7fffffff).toInt()
}

internal fun parseQQMusicQrCredential(data: JSONObject): QQMusicQrCredential = QQMusicQrCredential(
    musicId = data.optString("str_musicid").ifBlank { data.opt("musicid")?.toString().orEmpty() },
    musicKey = data.optString("musickey"),
    openId = data.optString("openid"),
    accessToken = data.optString("access_token"),
    refreshToken = data.optString("refresh_token"),
    unionId = data.optString("unionid"),
    encryptedUin = data.optString("encryptUin"),
    loginType = data.optInt("loginType", data.optInt("login_type", 0)),
)

internal fun buildQQMusicQrCredentialCookie(credential: QQMusicQrCredential): String {
    val uin = credential.musicId.filter(Char::isDigit).trimStart('0')
    if (uin.isBlank() || credential.musicKey.isBlank()) throw IOException("QQ 音乐登录凭据不完整")
    val loginType = credential.loginType.takeIf { it != 0 }
        ?: if (credential.musicKey.startsWith("W_X")) 1 else 2
    return listOf(
        "uin" to uin,
        "qqmusic_uin" to uin,
        "qm_keyst" to credential.musicKey,
        "qqmusic_key" to credential.musicKey,
        "music_key" to credential.musicKey,
        "login_type" to loginType.toString(),
        "psrf_qqopenid" to credential.openId,
        "psrf_qqaccess_token" to credential.accessToken,
        "psrf_qqrefresh_token" to credential.refreshToken,
        "psrf_qqunionid" to credential.unionId,
        "euin" to credential.encryptedUin,
    ).filter { (_, value) -> value.isNotBlank() && value.none { it == ';' || it == '\r' || it == '\n' } }
        .joinToString("; ") { (key, value) -> "$key=$value" }
}

internal class QQMusicLoginCookieJar : CookieJar {
    private val cookies = linkedMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie -> this.cookies[cookieKey(cookie)] = cookie }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { (_, cookie) -> cookie.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }

    private fun cookieKey(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"
}

private fun randomHex(bytes: Int): String {
    val buffer = ByteArray(bytes)
    SecureRandom().nextBytes(buffer)
    return buffer.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
