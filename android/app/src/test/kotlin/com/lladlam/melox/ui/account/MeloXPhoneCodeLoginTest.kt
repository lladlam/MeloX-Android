package com.lladlam.melox.ui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXPhoneCodeLoginTest {
    @Test
    fun normalizesPhoneWithoutAcceptingLetters() {
        assertEquals("138 0013-8000", normalizePhoneInput("138 0013-8000abc"))
        assertEquals("13800138000", normalizedPhone("138 0013-8000"))
        assertTrue(isValidPhone("138 0013 8000"))
        assertFalse(isValidPhone("1234"))
    }

    @Test
    fun verificationCodeIsNumericAndBounded() {
        assertEquals("12345678", normalizeVerificationCode("12a34 567890"))
        assertTrue(isValidVerificationCode("123456"))
        assertFalse(isValidVerificationCode("123"))
        assertEquals(60, PHONE_CODE_RESEND_SECONDS)
    }
}
