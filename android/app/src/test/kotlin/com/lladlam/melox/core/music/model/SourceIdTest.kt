package com.lladlam.melox.core.music.model

import org.junit.Assert.*
import org.junit.Test

class SourceIdTest {
    @Test fun preservesBuiltInsAndDoesNotAttributeUnknownSourcesToNetease() {
        MusicSource.entries.forEach {
            assertEquals(it.storageValue, SourceId.fromBuiltIn(it).value)
            assertEquals(it, SourceId.parse(it.storageValue)?.builtIn)
        }
        assertNull(SourceId.parse("plugin:example.catalog")?.builtIn)
        assertNull(SourceId.parse("future_provider")?.builtIn)
        assertNotEquals(SourceId.parse("plugin:example.catalog"), SourceId.parse("plugin:other.catalog"))
        listOf("plugin:Example.catalog", "plugin:example", "plugin:a..b", "plugin:a.b/evil", "").forEach {
            assertNull(it, SourceId.parse(it))
        }
    }

    @Test fun resourceReferencesAreCanonicalAndUnambiguous() {
        val source = SourceId.parse("plugin:example.catalog")!!
        listOf("同一首歌/100%:a", "%2F", "a/b", "a:b", "😀").forEach { resource ->
            assertEquals(source to resource, SourceResourceUri.decode(SourceResourceUri.encode(source, resource)))
        }
        val uri = SourceResourceUri.encode(source, "a/b")
        assertNull(SourceResourceUri.decode(uri.replace("%2F", "%2f")))
        assertNull(SourceResourceUri.decode(uri.replace("a%2Fb", "a/b")))
        assertNull(SourceResourceUri.decode(uri.replace("a%2Fb", "%61%2Fb")))
        assertNull(SourceResourceUri.decode(uri.replace("a%2Fb", "%C0%AF")))
        assertNull(SourceResourceUri.decode(uri.replace("a%2Fb", "%")))
        assertNull(SourceResourceUri.decode(uri + "?query=1"))
    }

    @Test fun resourceReferenceLimitsAreEnforced() {
        val source = SourceId.parse("plugin:example.catalog")!!
        assertEquals(source to "a".repeat(4096), SourceResourceUri.decode(SourceResourceUri.encode(source, "a".repeat(4096))))
        assertThrows(IllegalArgumentException::class.java) { SourceResourceUri.encode(source, "a".repeat(4097)) }
        assertThrows(IllegalArgumentException::class.java) { SourceResourceUri.encode(source, "\uD800") }
        assertNull(SourceResourceUri.decode("melox://source/v1/plugin%3Aexample.catalog/" + "a".repeat(4097)))
    }
}
