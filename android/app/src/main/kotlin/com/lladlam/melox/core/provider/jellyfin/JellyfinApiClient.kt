package com.lladlam.melox.core.provider.jellyfin

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Small synchronous transport; provider methods call it from Dispatchers.IO. */
class JellyfinApiClient(
    private val httpClient: OkHttpClient,
    private val clientName: String = "MeloX",
    private val clientVersion: String = "0.5.1",
) {
    fun publicServerInfo(serverUrl: String): JellyfinServerInfo {
        val json = request(serverUrl, "/System/Info/Public", null)
        return JellyfinServerInfo(
            id = json.optString("Id"),
            name = json.optString("ServerName").ifBlank { "Jellyfin" },
            version = json.optString("Version"),
        )
    }

    fun authenticate(serverUrl: String, username: String, password: String): JellyfinSession {
        val normalized = serverUrl.trimEnd('/')
        val body = JSONObject().put("Username", username).put("Pw", password)
        val json = request(normalized, "/Users/AuthenticateByName", body)
        val user = json.optJSONObject("User") ?: throw IOException("Jellyfin 登录响应缺少用户信息")
        val token = json.optString("AccessToken").takeIf(String::isNotBlank)
            ?: throw IOException("Jellyfin 登录响应缺少 AccessToken")
        val server = publicServerInfo(normalized)
        return JellyfinSession(
            serverUrl = normalized,
            accessToken = token,
            userId = user.optString("Id"),
            serverId = server.id,
            userName = user.optString("Name").ifBlank { username },
        )
    }

    fun get(session: JellyfinSession, path: String, query: Map<String, String> = emptyMap()): JSONObject =
        request(session.baseUrl, path, null, session, query)

    fun post(session: JellyfinSession, path: String, body: JSONObject = JSONObject()): JSONObject =
        request(session.baseUrl, path, body, session)

    fun delete(session: JellyfinSession, path: String) {
        val request = Request.Builder()
            .url("${session.baseUrl}$path")
            .header("Accept", "application/json")
            .header("X-Emby-Authorization", authorizationHeader())
            .header("X-Emby-Token", session.accessToken)
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Jellyfin 请求失败：HTTP ${response.code}")
        }
    }

    private fun request(
        serverUrl: String,
        path: String,
        body: JSONObject?,
        session: JellyfinSession? = null,
        query: Map<String, String> = emptyMap(),
    ): JSONObject {
        val url = "${serverUrl.trimEnd('/')}$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Emby-Authorization", authorizationHeader())
        session?.let { builder.header("X-Emby-Token", it.accessToken) }
        body?.let { builder.post(it.toString().toRequestBody(JSON)) }
        httpClient.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("Jellyfin 请求失败：HTTP ${response.code}")
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"$clientName\", Device=\"Android\", DeviceId=\"melox-android\", Version=\"$clientVersion\""

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
