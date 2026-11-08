package com.lladlam.melox.ui.glass

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标尺寸的**契约测试**（2026-10-25）。
 *
 * ### 为什么需要这些测试
 * 图标尺寸这条链上有三处「隐式依赖」，任何一处变掉都**不会报错**，只会让界面静默变样：
 *   1. 旋钮常量被手滑改掉 —— diff 里只是两个数字，code review 极易漏；
 *   2. 换了字体版本后墨迹比例（ink_h/em）变了，但 [MeloXGlyphInkHeight] 没重测；
 *   3. 有人把 `ss16`（`gid → gid+1`，墨迹统一 ×1.27）那类字体特性加回来 ——
 *      2026-10 图标「异常巨大」和随后的「偏小一圈」都是这条链断掉造成的。
 *
 * 这里把「当前约定的视觉尺寸」写成期望值：改动时测试变红，强制复核一次，
 * 而不是靠下次肉眼发现。
 */
class MeloXIconMetricsTest {

    /**
     * 复刻 `SfGlyphIcon` 的真实链路：墨迹(dp) = 归一后字号 × 旋钮 × ink_h/em。
     *
     * ⚠ 刻意不引入 Paint / Canvas —— 这条公式就是 `textSize` 那行的全部算术，
     *   在 JVM 上算得出来，不需要 Robolectric。
     */
    private fun inkDp(symbol: String, iconSize: Float = 24f): Float {
        val ink = MeloXGlyphInkHeight.getValue(symbol)
        val fitted = meloXInkFittedIconSize(iconSize.sp, symbol).value
        return fitted * MeloXSfGlyphScale * ink
    }

    @Test
    fun scalesDefaultTo065Look() {
        // 0.6.5 = iconSize × ink_h/em，没有任何全局缩放，也没有字形间归一。
        assertEquals("SF 字形旋钮必须为 1f", 1f, MeloXSfGlyphScale, 0f)
        assertEquals("手绘/矢量旋钮必须为 1f", 1f, MeloXVectorIconScale, 0f)
        assertNull(
            "归一必须默认关闭：打开会把 play.fill 放大 18%、forward.fill 放大 28%",
            MeloXInkFitTarget,
        )
    }

    @Test
    fun twentyFourSpInkHeightsMatch065() {
        // 真机实测基准（1440×3168 / density 4.0，按墨迹像素量）：
        //   底栏 Library 23.5dp、播放键 39.8dp（48sp）。
        assertEquals("gear ink_h=1.086", 26.06f, inkDp("gear"), 0.02f)
        assertEquals("music.note.list ink_h=0.984", 23.62f, inkDp("music.note.list"), 0.02f)
        assertEquals("play.fill ink_h=0.827", 19.85f, inkDp("play.fill"), 0.02f)
        assertEquals("safari ink_h=0.996", 23.90f, inkDp("safari"), 0.02f)
    }

    @Test
    fun playButtonAt48SpMatchesMeasurement() {
        // 播放页主键 48sp ⇒ 39.7dp，实测 39.8dp。
        assertEquals(39.70f, inkDp("play.fill", 48f), 0.05f)
    }

    @Test
    fun inkRatiosStayInSaneRange() {
        // SF 字体的真实跨度是 0.195（ellipsis）~ 1.200（rectangle.landscape.rotate）。
        // 超出这个范围说明表里混进了别的度量口径（例如按 viewport 而非 em 计算）。
        MeloXGlyphInkHeight.forEach { (name, ink) ->
            assertTrue("$name 的 ink_h/em=$ink 超出可信区间 [0.1, 1.5]", ink in 0.1f..1.5f)
        }
    }

    @Test
    fun inkFitIsIdentityWhenTargetIsNull() {
        // 关闭归一后，无论字形多偏，字号都必须原样返回 —— 这正是「保持 0.6.5 尺寸」的保证。
        MeloXGlyphInkHeight.keys.forEach { name ->
            assertEquals(
                "$name 在 MeloXInkFitTarget = null 时必须原样返回 iconSize",
                24f,
                meloXInkFittedIconSize(24.sp, name).value,
                0f,
            )
        }
    }

    @Test
    fun inkTableHasNoDegenerateEntries() {
        assertTrue("墨迹表不应为空（空表 = 归一静默失效）", MeloXGlyphInkHeight.isNotEmpty())
        MeloXGlyphInkHeight.forEach { (name, ink) ->
            assertTrue("$name 的 ink_h/em 必须为正，实际 $ink", ink > 0f)
        }
    }
}
