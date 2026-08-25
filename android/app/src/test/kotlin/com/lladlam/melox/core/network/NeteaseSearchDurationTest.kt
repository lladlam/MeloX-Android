package com.lladlam.melox.core.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NeteaseSearchDurationTest {
    @Test
    fun readsModernDtDurationNeededByAmlLMatching() {
        assertEquals(183_456L, neteaseSearchDurationMs(JSONObject("""{"dt":183456}""")))
    }

    @Test
    fun fallsBackToLegacyDurationField() {
        assertEquals(182_000L, neteaseSearchDurationMs(JSONObject("""{"duration":182000}""")))
    }
}
