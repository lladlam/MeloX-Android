package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import org.junit.Assert.*
import org.junit.Test

class PluginTrackLinkTest {
    @Test fun shareRoundTripKeepsMissingPluginAndOpaqueUnicodeIdentity() {
        val id = MusicResourceId(SourceId.parse("plugin:org.example.missing")!!, "曲目/opaque:%2f?x=a+b")
        val link = PluginTrackLink.create(id, "Title & Artist")
        val parsed = PluginTrackLink.parse(link)
        assertEquals(id, parsed.id)
        assertEquals("Title & Artist", parsed.title)
    }
    @Test fun deepLinksCannotSupplyNetworkOrLocalPlaybackAddressesOrDuplicateIdentity() {
        val id = MusicResourceId(SourceId.parse("plugin:org.example.test")!!, "opaque")
        val link = PluginTrackLink.create(id, "Title")
        for (invalid in listOf(link + "&source=netease", link.replace("plugin%3Aorg.example.test", "netease"),
            link.replace("melox://", "https://"), link + "&url=file:///data/private", link + "#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { PluginTrackLink.parse(invalid) }
        }
    }
}
