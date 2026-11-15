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
    /** Release tag explicitly marks a security fix (`*-security`). */
    val securityCritical: Boolean = false,
)

/** Latest commit on the default branch, for development-build update checks. */
data class MeloXDevCommit(
    val sha: String,
    val message: String,
    val author: String,
    val committedAt: String,
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

    /**
     * Downloads an incremental-update patch, verifying its declared size and
     * SHA-256 before returning it. Mirrors the full-APK download routing, and
     * gives up (returns `null`) so the caller can fall back to the full APK.
     */
    suspend fun downloadPatch(
        context: Context,
        patch: com.lladlam.melox.core.remoteconfig.MeloXRemoteUpdatePatch,
    ): java.io.File? = withContext(Dispatchers.IO) {
        val router = requireNotNull(routing) { "Context is required for patch downloads" }
        val target = java.io.File(context.cacheDir, "melox_patch_${patch.toVersion}.bin")
        var lastError: Throwable? = null
        for (source in router.candidates()) {
            val result = runCatching {
                target.delete()
                val request = Request.Builder().url(router.routedUrl(source, patch.patchUrl)).build()
                router.client(source).newBuilder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        var written = 0L
                        response.body.byteStream().use { input ->
                            target.outputStream().buffered(1 shl 16).use { output ->
                                val buffer = ByteArray(1 shl 16)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    written += read
                                    check(written <= patch.patchBytes) { "patch larger than declared" }
                                    digest.update(buffer, 0, read)
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        check(written == patch.patchBytes) { "patch size mismatch: $written" }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        check(actual.equals(patch.patchSha256, ignoreCase = true)) { "patch sha256 mismatch" }
                    }
            }
            result.onSuccess { return@withContext target }
            lastError = result.exceptionOrNull()
        }
        android.util.Log.w("MeloXUpdate", "patch download failed", lastError)
        target.delete()
        null
    }

    suspend fun downloadUrl(release: MeloXRelease): String? = withContext(Dispatchers.IO) {
        val original = release.apkUrl ?: return@withContext null
        val router = requireNotNull(routing) { "Context is required for update requests" }
        val source = router.candidates().firstOrNull() ?: return@withContext original
        router.routedUrl(source, original)
    }

    /**
     * Rolling development-build APK, routed through the same mirror selection as
     * release downloads so the file can be fetched without reaching github.com
     * directly and without a GitHub login.
     */
    suspend fun devBuildUrl(): String = withContext(Dispatchers.IO) {
        val router = requireNotNull(routing) { "Context is required for update requests" }
        val source = router.candidates().firstOrNull() ?: return@withContext DevBuildPageUrl
        router.routedUrl(source, DevBuildPageUrl)
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
            securityCritical = value.optBoolean("securityCritical", false) || isSecurityTag(version),
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
                    securityCritical = isSecurityTag(tag),
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

    /**
     * Latest commit on the default branch, for development-build update checks.
     * The commit subject is the "what changed" note shown to the user; nothing is
     * downloaded or installed from here.
     */
    suspend fun latestDevCommit(): MeloXDevCommit = withContext(Dispatchers.IO) {
        val router = requireNotNull(routing) { "Context is required for update requests" }
        var lastError: Throwable? = null
        for (source in router.candidates()) {
            val request = Request.Builder()
                .url(router.routedUrl(source, GitHubBranchUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MeloX-Android")
                .build()
            val result = runCatching {
                router.client(source).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
                    val commit = JSONObject(response.body.string()).optJSONObject("commit")
                        ?: throw IOException("${source.label} 响应缺少 commit")
                    val sha = commit.optString("sha").takeIf(String::isNotBlank)
                        ?: throw IOException("${source.label} 响应缺少 commit sha")
                    val detail = commit.optJSONObject("commit")
                    MeloXDevCommit(
                        sha = sha,
                        message = detail?.optString("message").orEmpty().substringBefore('\n').trim(),
                        author = commit.optJSONObject("author")?.optString("login").orEmpty(),
                        committedAt = detail?.optJSONObject("author")?.optString("date").orEmpty(),
                    )
                }
            }
            result.getOrNull()?.let { return@withContext it }
            lastError = result.exceptionOrNull()
        }
        throw IOException("获取最新 commit 失败：${lastError?.message ?: "所有源均不可用"}")
    }

    private fun isSecurityTag(value: String): Boolean =
        value.trim().substringAfterLast('-', "").equals("security", ignoreCase = true)

    private fun versionParts(value: String): List<Int>? {
        val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    private companion object {
        const val GitHubReleasesUrl = "https://api.github.com/repos/lladlam/MeloX-Android/releases?per_page=100"
        const val GitHubBranchUrl = "https://api.github.com/repos/lladlam/MeloX-Android/branches/main"
        const val DevBuildPageUrl =
            "https://github.com/lladlam/MeloX-Android/releases/download/dev-latest/MeloX-Android-debug.apk"
        const val ReleasePagePrefix = "https://github.com/lladlam/MeloX-Android/releases/"
        const val ReleaseDownloadPrefix = "https://github.com/lladlam/MeloX-Android/releases/download/"
        val VERSION_PATTERN = Regex(
            pattern = "^(?:android-)?v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
