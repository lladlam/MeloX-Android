package com.lladlam.melox.core.plugin

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginArchiveReaderTest {
    private val manifest = """{"id":"example.music","name":"Example","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":["search","playback"],"networkDomains":["example.com"]}"""

    @Test fun readsManifestAndEntryWithoutExtracting() {
        val plugin = PluginArchiveReader.read(archive("manifest.json" to manifest, "index.js" to "globalThis.plugin = {}"))
        assertEquals("plugin:example.music", plugin.manifest.sourceId.value)
        assertEquals(setOf(PluginCapability.Search, PluginCapability.Playback), plugin.manifest.capabilities)
        assertEquals("globalThis.plugin = {}", plugin.script)
    }

    @Test fun rejectsTraversalUnsupportedVersionAndMissingEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginArchiveReader.read(archive("../manifest.json" to manifest, "index.js" to "x"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginArchiveReader.read(archive("manifest.json" to manifest.replace("\"apiVersion\":1", "\"apiVersion\":2"), "index.js" to "x"))
        }
        assertThrows(IllegalStateException::class.java) {
            PluginArchiveReader.read(archive("manifest.json" to manifest))
        }
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
