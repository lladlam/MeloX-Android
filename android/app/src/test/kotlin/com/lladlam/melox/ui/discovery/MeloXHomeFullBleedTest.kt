package com.lladlam.melox.ui.discovery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXHomeFullBleedTest {
    @Test
    fun homeViewportIsFullWidthAndInsetsIndividualBlocks() {
        val source = File("src/main/kotlin/com/lladlam/melox/ui/discovery/MeloXDiscoveryScreens.kt").readText()

        assertFalse(source.contains(".widthIn(max = window.maxContentWidth)"))
        assertTrue(source.contains("contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp)"))
        assertTrue(source.contains("contentPadding = PaddingValues(horizontal = 20.dp)"))
        assertTrue(source.contains("SectionTitle(block.title, block.trailing, Modifier.padding(horizontal = 20.dp))"))
    }
}
