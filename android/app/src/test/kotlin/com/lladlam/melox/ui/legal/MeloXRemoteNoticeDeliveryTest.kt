package com.lladlam.melox.ui.legal

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteNoticeDeliveryTest {
    @Test
    fun appShowsOnlyConsentedVerifiedAnnouncementsAndRecordsDisplay() {
        val source = File("src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt").readText()

        assertTrue(source.contains("MeloXRemoteConfigConsent.enabled(context)"))
        assertTrue(source.contains("remoteConfigStatus.source == MeloXRemoteConfigSource.VerifiedRemote"))
        assertTrue(source.contains("MeloXRemoteNoticeStore.shouldShow(context, it)"))
        assertTrue(source.contains("MeloXRemoteNoticeStore.markShown(context, notice)"))
        assertTrue(source.contains("MeloXRemoteNoticeDialog("))
    }
}
