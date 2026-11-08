package com.lladlam.melox.ui.layout

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class MeloXWindowWidthClass { Compact, Medium, Expanded }
enum class MeloXWindowHeightClass { Compact, Medium, Expanded }

// ============================================================================================
// 设备档位识别（2026-11-07 加固：互斥 + 完备 + 单点真源）
//
// 之前有两个各自独立的布尔：`supportsPlayerTwoPane`（WindowSizeClass 语义）和
// `ultrawidePlayer`（纯宽高比 ≥ 2.2）。两者**不同源**，导致两类错判：
//  · 串：手机横屏 20:9 ≈ 2.22 / 21:9 ≈ 2.33 会命中 `ultrawidePlayer`；虽然当下靠
//    「下游都写在 `if (twoPane)` 里」侥幸没串，但那是隐含兜底，新增分支一忘就出事。
//    更糟的是 Compact 宽档构造时硬写 `supportsPlayerTwoPane = false`，却仍把
//    `ultrawidePlayer` 传 true ⇒ 同一份 window info 里「平板=否、车机=是」自相矛盾。
//  · 漏：车机的特征恰恰是「极宽 + 矮」，`heightClass == Compact（< 480dp）` 会把
//    443~479dp 高的车机整段踢回**手机单栏**，专门给车机做的横向并排布局永远用不上。
//
// 现在只保留一个纯函数 [meloXClassifyDevice]，任何尺寸输入都只能落到三档之一；
// [MeloXWindowInfo.supportsPlayerTwoPane] / [MeloXWindowInfo.ultrawidePlayer] 全部由
// [MeloXWindowInfo.deviceProfile] **派生**，调用方不可能再拿到互相矛盾的组合。
//
// ⚠ 与 `meloXUsesListDetail(Constraints)` 不是一回事：那个判的是「横 / 竖」这一正交维度，
//   且必须用 measure Constraints 同帧真值（配置口径滞后，见其 kdoc）；本枚举判的是
//   「手机 / 平板 / 车机」设备档，不参与转屏瞬间的结构切换。两者互不覆盖。
// ============================================================================================

/** 手机 / 平板 / 车机。**任意尺寸输入必且仅落入其中一档**，优先级 Car > Tablet > Phone。 */
enum class MeloXDeviceProfile { Phone, Tablet, CarHeadUnit }

/** 官方 WindowSizeClass 断点（与 androidx.compose.material3.adaptive 同源）。 */
private const val WIDTH_MEDIUM_DP = 600
private const val WIDTH_EXPANDED_DP = 840
private const val HEIGHT_MEDIUM_DP = 480
private const val HEIGHT_EXPANDED_DP = 900

/**
 * 车机几何门槛（只用于「没有 ROM 权威信号」时的兜底）。
 *
 * 光靠宽高比挡不住手机：手机横屏宽 = 其竖屏宽，主流 792~873dp，21:9 的 Xperia 约 916dp，
 * 比值 2.22 / 2.33 都会过 2.2 闸门。加两个绝对门槛后：
 * · `>= 1000dp 宽`：**主力防线**。手机横屏够不到 —— 21:9 的 Xperia 916dp、三折叠展开
 *   横屏 1061dp 但比例只有 1.43（被 ratio 挡掉）；车机 1920×720@160/240dpi = 1920 / 1280dp；
 * · `>= 380dp 高`：只用来排除桌面自由窗口 / 分屏残留的矮横条（如 1600×200）。
 *   ⚠ 2026-11-07 由 420 降到 380：1280×400dp（1920×600@160dpi 一类低矮中控）与
 *   1024×390dp 以前会被 420 整段漏判成手机单栏。降门槛**不会**放进手机 ——
 *   真正的守门人那 1000dp 宽度门槛已经把最宽的 Xperia（916dp）挡在外面；
 * · `>= 2.2:1`：平板横屏（1.33~1.92）与桌面窗口留在 Tablet。
 * 低分辨率车机（如 1920×720@320dpi = 960×360dp，无 automotive 信号）仍然漏判：
 * 它和 21:9 手机横屏在几何上不可区分（宽度够但比例接近），宁可漏也不串。
 * 这类设备若 ROM 报了 `UI_MODE_TYPE_CAR` / `FEATURE_AUTOMOTIVE` 则由 [automotive] 兜住。
 */
