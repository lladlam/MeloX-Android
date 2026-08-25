package com.lladlam.melox.core.remoteconfig

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigSchedulingTest {
    @Test
    fun automaticRefreshIntervalIsTwoHours() {
        assertEquals(2L * 60L * 60L * 1_000L, MeloXRemoteConfigRefreshIntervalMs)
    }

    @Test
    fun foregroundOpenForcesCheckAndPeriodicPollingRequiresConsent() {
        val main = File("src/main/kotlin/com/lladlam/melox/MainActivity.kt").readText()
        val runtime = File("src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigRuntime.kt").readText()

        assertTrue(main.contains("MeloXAppVisibility.foregroundSessionId"))
        assertTrue(main.contains("force = true"))
        assertTrue(runtime.contains("while (isActive && MeloXRemoteConfigConsent.enabled(appContext))"))
        assertTrue(runtime.contains("MeloXAppVisibility.isForeground"))
    }

    @Test
    fun policyDisclosesUpdatedFrequency() {
        val policy = File("src/main/assets/legal/cloud-control-privacy-zh-CN.md").readText()

        assertTrue(policy.contains("版本：1.2"))
        assertTrue(policy.contains("每次应用从后台进入前台时都会检查一次"))
        assertTrue(policy.contains("每两小时检查一次"))
        assertFalse(policy.contains("每 24 小时"))
        assertEquals("1.2-2026-08-25", MeloXRemoteConfigConsent.PolicyVersion)
    }
}
