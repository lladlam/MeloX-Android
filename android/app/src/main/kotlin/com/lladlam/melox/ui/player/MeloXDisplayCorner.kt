package com.lladlam.melox.ui.player

import android.content.res.Resources
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import com.lladlam.melox.ui.layout.MeloXDeviceProfile
import com.lladlam.melox.ui.layout.rememberMeloXWindowInfo
import kotlinx.coroutines.delay

/** 轮询上限：组合期首读常为 null（insets 未派发），最多 20 × 50ms = 1s。 */
private const val MELOX_CORNER_POLL_ATTEMPTS = 20
private const val MELOX_CORNER_POLL_DELAY_MS = 50L

/**
 * 框架 dimen 候选（AOSP `DisplayUtils.getCornerRadiusTop()` 同源）：
 * OEM 三者里定义哪个都有可能，取最大正值。`rounded_corner_radius` 是 AOSP 默认项，
 * top/bottom 是上下圆角不同的机型分别定义的项。
 */
private val FRAMEWORK_CORNER_DIMENS = listOf(
    "rounded_corner_radius_top" to "dimen:top",
    "rounded_corner_radius_bottom" to "dimen:bottom",
    "rounded_corner_radius" to "dimen:default",
)

/**
 * 系统屏幕圆角半径（px）+ 来源标签。**多源回退**，全部落空返回 null。
 *
 * 源 1（API 31+）`WindowInsets.getRoundedCorner`：官方入口。ROM 把圆角上报进窗口 insets
 *   时最权威（Pixel / 一加等 AOSP 血统）。取四角最大值（个别机型上下圆角不同）。
 * 源 2（所有 API）框架 dimen `android:dimen/rounded_corner_radius_*`：
 *   `Resources.getSystem().getIdentifier(name, "dimen", "android")` —— AOSP `DisplayUtils`
 *   与小米 HyperOS 官方适配文档同款取法。大量 OEM（含**不在 WindowInsets 上报圆角**的
 *   ROM）都靠它暴露屏幕圆角；SystemUI 画圆角遮罩读的也是这批值（framework-res + RRO）。
 *   多显示器机型（`config_roundedCornerRadiusArray` 非空）走数组成员，取最大项兜底。
 *
 * ⚠ 2026-10-16 用户报障「根本没有正确获取圆角，用的都是硬编码」的根因：旧实现**只有源 1**，
 *   而本机（OriginOS）不把圆角填进 App 窗口 insets ⇒ 永远读不到 ⇒ 永远回落 46dp 硬编码。
 *   补上源 2 后本机可经 dimen 拿到真值；Pixel 等走源 1。两者皆失才落硬编码兜底。
 */
internal fun meloXSystemCornerRadiusPx(view: View): Pair<Float, String>? {
    // 屏幕短边一半（px）作为物理上限：个别 ROM（如红米 K20P 转屏过渡态）会在
    // `WindowInsets` 里瞬时回报一个异常大的圆角值，若不拦截会被当作真值采用，
    // 导致圆角「突然变大再恢复」。
    val dm = view.resources.displayMetrics
    val maxValidPx = minOf(dm.widthPixels, dm.heightPixels).toFloat() * 0.5f
    // 源 1：WindowInsets（API 31+ 官方入口）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val fromInsets = meloXDisplayCornerRadiusPxS(view)
        // ★ 加物理边界校验：超出短边一半的瞬时异常值直接丢弃，退回源 2（框架 dimen 恒定）。
        //   旧实现 `fromInsets > 0f` 就采用 ⇒ 转屏 insets 瞬时大值被当场当成真值，根因在此。
        if (fromInsets != null && fromInsets > 0.5f && fromInsets <= maxValidPx) {
            return fromInsets to "insets"
        }
    }
    val dimen = frameworkCornerRadiusPx()
    return dimen
}

