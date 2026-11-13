package com.lladlam.melox.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseApiRetryPolicyTest {
    @Test
    fun readOnlyEndpointsRemainRetryable() {
        assertFalse(isNonIdempotentNeteaseApi("/api/song/like/get"))
        assertFalse(isNonIdempotentNeteaseApi("/api/resource/comment/floor/get"))
        assertFalse(isNonIdempotentNeteaseApi("/api/v1/resource/comments/R_SO_4_347230"))
        assertFalse(isNonIdempotentNeteaseApi("/api/cloud/upload/check"))
    }

    @Test
    fun mutationsAreNotReplayed() {
        assertTrue(isNonIdempotentNeteaseApi("/api/playlist/create"))
        assertTrue(isNonIdempotentNeteaseApi("/api/song/like"))
        assertTrue(isNonIdempotentNeteaseApi("/api/msg/private/send"))
        assertTrue(isNonIdempotentNeteaseApi("/api/listen/together/invitation/accept"))
        assertTrue(isNonIdempotentNeteaseApi("/api/listen/together/command/report"))
        assertTrue(isNonIdempotentNeteaseApi("/api/sms/captcha/sent"))
        assertTrue(isNonIdempotentNeteaseApi("/api/w/login/cellphone"))
    }
}
