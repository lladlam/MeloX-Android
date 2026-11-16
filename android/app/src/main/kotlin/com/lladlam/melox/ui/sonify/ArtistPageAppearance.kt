package com.lladlam.melox.ui.sonify

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/** 一个注册页对底栏遮罩的贡献：页面背景色 + 其进出场过渡进度。 */
class ScrimRegistration internal constructor(
    val color: State<Color>,
    val transition: PageScrimTransition,
    /**
     * 是否参与底栏玻璃的 tint 着色。
     *
     * 封面取色页注册时传 false：它只需要底栏遮罩色跟随渐变（否则底部出现色带），
     * 但玻璃本身应保持原样——用户裁决「只跟随采样 + 修遮罩色」，不额外加 tint。
     * 若让它参与 tint，底栏玻璃会被封面色明显染色，与裁决不符。
     */
    val contributesTint: Boolean = true
)

/** 页面进出场进度：与页面 fadeIn/fadeOut 同帧同曲线，转场结束/被保留时 transitioning=false。 */
class PageScrimTransition(
    val progress: State<Float>,
    val transitioning: State<Boolean>
)

/** Entry-scoped colors keep retained tab pages from changing the active page's scrim. */
object ArtistPageAppearance {
    val activeEntryId = mutableStateOf<String?>(null)
    private val registrations: SnapshotStateMap<String, ScrimRegistration> = mutableStateMapOf()

    fun register(
        id: String,
        color: State<Color>,
        transition: PageScrimTransition,
        contributesTint: Boolean = true
    ): ScrimRegistration {
        val registration = ScrimRegistration(color, transition, contributesTint)
        registrations[id] = registration
        return registration
    }

    fun unregister(id: String, registration: ScrimRegistration) {
        // A departing composition must not remove a newer registration for the same entry.
        if (registrations[id] === registration) {
            registrations.remove(id)
        }
    }

    fun colorFor(id: String): Color? = registrations[id]?.color?.value

    fun hasTransitioningRegistration(): Boolean =
        registrations.values.any { it.transition.transitioning.value }

    /**
     * 底栏遮罩颜色 = 默认主题色之上，把每个「对当前画面生效」的注册页按其进出场
     * 过渡进度 lerp 到该页背景色。计入条件：progress>0 且（是当前活跃 entry，或
     * 转场仍在跑）。后者覆盖退场：pop 开始时 activeEntry 已翻到上一页，但艺人页
     * 还在组合里淡出；前者覆盖转场已结束的常驻页。被保留在离屏房子里的艺人页两者
     * 皆不满足，不参与混合。进度与页面淡入淡出出自同一个 AnimatedContent
     * Transition（同帧启动、同 spec），因此遮罩换色与页面进退场逐帧同步，
     * 不会出现"底栏先变、页面后变"。
     */
    fun blendedScrimColor(default: Color, activeId: String?): Color {
        var result = default
        for ((id, registration) in registrations) {
            val progress = registration.transition.progress.value
            if (progress <= 0f) continue
            if (id != activeId && !registration.transition.transitioning.value) continue
            result = lerp(result, registration.color.value, progress.coerceIn(0f, 1f))
        }
        return result
    }

    /**
     * 页面底衬色（NavPageVisual 的全屏底色）：**仅当转场两端都是艺人域页面**时，才把
     * 各进出场中的注册页按其进度加权平均后返回；其余一律 null，调用方回退主题底。
     *
     * 为什么需要：NavHost 用 fadeIn/fadeOut 交叉淡化，两页各按透明度叠在底衬之上，
     * 中点处底衬会以 p(1-p) 的权重透出（p 为进场页进度）。艺人域两页底色都是提取色，
     * 底衬若仍是主题色就会被透出成「闪一下主题色」（浅色泛白、深色泛黑），故转场中
     * 底衬改铺提取色。
     *
     * 为什么必须两端都是艺人域：普通页（列表、搜索…）自身不铺底色，**直接透出本底衬**。
     * 只要有一端是普通页（艺人页 ↔ 普通页，无论 push 还是 pop），把底衬染成提取色就会
     * 让那一端的普通页整块变成提取色。普通页不注册，无从直接查询某条 entry 是不是普通页；
     * 但**艺人域转场两端都在注册表里，普通页转场只有一端在**，故用「除目的页外还有注册页
     * 正在进出场」作为「另一端同样是艺人域页」的判据。
     *
     * 加权平均而非「取进度最高者」：不同艺人两页互转时，取最高者会在进度交叉的瞬间让
     * 底衬跳变；加权平均随进度连续滑移，无跳变。
     */
    fun transitioningBackdropColor(activeId: String?): Color? {
        if (activeId == null || registrations[activeId] == null) return null
        var result: Color? = null
        var weight = 0f
        var hasRegisteredCounterpart = false
        for ((id, registration) in registrations) {
            if (!registration.transition.transitioning.value) continue
            val progress = registration.transition.progress.value
            if (progress <= 0f) continue
            if (id != activeId) hasRegisteredCounterpart = true
            val color = registration.color.value
            result = if (result == null) color
            else lerp(result, color, progress / (weight + progress))
            weight += progress
        }
        return if (hasRegisteredCounterpart) result else null
    }

