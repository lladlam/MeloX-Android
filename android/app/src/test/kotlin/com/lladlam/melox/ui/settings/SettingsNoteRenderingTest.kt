package com.lladlam.melox.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置项的说明文字（`note`）必须真正显示出来。
 *
 * 历史回归：`SettingsToggleRow` / `SettingsExternalToggleRow` 仍然接收 `note` 参数，
 * 但实现里丢掉了 `subtitle = note`，导致所有说明文字（及其四语翻译）永远不显示，
 * 而调用点看起来一切正常，新加的文案也照样「翻译了却看不见」。
 */
class SettingsNoteRenderingTest {
    private val screen = File("src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt").readText()

    private fun functionBody(name: String): String {
        val signature = screen.indexOf("private fun $name(")
        assertTrue("$name should exist", signature >= 0)
        val bodyStart = screen.indexOf("\n) {", signature).let { if (it < 0) signature else it + 4 }
        val next = screen.indexOf("\n@Composable", bodyStart).let { if (it < 0) screen.length else it }
        return screen.substring(bodyStart, next)
    }

    @Test
    fun toggleRowsRenderTheirNoteAsSubtitle() {
        for (name in listOf("SettingsToggleRow", "SettingsExternalToggleRow")) {
            val body = functionBody(name)
            assertTrue(
                "$name must pass its note to MeloXIosListRow as subtitle",
                body.contains("subtitle = note"),
            )
        }
    }

    @Test
    fun settingsNotesAreAllowedToWrapInsteadOfBeingTruncated() {
        assertTrue(
            "settings notes must not be capped at two lines",
            screen.contains("subtitleMaxLines = SettingsNoteMaxLines"),
        )
        assertTrue(
            "SettingsNoteMaxLines must allow long notes to wrap",
            screen.contains("private const val SettingsNoteMaxLines = "),
        )
    }

    @Test
    fun settingsRowsDoNotHardcodeChineseCopy() {
        // 说明文字恢复显示后，任何硬编码中文都会在英文／日文界面直接露出来。
        val hardcodedChinese = Regex("\"[^\"]*[\\u4e00-\\u9fff][^\"]*\"")
        val offenders = mutableListOf<String>()
        for (name in listOf("SettingsToggleRow", "SettingsExternalToggleRow")) {
            var index = screen.indexOf("private fun $name(")
            while (index >= 0) {
                val callStart = screen.indexOf("$name(", index)
                if (callStart < 0) break
                val callEnd = screen.indexOf("\n", callStart)
                val call = screen.substring(callStart, minOf(callEnd, screen.length))
                hardcodedChinese.findAll(call).forEach { offenders.add("${name}: ${it.value}") }
                index = callEnd
            }
        }
        assertTrue("hardcoded Chinese notes should be replaced by string resources: $offenders", offenders.isEmpty())
    }
}
