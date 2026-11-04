package com.lladlam.melox.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NeteaseFavoriteRetryTest {
    @Test
    fun retriesTransientErrorsWithBoundedBackoff() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val result = retryNeteaseFavorite(pause = { delays += it }) {
            attempts++
            if (attempts < 3) throw IOException("connection reset")
            "saved"
        }
        assertEquals("saved", result)
        assertEquals(3, attempts)
        assertEquals(listOf(800L, 1600L), delays)
    }

    @Test
    fun rateLimitRetriesButAuthenticationFailureDoesNot() = runBlocking {
        var attempts = 0
        val result = retryNeteaseFavorite(pause = {}) {
            if (++attempts == 1) throw NeteaseRequestException(429, "操作频繁")
            true
        }
        assertTrue(result)
        assertEquals(2, attempts)
        attempts = 0
        val failure = runCatching {
            retryNeteaseFavorite(pause = {}) {
                attempts++
                throw NeteaseRequestException(401, "请先登录")
            }
        }
        assertTrue(failure.isFailure)
        assertEquals(1, attempts)
    }

    @Test
    fun cancellationIsPropagatedWithoutRetry() = runBlocking {
        var attempts = 0
        val failure = runCatching {
            retryNeteaseFavorite(pause = {}) {
                attempts++
                throw CancellationException("cancelled")
            }
        }
        assertTrue(failure.exceptionOrNull() is CancellationException)
        assertEquals(1, attempts)
    }
}