private const val CAR_MIN_WIDTH_DP = 1000
private const val CAR_MIN_HEIGHT_DP = 380
private const val CAR_RATIO_TIMES_TEN = 22 // width * 10 >= height * 22

/**
 * 纯几何「超宽横屏」判据（横屏 且 宽高比 ≥ 2.2），**不看 automotive**。
 *
 * ⚠ 这是与 [MeloXDeviceProfile] **正交**的第二个维度，两者不能互相替代：
 *   · 设备档回答「这是车机 / 平板 / 手机」——决定圆角基准、尺寸策略；
 *   · 本判据回答「这块屏够不够宽，宽到能左列播放器 + 右列内容并排」。
 *   只看设备档会**串**：ROM 报 automotive 但比例正常的中控（1024×600 / 1280×720，
 *   比值 1.7~1.78）会被判成车机，若再无条件套超宽布局，右列被 [MeloXUltrawideMinPaneWidth]
 *   顶掉、封面被纵向预算压小 ⇒ 反而不如平板双栏舒展。反过来只看几何会**漏**：
 *   真车机即使比例不极端也该算车机档。⇒ 两个维度分别判、在
 *   [MeloXWindowInfo.ultrawidePlayer] 里取交集。
 */
fun meloXIsUltrawide(widthDp: Int, heightDp: Int): Boolean {
    if (widthDp <= 0 || heightDp <= 0) return false
    // 整数乘法比较，避免浮点误差在 2.2 边界上左右横跳。
    return widthDp > heightDp && widthDp * 10 >= heightDp * CAR_RATIO_TIMES_TEN
}

/**
 * **三档识别的唯一真源**（纯函数，无 Compose / Context 依赖，便于配置矩阵验证）。
 *
 * @param widthDp  内容区宽（已扣常驻侧栏，dp 取整）
 * @param heightDp 内容区高（dp 取整）
 * @param automotive ROM 权威信号：`Configuration.UI_MODE_TYPE_CAR` 或
 *   `PackageManager.FEATURE_AUTOMOTIVE`。**优先于几何**：车机即使比例不超宽也不漏判。
 *
 * 互斥性：`if/else` 全分支穷尽，任一输入只返回一档；完备性：所有非正尺寸归 Phone。
 */
fun meloXClassifyDevice(
    widthDp: Int,
    heightDp: Int,
    automotive: Boolean = false,
): MeloXDeviceProfile {
    if (widthDp <= 0 || heightDp <= 0) return MeloXDeviceProfile.Phone
    val landscape = widthDp > heightDp
    // ① 权威信号优先：真车机（含比例不极端的车机屏）不会被几何门槛漏掉。
    if (automotive && landscape) return MeloXDeviceProfile.CarHeadUnit
    if (!landscape) {
        // ② 竖屏：只按官方宽分级。播放页横屏子树由 isLandscape 闸门关掉，
        //    这里判 Tablet 不会让竖屏走双栏（行为与旧版一致）。
        return if (widthDp >= WIDTH_MEDIUM_DP) {
            MeloXDeviceProfile.Tablet
        } else {
            MeloXDeviceProfile.Phone
        }
    }
    // ③ 横屏车机 = 极宽 + 矮 + 够大，三门槛同时满足才认（挡住 20:9 / 21:9 手机横屏）。
    if (meloXIsUltrawide(widthDp, heightDp) &&
        widthDp >= CAR_MIN_WIDTH_DP && heightDp >= CAR_MIN_HEIGHT_DP
    ) {
        return MeloXDeviceProfile.CarHeadUnit
    }
    // ④ 横屏平板：宽 / 高都非 Compact（官方 600 / 480 断点）；其余一律 Phone。
    return if (widthDp >= WIDTH_MEDIUM_DP && heightDp >= HEIGHT_MEDIUM_DP) {
        MeloXDeviceProfile.Tablet
    } else {
        MeloXDeviceProfile.Phone
    }
}

