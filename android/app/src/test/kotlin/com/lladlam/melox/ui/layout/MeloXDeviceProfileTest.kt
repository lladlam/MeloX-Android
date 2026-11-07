package com.lladlam.melox.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三档识别矩阵守卫（2026-11-07 加固）。
 *
 * 用户口径：「手机、平板、车机识别加固，**不准串 也不准漏**」。本测试把这两个词落成
 * 两条可执行断言：
 *  · 不串 = 手机横屏（含 20:9 / 21:9 / 三折叠展开）任何尺寸都**不得**判成 CarHeadUnit，
 *    否则会套上只给超宽屏准备的左封面 + 右歌词布局；
 *  · 不漏 = 真车机几何（1920×720 / 1280×400 / K20P 模拟 1600×640）都必须判成
 *    CarHeadUnit，否则矮屏会被 heightClass=Compact 踢回手机单栏。
 *
 * ⚠ 已知取舍（写在这里免得日后又被当 bug 修）：**无 automotive 信号**的低分辨率车机
 *   （1920×720@320dpi = 960×360dp）在几何上与 21:9 手机横屏不可区分，宁可漏也不串；
 *   真车机由 `UI_MODE_TYPE_CAR` / `FEATURE_AUTOMOTIVE` 兜住（见 [MeloXDeviceProfile]）。
 */
class MeloXDeviceProfileTest {

    // ---------------------------------------------------------------- 不串：手机

    @Test
    fun phonePortraitStaysPhone() {
        assertProfile(MeloXDeviceProfile.Phone, 411, 891)
        assertProfile(MeloXDeviceProfile.Phone, 360, 800)
    }

    @Test
    fun phoneLandscapeStaysPhoneIncludingUltraTallAspect() {
        // 16:9 / 19.5:9 / 20:9 —— 宽高比 1.78 / 2.17 / 2.22，全部低于 2.2 或宽度不足。
        assertProfile(MeloXDeviceProfile.Phone, 800, 360)
        assertProfile(MeloXDeviceProfile.Phone, 873, 393)
        // ★ 21:9 Xperia 横屏：ratio 2.33 —— 已经越过 2.2 的比值闸门，**只能靠 1000dp
        //   宽度门槛挡住**。这条断言就是那 1000dp 的存在理由，回归时会立刻炸出来。
        assertProfile(MeloXDeviceProfile.Phone, 916, 393)
    }

    @Test
    fun foldableUnfoldedLandscapeStaysTabletNotCar() {
        // 三折叠展开横屏：宽 1061dp 已过 1000dp 门槛，但 ratio 只有 1.43 ⇒ 必须是平板。
        assertProfile(MeloXDeviceProfile.Tablet, 1061, 744)
    }

    @Test
    fun desktopNarrowStripStaysPhoneOrTabletNeverCar() {
        // 桌面自由窗口拉出的矮横条：高度 200dp 远低于车机的 380dp 下限。
        assertProfile(MeloXDeviceProfile.Phone, 1600, 200)
        // 宽度过门槛、高度也够，但比例只有 1.78 ⇒ 平板横屏，不是车机。
        assertProfile(MeloXDeviceProfile.Tablet, 1280, 720)
    }

    // ---------------------------------------------------------------- 不漏：车机

    @Test
    fun automotiveSignalWinsOverGeometry() {
        // ROM 权威信号优先：比例不极端的真车机（1024×600 / 1280×720）也不能漏。
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1024, 600, automotive = true)
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1280, 720, automotive = true)
        // 竖屏车机不进车机档（横屏超宽布局对它没有意义），交给平板双栏。
        assertProfile(MeloXDeviceProfile.Tablet, 720, 1280, automotive = true)
    }

    @Test
    fun ultrawideCarGeometryWithoutSignalIsRecognized() {
        // K20P 模拟车机 2400×960@240dpi ⇒ 1600×640dp
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1600, 640)
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1920, 720)
        // ★ 2026-11-07 高度门槛 420 → 380：低矮中控（1280×400 / 1024×390）不再被漏判。
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1280, 400)
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1024, 390)
    }

    @Test
    fun carThresholdsAreExactAtBoundaries() {
        // 宽度门槛：999 判手机，1000 起判车机（高度 454 ⇒ ratio 2.202 过 2.2）。
        assertProfile(MeloXDeviceProfile.Phone, 999, 454)
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1000, 454)
        // 高度门槛：379 落空，380 生效（宽度给足 ⇒ ratio 3.29，只看高度闸）。
        assertProfile(MeloXDeviceProfile.Phone, 1250, 379)
        assertProfile(MeloXDeviceProfile.CarHeadUnit, 1250, 380)
    }

    @Test
    fun degenerateSizesFallBackToPhone() {
        assertProfile(MeloXDeviceProfile.Phone, 0, 0)
        assertProfile(MeloXDeviceProfile.Phone, -1, 800)
        assertProfile(MeloXDeviceProfile.Phone, 800, 0)
    }

    // ------------------------------------------------- 车机档 ≠ 超宽屏（第二个维度）

    @Test
    fun ultrawideGeometryIsIndependentOfDeviceProfile() {
        // 几何超宽为真：车机 2.5:1、21:9 手机、K20P 模拟屏。
        assertTrue(meloXIsUltrawide(2400, 960))
        assertTrue(meloXIsUltrawide(1600, 640))
        assertTrue(meloXIsUltrawide(916, 393))
        // 几何超宽为假：普通车机比例 1.7:1、桌面 16:10、竖屏。
        assertFalse(meloXIsUltrawide(1024, 600))
        assertFalse(meloXIsUltrawide(1280, 720))
        assertFalse(meloXIsUltrawide(600, 1024))
        assertFalse(meloXIsUltrawide(0, 640))
    }

    @Test
    fun automotiveCarWithNormalAspectMustNotTakeUltrawideLayout() {
        // 这是「串」的第二个入口：设备档是车机，但比例只有 1.7:1。
        // `ultrawidePlayer = supportsPlayerTwoPane && CarHeadUnit && ultrawideGeometry`
        // ⇒ 前者为 true、后者为 false，最终必须走**平板双栏**而不是超宽布局。
        val profile = meloXClassifyDevice(1024, 600, automotive = true)
        assertEquals(MeloXDeviceProfile.CarHeadUnit, profile)
        assertFalse(meloXIsUltrawide(1024, 600))
    }

    private fun assertProfile(
        expected: MeloXDeviceProfile,
        widthDp: Int,
        heightDp: Int,
        automotive: Boolean = false,
    ) {
        assertEquals(
            "classify(${widthDp}x$heightDp, automotive=$automotive)",
            expected,
            meloXClassifyDevice(widthDp, heightDp, automotive),
        )
    }
}