@RequiresApi(Build.VERSION_CODES.S)
private fun meloXDisplayCornerRadiusPxS(view: View): Float? {
    val insets = ViewCompat.getRootWindowInsets(view) ?: return null
    val radii = listOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT,
    ).mapNotNull { insets.getRoundedCorner(it)?.radius?.takeIf { radius -> radius > 0 } }
    return radii.maxOrNull()?.toFloat()
}

/**
 * 框架 dimen 取屏幕圆角（px）。`Resources.getSystem()` 反映 framework-res 及其 RRO 覆盖，
 * 即 SystemUI 画圆角遮罩时读的同一批值。取 dimen 三者 + `config_roundedCornerRadiusArray`
 * 的最大正值；一个都没定义返回 null。
 */
private fun frameworkCornerRadiusPx(): Pair<Float, String>? {
    val res = Resources.getSystem()
    // 与源 1 同一道物理边界：dimen / 数组也可能被 OEM 配错 —— 多显示器机型
    // （`config_roundedCornerRadiusArray` 非空）取最大项时，外接副屏 / 电视的大圆角
    // 会盖过内屏；个别 RRO 也会把别的量填进 dimen。超边界的候选直接丢弃，
    // 全部越界则返回 null 落硬编码兜底。
    val dm = res.displayMetrics
    val maxValidPx = minOf(dm.widthPixels, dm.heightPixels).toFloat() * 0.5f
    var bestPx = 0
    var bestLabel = ""
    for ((name, label) in FRAMEWORK_CORNER_DIMENS) {
        val id = runCatching { res.getIdentifier(name, "dimen", "android") }.getOrDefault(0)
        if (id == 0) continue
        val px = runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
        if (px > bestPx && px <= maxValidPx) {
            bestPx = px
            bestLabel = label
        }
    }
    // 多显示器机型：`config_roundedCornerRadiusArray` 按 `config_displayUniqueIdArray` 索引
    // （AOSP `RoundedCorners.loadRoundedCornerRadii` 同源）。拿不到 display unique id 时
    // 取最大项 —— 内屏前台场景的保守近似（角半径跨显示器不会差出量级）。
    val arrayId = runCatching {
        res.getIdentifier("config_roundedCornerRadiusArray", "array", "android")
    }.getOrDefault(0)
    if (arrayId != 0) {
        val maxPx = runCatching { res.getIntArray(arrayId).maxOrNull() ?: 0 }.getOrDefault(0)
        if (maxPx > bestPx && maxPx <= maxValidPx) {
            bestPx = maxPx
            bestLabel = "dimen:array"
        }
    }
    return if (bestPx > 0) bestPx.toFloat() to bestLabel else null
}

/**
 * 取系统屏幕圆角的结果，**运行时原值 + 实际采用值 + 来源**都暴露出来，
 * 供 debug 包在屏幕上打读数用（用户口径：「再次确认获取系统圆角」）。
 *
 * @param runtimePx 运行时原始读数（null = 没读到 → 用兜底）
 * @param usedPx    最终渲染采用的值（px）
 * @param source    来源标签：`insets` / `dimen:top|bottom|default|array` / `fallback` / `tablet`
 */
internal data class MeloXDisplayCornerInfo(
    val runtimePx: Float?,
    val usedPx: Float,
    val source: String = "fallback",
)

/**
 * 取系统屏幕圆角（px），**轮询到拿到为止**。
 *
 * ⚠ 组合期读一次多半拿到 null（insets 还没派发）⇒ 会一直停在兜底值上，等于「根本没取到」
 *   （用户 2026-09-28 真机报障「你现在系统圆角都获取不到」就是这么来的）。
 *   这里最多试 [MELOX_CORNER_POLL_ATTEMPTS] 次（每次间隔 [MELOX_CORNER_POLL_DELAY_MS]），
 *   任一来源拿到**落在物理合理区间**的值才采用；超时/不合理则用
 *   [MeloXPlayerRecedeCompatFallbackDp]（46dp，与 iQOO 15 的系统真值等值）。
 *   **平板 / 车机档（[MeloXDeviceProfile]）不走这套手机比例判据**，
 *   直接采用 [MeloXTabletDisplayCornerDp]，理由见该常量。
 *
 * ⚠ 合理性判据只做**物理边界**（0 < r ≤ 屏幕短边一半）。旧实现用「屏宽 8%~20%」的手机专属
 *   比例带，会把合法真值误杀（Pixel ≈7.6%、异形/平板屏更低；显示大小缩放也会改屏宽）⇒
 *   又掉回硬编码，是同类事故源，2026-10-16 一并拆除。
 *
 * 播放器页面（`MeloXApp` 的下沉背板）和外壳卡片（`MeloXIOSNowPlayingSharedHost`）
 * **必须用同一个值** —— 否则同一屏里两个圆角差一点点，肉眼就是「对不上」。
 */
