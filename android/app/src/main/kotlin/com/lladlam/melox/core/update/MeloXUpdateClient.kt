package com.lladlam.melox.core.update

import android.content.Context
import com.lladlam.melox.core.network.MeloXGitHubRouting
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class MeloXRelease(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val publishedAt: String,
)

class MeloXUpdateClient(
    context: Context? = null,
    private val httpClient: OkHttpClient = MeloXHttpClient.shared,
    routing: MeloXGitHubRouting? = null,
) {
    private val routing = routing ?: context?.let { MeloXGitHubRouting(it, httpClient) }

    suspend fun latestStableRelease(forceSourceBenchmark: Boolean = false): MeloXRelease = withContext(Dispatchers.IO) {
        val router = requireNotNull(routing) { "Context is required for update requests" }
        var lastError: Throwable? = null
        for (source in router.candidates(forceSourceBenchmark)) {
            val request = Request.Builder()
                .url(router.routedUrl(source, MeloXGitHubRouting.UpdateManifestUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "MeloX-Android")
                .build()
            val result = runCatching {
                router.client(source).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
                    parseManifest(response.body.string())
                }
            }
            result.getOrNull()?.let { return@withContext it }
            lastError = result.exceptionOrNull()
        }
        throw IOException("更新检查失败：${lastError?.message ?: "所有 GitHub 源均不可用"}")
    }

    suspend fun downloadUrl(release: MeloXRelease): String? = withContext(Dispatchers.IO) {
        val original = release.apkUrl ?: return@withContext null
        val router = requireNotNull(routing) { "Context is required for update requests" }
        val source = router.candidates().firstOrNull() ?: return@withContext original
        router.routedUrl(source, original)
    }

    internal fun parseManifest(body: String): MeloXRelease {
        val value = JSONObject(body)
        val version = value.getString("version").trim()
        val pageUrl = value.getString("pageUrl").trim()
        require(version.isNotBlank() && pageUrl.startsWith("https://github.com/lladlam/MeloX-Android/releases/")) {
            "更新清单格式错误"
        }
        val apkUrl = value.optString("apkUrl").trim().takeIf(String::isNotBlank)
        require(apkUrl == null || apkUrl.startsWith("https://github.com/lladlam/MeloX-Android/releases/download/")) {
            "更新下载地址不受信任"
        }
        return MeloXRelease(
            version = version,
            name = value.optString("name").ifBlank { version },
            notes = value.optString("notes"),
            pageUrl = pageUrl,
            apkUrl = apkUrl,
            apkName = value.optString("apkName").trim().takeIf(String::isNotBlank),
            publishedAt = value.optString("publishedAt"),
        )
    }

    fun isNewer(latest: String, current: String): Boolean {
        fun parts(value: String) = value.trim()
            .removePrefix("android-")
            .removePrefix("v")
            .substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val left = parts(latest)
        val right = parts(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }
}
