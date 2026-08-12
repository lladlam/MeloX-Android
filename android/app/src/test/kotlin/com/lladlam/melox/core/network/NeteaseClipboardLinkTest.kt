package com.lladlam.melox.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseClipboardLinkTest {
    @Test fun parsesSongAndPlaylistLinks() {
        assertEquals(NeteaseClipboardTarget.Song(123L), NeteaseClipboardLink.parse("https://music.163.com/#/song?id=123"))
        assertEquals(NeteaseClipboardTarget.Playlist(456L), NeteaseClipboardLink.parse("分享 https://music.163.com/playlist?id=456"))
    }

    @Test fun ignoresUnrelatedClipboardText() {
        assertNull(NeteaseClipboardLink.parse("https://example.com/song?id=123"))
    }
}
