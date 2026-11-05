package com.lladlam.melox.core.download

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderDownloadIndexCodecTest {
    @Test fun futureRowsAndFieldsSurviveUpdatingAnotherSourceWithSameResourceId() {
        val old = JSONArray("""[{"source":"plugin:org.example.one","id":"same","title":"Old","future":{"keep":true}},
            {"source":"future:unsupported","id":"same","opaque":[1,2]},"opaque-row",
            {"source":"plugin:org.example.two","id":"same","title":"Other"}]""")
        val update = JSONArray().put(JSONObject("""{"source":"plugin:org.example.one","id":"same","title":"New"}"""))
        val merged = ProviderDownloadIndexCodec.merge(old, update, emptySet())
        assertEquals(4, merged.length())
        assertEquals("New", merged.getJSONObject(0).getString("title"))
        assertTrue(merged.getJSONObject(0).getJSONObject("future").getBoolean("keep"))
        assertEquals(old.getJSONObject(1).toString(), merged.getJSONObject(1).toString())
        assertEquals("opaque-row", merged.getString(2))
        assertEquals("Other", merged.getJSONObject(3).getString("title"))
    }

    @Test fun onlyExplicitRemovalDropsAnUnavailableSavedRecord() {
        val old = JSONArray("""[{"source":"plugin:org.example.one","id":"a","fileName":"missing.audio"},
            {"source":"plugin:org.example.two","id":"a","future":true}]""")
        assertEquals(2, ProviderDownloadIndexCodec.merge(old, JSONArray(), emptySet()).length())
        val merged = ProviderDownloadIndexCodec.merge(old, JSONArray(), setOf("plugin:org.example.one:a"))
        assertEquals(1, merged.length())
        assertEquals("plugin:org.example.two", merged.getJSONObject(0).getString("source"))
    }
}