    /**
     * 底栏 tint 通道：与 [blendedScrimColor] 同一批注册、同一条进度，但每个注册色先经
     * [tintOf] 整形成玻璃着色（源即提取色 State，无需页面侧另行注册）。
     *
     * 混合语义与 scrim 完全一致（连续 lerp，定版裁决）：
     * - 后注册者（更新的页面）后参与 lerp、占比更重，push 时 tint 先随新页淡入；
     * - pop 回已 settle 的上一页时结果是 lerp(上一页tint, 退出页tint, p)——p≈1 时
     *   即退出页色，随 p 衰减单调滑回，起点无跳变；
     * - alpha 每步 lerp 恒 ≤1，多页叠加不会越混越实。
     * 已知边界：色相差异大的两页交叉时中点会经过低 alpha 混色过渡带，tint 浓度
     * 有限且画在玻璃面内，观感是环境色晕渐变而非色带，接受。
     */
    fun blendedTintColor(default: Color, activeId: String?, tintOf: (Color) -> Color): Color {
        var result = default
        for ((id, registration) in registrations) {
            // 封面取色页只参与遮罩色，不参与玻璃着色（见 ScrimRegistration.contributesTint）
            if (!registration.contributesTint) continue
            val progress = registration.transition.progress.value
            if (progress <= 0f) continue
            if (id != activeId && !registration.transition.transitioning.value) continue
            result = lerp(result, tintOf(registration.color.value), progress.coerceIn(0f, 1f))
        }
        return result
    }

}

/** tint 绘制浓度（玻璃面内、surface 色之上），与遮罩 [bottomScrimAlpha]（见 MainActivity）相互独立。 */
internal const val bottomTintAlphaLight = 0.45f
internal const val bottomTintAlphaDark = 0.40f

/**
 * 提取色 → 玻璃面 tint：HSV 整形以「忠实还原」为目标，只钳制不失真——
 * 饱和度/明度走钳制区间（保留提取色本身的明暗特征，不再钉死成单一粉彩/暗辉值，
 * 此前明度钉 0.80 是底栏取色观感比真提取色浅很多的原因），仅压掉两端极端值
 * 防脏（过暗发闷）防刺（过亮过饱和）。饱和度 <0.08 的中性色（含调色板未就绪的
 * 占位白 0xFFF2F2F4 / 占位黑 0xFF1B1B1D）直接返回透明，等价于本页不参与着色。
 */
internal fun accentTintOf(source: Color, isDark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(source.toArgb(), hsv)
    if (hsv[1] < 0.08f) return Color.Transparent
    hsv[1] = if (isDark) hsv[1].coerceIn(0.40f, 0.90f) else hsv[1].coerceIn(0.35f, 0.85f)
    hsv[2] = if (isDark) hsv[2].coerceIn(0.30f, 0.60f) else hsv[2].coerceIn(0.55f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
        .copy(alpha = if (isDark) bottomTintAlphaDark else bottomTintAlphaLight)
}

/**
 * 取当前 destination 的进出场进度。NavHost 的 fadeIn/fadeOut 默认 spec 是
 * spring(1f, 400f, 0.01f)（反编译 animation 1.10.2 确认），这里的
 * Transition.animateFloat 挂在同一个 AnimatedContent Transition 上、用同 spec，
 * 故与页面淡入淡出逐帧一致；无转场（初始 destination/进程恢复）时 progress 恒为
 * 1f，transitioning 恒为 false。须在 NavHost 的 composable 内容 lambda
 * （AnimatedContentScope 接收者）内调用。
 */
@Composable
fun AnimatedContentScope.scrimTransition(): PageScrimTransition {
    val transition = this.transition
    val progress = with(transition) {
        animateFloat(
            transitionSpec = {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.01f
                )
            },
            label = "artistPageScrimProgress"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    }
    val transitioning = remember(transition) {
        derivedStateOf { transition.currentState != transition.targetState }
    }
    return remember(transition, progress, transitioning) {
        PageScrimTransition(progress, transitioning)
    }
}