/**
 * 播放页「平板双栏」判据（见 [MeloXWindowInfo.supportsPlayerTwoPane]）。
 *
 * 采用 Android 官方 **WindowSizeClass** 语义（宽 / 高各自分 Compact / Medium / Expanded），
 * 断点（宽 600 / 840、高 480 / 900）由官方统一维护，App 不自己发明数字。
 *
 * 高度 != Compact（≥ 480dp）是手机的真正拦路虎 —— 手机横屏高度 = 其竖屏宽度，
 * 普遍 ≤ 440dp。**唯一例外是车机**：车机天生矮，[MeloXDeviceProfile.CarHeadUnit]
 * 档豁免高度门槛（见 [MeloXWindowInfo.supportsPlayerTwoPane]），否则会漏判成手机单栏。
 */
data class MeloXWindowInfo(
    val widthClass: MeloXWindowWidthClass,
    val heightClass: MeloXWindowHeightClass,
    val isLandscape: Boolean,
    val gutter: Dp,
    val maxContentWidth: Dp,
    val gridColumns: Int,
    /** 三档识别结果。**所有播放页档位布尔都由它派生**，禁止再各自独立计算。 */
    val deviceProfile: MeloXDeviceProfile,
    /**
     * 纯几何「超宽横屏」（横屏 且 ≥ 2.2:1，见 [meloXIsUltrawide]）。
     *
     * ⚠ 与 [deviceProfile] **正交，不可互相替代**：设备档判「车机 / 平板 / 手机」，
     *   本字段判「这块屏够不够宽到能左右并排」。ROM 报 automotive 但比例只有 1.7:1 的
     *   中控（1024×600 / 1280×720）设备档是车机、但套超宽布局会又窄又小 ⇒ 由本字段拦住。
     */
    val ultrawideGeometry: Boolean,
    private val splitScreenEnabled: Boolean,
) {
    /**
     * 播放页「左播放器 / 右歌词」双栏是否可用（**只有播放页用它**）。
     *
     * ⚠ 与 [supportsTwoPane] **不是一回事**，别合并：那个是「宽一点就多给点留白 / 多排一列」
     * 的宽松判据（搜索页间距、资料库列数、发现页宽度在用），本字段则是播放页能不能塞下
     * 一整列播放器的硬尺寸判据。
     *
     * · Phone 恒 false ⇒ 手机横屏（含 20:9 / 21:9）永远不会碰任何车机 / 平板分支；
     * · CarHeadUnit 恒 true（在用户打开分屏的前提下）⇒ **矮车机不再被 heightClass Compact 误杀**；
     * · Tablet 沿用 `heightClass != Compact` 原语义（横屏 Tablet 定义里本就 ≥ 480dp）。
     */
    val supportsPlayerTwoPane: Boolean
        get() = splitScreenEnabled && when (deviceProfile) {
            MeloXDeviceProfile.CarHeadUnit -> true
            MeloXDeviceProfile.Tablet -> heightClass != MeloXWindowHeightClass.Compact
            MeloXDeviceProfile.Phone -> false
        }

    /**
     * 「超宽横屏」（车机 / 带鱼屏）档布局闸门：关掉封面态右移、让右列歌词常驻，
     * 并把封面与控制条横向并排（见 `MeloXIOSNowPlayingScene`）。
     *
     * ⚠ **已内含 [supportsPlayerTwoPane]**：手机横屏、竖屏、用户关闭分屏时恒 false，
     *   下游不必（也不应）再自己拼 `twoPane &&`，从源头杜绝「识别成车机却按手机画」的串味。
     * ⚠ **必须同时满足 [ultrawideGeometry]**：车机档 ≠ 超宽屏。1024×600 / 1280×720 的
     *   中控（比值 1.7~1.78）设备档是 [MeloXDeviceProfile.CarHeadUnit]，但套超宽布局只会
     *   把右列压到 [MeloXUltrawideMinPaneWidth] 下限、封面被纵向预算挤小 ⇒ 这类车机走
     *   **平板双栏**更舒展（[supportsPlayerTwoPane] 仍为 true，布局不会退回手机单栏）。
     * · 输入与 widthClass 同用 `containerSize`（`LocalMeloXPaneSize ?: LocalWindowInfo`），
     *   Scene 与 SharedHost 都走 [rememberMeloXWindowInfo] ⇒ 单点真源，两处不会错拍。
     */
    val ultrawidePlayer: Boolean
        get() = supportsPlayerTwoPane &&
            deviceProfile == MeloXDeviceProfile.CarHeadUnit &&
            ultrawideGeometry

    val supportsTwoPane: Boolean get() = widthClass != MeloXWindowWidthClass.Compact
}