@Composable
internal fun rememberMeloXDisplayCornerInfo(): MeloXDisplayCornerInfo {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val window = rememberMeloXWindowInfo()
    // 平板档（双栏大屏）不吃手机那套比例判据，见 [MeloXTabletDisplayCornerDp]。
    // 判据用**设备档**而不是 `supportsPlayerTwoPane`：后者含 `splitScreenEnabled`
    // （用户开关），关掉分屏时同一台平板的圆角会从 18dp 突跳回手机兜底 46dp。
    // 屏幕圆角是物理属性，只该由屏幕尺寸 + ROM 信号决定。
    val tablet = window.deviceProfile != MeloXDeviceProfile.Phone
    val fallbackPx = with(density) { MeloXPlayerRecedeCompatFallbackDp.dp.toPx() }
    val tabletPx = with(density) { MeloXTabletDisplayCornerDp.dp.toPx() }
    // ⚠ 修复（2026-11-07）：系统屏幕圆角是**物理**尺寸，与横竖屏无关；转屏复轮询会在 insets
    //   过渡态用 `WindowInsets` 的**瞬时偏大值**覆盖已拿到的真值 ⇒ 圆角「突然变大再恢复」。
    //   iQOO 15 真值恰 = 46dp 兜底 ⇒ 看不出；k20p 等真值 ≠ 46dp 的机型会闪。两道闸：
    //   1) `remember` 的 key 只认 `view` / `tablet`（结构性变化才重置到兜底），转屏保留上一次拿到的值，
    //      不再被重置回 46dp 兜底；
    //   2) 下方 `LaunchedEffect` 轮询写入加「粘性闸」：已拿到真值（source ≠ "fallback"）就绝不覆盖，
    //      转屏复轮询的瞬时偏大值无法改写；仅仍处兜底占位时才写入（首启/转屏补轮询的恢复路径）。
    var info by remember(view, tablet) {
        mutableStateOf(
            MeloXDisplayCornerInfo(
                runtimePx = null,
                usedPx = if (tablet) tabletPx else fallbackPx,
                source = if (tablet) "tablet" else "fallback",
            ),
        )
    }
    // ★ key 仍含 `screenWidthDp/Height` ⇒ 转屏会复轮询（保留首启失败后的恢复路径）；
    //   但下方写入加了「粘性闸」：已拿到真值（source ≠ "fallback"）就绝不覆盖，转屏期
    //   `WindowInsets` 的瞬时偏大值不会改写 ⇒ 圆角零跳变。`remember(view, tablet)` 又保证
    //   转屏不把 `info` 重置回兜底（见上方注释）。两闸合起来：转屏全程圆角恒定。
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp, tablet) {
        if (tablet) {
            // 平板档：**不参与**手机比例判据（见 [MeloXTabletDisplayCornerDp]），
            // 但仍把系统真值轮询出来，供 debug HUD 对照。
            var runtime: Float? = null
            var source = "tablet"
            for (attempt in 1..MELOX_CORNER_POLL_ATTEMPTS) {
                val hit = meloXSystemCornerRadiusPx(view)
                if (hit != null) {
                    runtime = hit.first
                    source = hit.second
                    break
                }
                delay(MELOX_CORNER_POLL_DELAY_MS)
            }
            info = MeloXDisplayCornerInfo(runtimePx = runtime, usedPx = tabletPx, source = source)
            return@LaunchedEffect
        }
        repeat(MELOX_CORNER_POLL_ATTEMPTS) {
            val hit = meloXSystemCornerRadiusPx(view)
            if (hit != null) {
                val radius = hit.first
                val source = hit.second
                // 物理边界校验已在 [meloXSystemCornerRadiusPx] 内部按**物理屏**口径做完
                // （源 1 insets 与源 2 dimen 都过同一道 `≤ 短边一半` 闸），到这里的一定是
                // 合法值 ⇒ 直接采用。
                // ⚠ 这里**不要**再用 `configuration.screenWidthDp / screenHeightDp` 复算：
                //   分屏 / 自由窗口 / 桌面模式下 configuration 是**窗口**尺寸而非屏幕尺寸，
                //   窗口一矮就会把合法真值误杀成兜底（两套口径不一致的根因）。
                // ★ 粘性闸：已拿到真值（source ≠ "fallback"）**绝不覆盖** —— 转屏复轮询时
                //   `WindowInsets` 可能瞬时回报偏大值，绝不能拿它覆盖已稳定的真值（否则圆角
                //   「突然变大再恢复」）。仅当仍处兜底占位时才写入（首启 / 转屏补轮询的恢复路径）。
                if (info.source == "fallback") {
                    info = MeloXDisplayCornerInfo(runtimePx = radius, usedPx = radius, source = source)
                }
                return@LaunchedEffect
            }
            delay(MELOX_CORNER_POLL_DELAY_MS)
        }
    }
    return info
}

