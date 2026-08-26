package com.lladlam.melox.core.provider.kuwo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KuwoJsonNormalizerTest {
    @Test
    fun singleQuotedJsonBecomesParseable() {
        val raw = "{'TOTAL':'3600','abslist':[{'NAME':'花海','ARTIST':'周杰伦','DURATION':'210'}]}"
        val normalized = KuwoJsonNormalizer.normalize(raw)
        val json = JSONObject(normalized)
        assertEquals("3600", json.getString("TOTAL"))
        val list = json.getJSONArray("abslist")
        assertEquals(1, list.length())
        assertEquals("花海", list.getJSONObject(0).getString("NAME"))
    }

    @Test
    fun htmlEntitiesAndSeparatorsArePreserved() {
        val raw = "{'ARTIST':'Jay&nbsp;Chou###Mayday','SONGNAME':'花海&nbsp;(DJ&nbsp;阿若版)'}"
        val normalized = KuwoJsonNormalizer.normalize(raw)
        val json = JSONObject(normalized)
        assertEquals("Jay&nbsp;Chou###Mayday", json.getString("ARTIST"))
        assertEquals("花海&nbsp;(DJ&nbsp;阿若版)", json.getString("SONGNAME"))
    }
}