/** One shared window policy for phone, foldable, tablet and car-head-unit page layouts. */
@Composable
fun rememberMeloXWindowInfo(): MeloXWindowInfo {
    val containerSize = LocalMeloXPaneSize.current ?: LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    // 像素 → dp 四舍五入（`roundToInt`），避免亚像素截断导致尺寸分级边界抖动。
    val width = with(density) { containerSize.width.toDp().value.roundToInt() }
    val height = with(density) { containerSize.height.toDp().value.roundToInt() }
    val widthClass = when {
        width >= WIDTH_EXPANDED_DP -> MeloXWindowWidthClass.Expanded
        width >= WIDTH_MEDIUM_DP -> MeloXWindowWidthClass.Medium
        else -> MeloXWindowWidthClass.Compact
    }
    val heightClass = when {
        height >= HEIGHT_EXPANDED_DP -> MeloXWindowHeightClass.Expanded
        height >= HEIGHT_MEDIUM_DP -> MeloXWindowHeightClass.Medium
        else -> MeloXWindowHeightClass.Compact
    }
    // ROM 权威信号：uiMode 是设备静态属性，不参与转屏瞬间的布局结构切换 ⇒ 无滞后风险
    // （与「横屏分栏判据只能用 measure Constraints」那条铁律针对的不是同一维度）。
    val context = LocalContext.current
    val automotive = remember(context) {
        val uiMode = context.applicationContext.resources.configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_CAR ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }
    val profile = meloXClassifyDevice(width, height, automotive)
    // 与 [meloXClassifyDevice] 用同一份 dp 输入算，保证「设备档 / 超宽几何」不会一个用
    // 内容区、一个用整窗而互相打架。
    val ultrawideGeometry = meloXIsUltrawide(width, height)
    val splitEnabled = com.lladlam.melox.ui.settings.MeloXSettingsRuntime.splitScreenEnabled
    return when (widthClass) {
        MeloXWindowWidthClass.Compact ->
            MeloXWindowInfo(
                widthClass, heightClass, width > height, 20.dp, 600.dp, 2,
                profile, ultrawideGeometry, splitEnabled,
            )
        MeloXWindowWidthClass.Medium ->
            MeloXWindowInfo(
                widthClass, heightClass, width > height, 28.dp, 920.dp, 3,
                profile, ultrawideGeometry, splitEnabled,
            )
        MeloXWindowWidthClass.Expanded ->
            MeloXWindowInfo(
                widthClass, heightClass, width > height, 36.dp, 1180.dp, 4,
                profile, ultrawideGeometry, splitEnabled,
            )
    }
}
