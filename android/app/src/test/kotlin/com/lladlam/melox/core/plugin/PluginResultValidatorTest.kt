package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginResultValidatorTest {
    private val source = SourceId.parse("plugin:example.music")!!

    @Test fun standardSearchKeepsPluginIdentity() {
        val page = PluginResultValidator.search(source, """{"page":1,"pageSize":10,"hasMore":false,"items":[{"id":"x/1","title":"Song","artists":[{"name":"Artist"}],"durationMs":3000}]}""")
        assertEquals(source, page.tracks.single().id.sourceId)
        assertEquals("x/1", page.tracks.single().id.value)
        assertEquals(3000L, page.tracks.single().durationMs)
    }

    @Test fun platformSpecificOrUnsafeShapesAreNotGuessed() {
        assertThrows(Exception::class.java) {
            PluginResultValidator.search(source, """{"page":1,"pageSize":10,"hasMore":false,"items":[{"songname":"Song"}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) { PluginResultValidator.validateUrlSyntax("file:///etc/passwd") }
        assertThrows(IllegalArgumentException::class.java) { PluginResultValidator.validateUrlSyntax("https://user:pass@example.com/") }
    }
}
