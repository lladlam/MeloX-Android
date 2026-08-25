package com.lladlam.melox.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXColdStartArchitectureTest {
    @Test
    fun playbackAndOptionalIntegrationsWaitForFirstDraw() {
        val mainActivity = File("src/main/kotlin/com/lladlam/melox/MainActivity.kt").readText()
        val app = File("src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt").readText()
        val player = File("src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerUi.kt").readText()

        assertTrue(mainActivity.contains("firstDraw.await()"))
        assertTrue(mainActivity.contains("playbackConnectionEnabled = true"))
        assertTrue(mainActivity.contains("MeloXRemoteConfigRuntime.initializeAndRefresh"))
        assertTrue(player.contains("if (!connectionEnabled) return@DisposableEffect onDispose {}"))
        assertTrue(app.contains("rememberMeloXPlaybackUiState(connectionEnabled = playbackConnectionEnabled)"))
        assertFalse(app.contains("ProviderPlaybackRuntime.initialize(context)"))
        val application = File("src/main/kotlin/com/lladlam/melox/MeloXApplication.kt").readText()
        assertFalse(application.contains("MeloXRemoteConfigRuntime"))
    }

    @Test
    fun homeRecommendationJsonIsNotParsedDuringComposition() {
        val home = File("src/main/kotlin/com/lladlam/melox/ui/discovery/MeloXDiscoveryScreens.kt").readText()

        assertFalse(home.contains("mutableStateOf(LocalRecommendationStore.readRecommendations(context))"))
        assertFalse(home.contains("mutableStateOf(LocalRecommendationStore.readCandidateTracks(context))"))
        assertTrue(home.contains("withContext(Dispatchers.IO)"))
    }
}
