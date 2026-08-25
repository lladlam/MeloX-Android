package com.lladlam.melox.core.remoteconfig

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigThreadingTest {
    @Test
    fun everyRefreshCallerIsForcedOntoIoDispatcher() {
        val source = File(
            "src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigClient.kt",
        ).readText()

        assertTrue(source.contains("withContext(Dispatchers.IO) { refreshOnIo(versionCode, force) }"))
    }
}
