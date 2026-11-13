package com.lladlam.melox.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置项说明文字（`note`）已按产品要求隐藏。
 *
 * 反向回归：开关行仍接收 `note` 参数，但实现不得再把它渲染成副标题，
 * 否则设置页会重新出现用户明确要求删除的灰色备注。
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
    fun toggleRowsDoNotRenderNotes() {
        for (name in listOf("SettingsToggleRow", "SettingsExternalToggleRow")) {
            val body = functionBody(name)
            assertFalse(
                "$name must not pass note to MeloXIosListRow as subtitle",
                body.contains("subtitle = note"),
            )
        }
    }

    @Test
    fun settingsNoteLineLimitHelperIsGone() {
        assertFalse(
            "settings notes are hidden, so the line-limit helper must not remain",
            screen.contains("SettingsNoteMaxLines"),
        )
    }

    @Test
    fun settingsRowsDoNotHardcodeChineseCopy() {
        // 仍保留的参数区域不得出现硬编码中文，避免未来恢复备注时漏翻。
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
