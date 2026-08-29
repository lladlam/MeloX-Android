package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LxUserSourceStoreTest {
    @Test
    fun acceptsObfuscatedV5SourceWithEscapedRuntimeNames() {
        val script = """
            /*! @name 独家音源 @version 5 */
            globalThis['SERVER_SCRIPT_CONFIG'] = {"apiUrl":"https://example.com"};
            globalThis['\\u006c\\u0078'] = { musicUrl: true };
        """.trimIndent()

        assertTrue(LxUserSourceStore.looksLikeLxMusicSource(script))
    }

    @Test
    fun rejectsUnrelatedJavascript() {
        assertFalse(LxUserSourceStore.looksLikeLxMusicSource("console.log('hello')"))
    }
}
