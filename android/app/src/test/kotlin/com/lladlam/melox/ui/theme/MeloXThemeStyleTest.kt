package com.lladlam.melox.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version-qualified `Theme.MeloX` definitions replace, not merge with, the
 * unqualified resource. Every variant must therefore inherit `Theme.MeloX.Base`
 * and repeat any items introduced by an older qualified folder.
 */
class MeloXThemeStyleTest {
    private val baseDir = File("src/main/res")

    private fun style(name: String, folder: String): String {
        val file = File(baseDir, "$folder/styles.xml")
        assertTrue("${file.path} should exist", file.isFile)
        val text = file.readText()
        val start = text.indexOf("<style name=\"$name\"")
        assertTrue("$name should exist in $folder", start >= 0)
        val end = text.indexOf("</style>", start)
        assertTrue("$name in $folder should be closed", end > start)
        return text.substring(start, end)
    }

    @Test
    fun baseStyleKeepsSharedWindowAttributes() {
        val base = style("Theme.MeloX.Base", "values")
        for (item in listOf(
            "android:fontFamily",
            "android:windowLightStatusBar",
            "android:statusBarColor",
            "android:navigationBarColor",
            "android:windowActionModeOverlay",
        )) {
            assertTrue("base theme must define $item", base.contains("name=\"$item\""))
        }
    }

    @Test
    fun versionVariantsInheritTheBaseStyle() {
        for ((folder, items) in listOf(
            "values-v27" to listOf("android:windowLightNavigationBar"),
            "values-v29" to listOf(
                "android:windowLightNavigationBar",
                "android:enforceNavigationBarContrast",
            ),
        )) {
            val variant = style("Theme.MeloX", folder)
            assertTrue(
                "$folder must inherit Theme.MeloX.Base instead of replacing it",
                variant.contains("parent=\"Theme.MeloX.Base\""),
            )
            for (item in items) {
                assertTrue("$folder must define $item", variant.contains("name=\"$item\""))
            }
        }
    }
}