/** 只要最终值时的便捷入口。 */
@Composable
internal fun rememberMeloXDisplayCornerPx(): Float = rememberMeloXDisplayCornerInfo().usedPx

/** [rememberMeloXDisplayCornerPx] 的兜底 dp（= iQOO 15 系统真值 184px @density 4）。 */
internal const val MeloXPlayerRecedeCompatFallbackDp = 46f

/**
 * **平板档**显示圆角（dp）。
 *
 * ⚠ 为什么不能沿用手机那套：[rememberMeloXDisplayCornerInfo] 的两条基准**全是手机的** ——
 *   · 合理性判据 `圆角 ∈ 屏宽 8%~20%`（iQOO 15：184px / 1440px = 12.8%）；
 *   · 兜底 [MeloXPlayerRecedeCompatFallbackDp] = 46dp（= iQOO 15 的系统真值）。
 *   平板屏更宽，同一个物理圆角按**屏宽**算只剩 ~6% ⇒ 被判「不合理」而回落到手机的 46dp
 *   ⇒ **平板套着一个手机尺寸的圆角**（2026-10-07 用户报障：「对手机比例操纵的圆角」）。
 *   而平板自己的屏圆角在 dp 上本来就是**小**的：iPad 12.9" = 18pt / 1024pt 宽 = 1.8%，
 *   折算到 800dp 短边 ≈ 18dp。⇒ 平板档直接吃这个小圆角，不再走手机的比例判据。
 *   （2026-10-16 注：比例判据已拆，但平板档**采用值**仍保持本常量 —— 那是刻意的设计决策，
 *   见上；运行时真值会经 HUD 的 `runtime=`/`src=` 显示，不影响采用值。）
 *
 * ⚠ 只挂在 [com.lladlam.melox.ui.layout.MeloXWindowInfo.supportsPlayerTwoPane]
 *   （宽 ≥ 840dp 且高 ≥ 600dp）上 —— 平板**竖屏**仍是手机档，符合「竖屏不改」。
 *
 * ⚠ 与手机档同一条约定：这个值被**播放页下沉背板 / 播放器外壳峰值 / 页面转场裁剪**三处
 *   共用（同一屏里两个圆角差一点点就肉眼对不上），改这里 = 三处同时改。
 */
internal const val MeloXTabletDisplayCornerDp = 18f
