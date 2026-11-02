package com.lladlam.melox.core.network

import java.io.IOException
import kotlin.random.Random
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Resilience layer for third-party music APIs.
 *
 * QQ Music (and others) occasionally answer with a 5xx from a single front-end
 * node — most noticeably during late-night maintenance windows when traffic is
 * shifted to fewer edges. MeloX previously had no retry at all, so every
 * transient 5xx surfaced as a hard failure (and, on the playback path, as a
 * plain [IOException] the player treats as "network jitter" and waits on instead
 * of skipping). This interceptor absorbs those blips:
 *
 * - Retries on HTTP 500/502/503/504 and on transport failures (connect/timeout/reset).
 * - For QQ Music's `musicu.fcg` front-ends it rotates hosts (u -> u6) so a
 *   single down node is bypassed without any per-call logic changes. Host
 *   rotation is scoped to that path — other QQ endpoints (search at
 *   `c.y.qq.com/soso/fcgi-bin/client_search_cp`, profile at `c6.y.qq.com/...`)
 *   are host-specific and must not be re-routed across those front-ends.
 * - Business-level errors (non-5xx, JSON `code != 0`, empty body) are NOT retried.
 * - Bounded attempts with exponential backoff + jitter; safe for the read-only
 *   POST/GET shapes these providers use.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 4,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val hosts = QQ_MUSICU_HOSTS.takeIf { request.url.host in it && isMusicuFcGCall(request.url) }
        val attempts = maxOf(maxAttempts, hosts?.size ?: 1)
        var lastException: IOException? = null

        repeat(attempts) { index ->
            val host = if (hosts != null) {
                val start = hosts.indexOf(request.url.host)
                hosts[(start + index) % hosts.size]
            } else {
                request.url.host
            }
            val candidate = if (host != request.url.host) {
                request.newBuilder().url(request.url.newBuilder().host(host).build()).build()
            } else {
                request
            }

            try {
                val response = chain.proceed(candidate)
                if (response.isSuccessful || response.code !in 500..599) return response
                response.close()
                lastException = IOException("网络请求失败：HTTP ${response.code}（host=$host）")
            } catch (e: IOException) {
                lastException = IOException("网络连接失败（host=$host）：${e.message.orEmpty()}", e)
            }

            if (index < attempts - 1) {
                val backoff = (300L shl index) + Random.nextLong(0, 250)
                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        throw lastException ?: IOException("网络请求失败")
    }

    companion object {
        /**
         * Front-ends that accept a `musicu.fcg` POST. Measured 2026-09-29:
         * `u.y.qq.com` and `u6.y.qq.com` answer 200, while `c.y.qq.com` and
         * `i.y.qq.com` reject the POST with 405 "bad request", so rotating a
         * failing call onto them would only trade a retryable 5xx for a certain
         * failure. `u3.y.qq.com` does not exist.
         */
        val QQ_MUSICU_HOSTS = listOf("u.y.qq.com", "u6.y.qq.com")

        /**
         * `musicu.fcg` is the only QQ path that is served more than one way; every
         * other endpoint (`client_search_cp`, `fcg_get_profile_homepage.fcg`, ...)
         * is host-specific and must not be re-routed.
         */
        internal fun isMusicuFcGCall(url: okhttp3.HttpUrl): Boolean =
            url.pathSegments.any { it == "musicu.fcg" }
    }
}
