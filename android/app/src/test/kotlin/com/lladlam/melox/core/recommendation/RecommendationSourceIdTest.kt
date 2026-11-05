package com.lladlam.melox.core.recommendation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecommendationSourceIdTest {
    @Test fun legacyCandidateKeepsMissingPluginInsteadOfFallingBackToNetease() {
        val raw = JSONObject("""{"source":"plugin:org.example.removed","id":"123","title":"Title","artist":"Artist"}""")
        val track = requireNotNull(LocalRecommendationStore.decodeCandidate(raw))
        assertEquals("plugin:org.example.removed", track.id.sourceId.value)
        assertNull(track.id.sourceId.builtIn)
    }
    @Test fun completeCandidateRejectsContradictoryIdentity() {
        val raw = JSONObject("""{"source":"plugin:org.example.first","id":"123","track":{"source":"netease","id":"123","title":"Wrong"}}""")
        assertNull(LocalRecommendationStore.decodeCandidate(raw))
    }
}
