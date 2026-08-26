package com.lladlam.melox.core.update

import android.content.Context
import com.lladlam.melox.core.network.MeloXGitHubRouting
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
        val sources = router.candidates(forceSourceBenchmark)
        for (source in sources) {
            val request = Request.Builder()
                .url(router.routedUrl(source, GitHubReleasesUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MeloX-Android")
                .build()
            val result = runCatching {
                router.client(source).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
                    parseReleases(response.body.string())
                        ?: throw IOException("${source.label} 没有可用的 Android Release")
                }
            }
            result.getOrNull()?.let { return@withContext it }
            lastError = result.exceptionOrNull()
        }
        for (source in sources) {
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

    internal fun parseReleases(body: String): MeloXRelease? {
        val releases = JSONArray(body)
        return (0 until releases.length())
            .asSequence()
            .mapNotNull(releases::optJSONObject)
            .filterNot { it.optBoolean("draft", false) }
            .mapNotNull { value ->
                val tag = value.optString("tag_name").trim()
                val parts = versionParts(tag) ?: return@mapNotNull null
                val pageUrl = value.optString("html_url").trim()
                if (!pageUrl.startsWith(ReleasePagePrefix)) return@mapNotNull null
                val asset = value.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).asSequence()
                        .mapNotNull(assets::optJSONObject)
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                }
                val apkUrl = asset?.optString("browser_download_url")?.trim()?.takeIf {
                    it.startsWith(ReleaseDownloadPrefix)
                }
                parts to MeloXRelease(
                    version = tag,
                    name = value.optString("name").ifBlank { tag },
                    notes = value.optString("body"),
                    pageUrl = pageUrl,
                    apkUrl = apkUrl,
                    apkName = asset?.optString("name")?.trim()?.takeIf(String::isNotBlank),
                    publishedAt = value.optString("published_at"),
                )
            }
            .maxWithOrNull(compareBy<Pair<List<Int>, MeloXRelease>>(
                { it.first[0] },
                { it.first[1] },
                { it.first[2] },
                { it.second.publishedAt },
            ))
            ?.second
    }

    fun isNewer(latest: String, current: String): Boolean {
        val left = versionParts(latest) ?: return false
        val right = versionParts(current) ?: return false
        for (index in left.indices) {
            val difference = left[index].compareTo(right[index])
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun versionParts(value: String): List<Int>? {
        val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    private companion object {
        const val GitHubReleasesUrl = "https://api.github.com/repos/lladlam/MeloX-Android/releases?per_page=100"
        const val ReleasePagePrefix = "https://github.com/lladlam/MeloX-Android/releases/"
        const val ReleaseDownloadPrefix = "https://github.com/lladlam/MeloX-Android/releases/download/"
        val VERSION_PATTERN = Regex(
            pattern = "^(?:android-)?v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
