package com.lladlam.melox.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.Backdrop
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.diagnostics.MeloXCrashRecorder

/*
 * 液态玻璃的**能力门控**（2026-10-01 重写）。
 *
 * 这里只回答一个问题：**这一帧到底能不能画玻璃**。
 *   · 窗口没有硬件加速 ⇒ RenderNode / RenderEffect / RuntimeShader 全部无从谈起
 *     ⇒ 回落到各材质已经写好的 `background(...)` 实心分支。
 *     这类设备本来就没有玻璃条件，属于「不支持」，**不是降级**。
 *   · 其余情况一律放行，不改动任何数值、不改变观感。
 *
 * ⚠ **已删除：原先那套「崩溃自愈」**（strike 计数 → 永久关玻璃）。
 *   两个不可接受的代价：
 *     ① 它会把**能正常跑玻璃的机型**的玻璃关掉 —— 那是把 bug 藏起来，不是修 bug；
 *     ② 判据「上个进程没活到正常退后台」与 `am force-stop` / `adb install -r`
 *        在 Java 层完全同形，导致**正常装机流程自己触发**「玻璃莫名其妙没了」。
 *   ⇒ 替代方案是**能定位的崩溃**：`MeloXCrashRecorder` 把真实栈落到 `filesDir/crash/`，
 *     并标注崩溃瞬间玻璃是否在渲染。有了那条栈才知道该改哪一行；
 *     没有它，任何「修复」都只是猜 —— 已经吃到过靠猜迭代多轮的教训。
 *
 * ⚠ 另一条必须守住的约束：**所有会真正画玻璃的 composable 都必须经
 *   `meloXGlassBackdrop()` 取背板**，不要直接读 `LocalMeloXBackdrop.current`。
 *   前者带着硬件加速门控，后者绕不过去。
 *   （`meloXGlassAvailable()` 给「只想知道一个布尔」的地方用，不触发渲染打点。）
 */

/**
 * 玻璃此刻是否可用：既有背板，窗口又确实挂了硬件加速，且系统版本支持玻璃所需的原生能力。
 *
 * ⚠ **跨设备崩溃加固（2026-10-08）**：`minSdk = 26`，但 `kyant0:backdrop` 的玻璃走
 *   `RenderEffect`（API 31+）与 AGSL `RuntimeShader`（API 33+）。在低于 33 的设备
 *   （Android 8–12L）上，一旦进入 `drawBackdrop`，要么抛 `NoSuchMethodError`，要么触发
 *   GPU 驱动层的**原生 abort（Kotlin try/catch 接不住）**⇒ 整进程闪退。
 *   这类设备本就不支持玻璃，按「不支持」处理（非降级），回落到材质函数里写好的
 *   实心 `background(...)` 分支。⚠ 这也是文件顶部说的「不支持 ≠ 降级」的唯一正确判定点：
 *   能力由系统版本决定，不能靠「上一个进程有没有活到后台」这种会误伤的启发式。
 *
 * ⚠ 不要用 `LocalMeloXBackdrop.current != null` 代替它：在无硬件加速时那个判断仍为 true，
 *   会让上层按「有玻璃」去排版（例如选半透明 tint），而玻璃其实一次都不会画
 *   ⇒ 出现「半透明 tint 但没有采样背板」的不可读 UI。
 */
@Composable
internal fun meloXGlassAvailable(): Boolean {
    // ⚠ 玻璃的原生能力门槛：AGSL(RuntimeShader) 需 API 33，RenderEffect 需 API 31。
    //   库内部是否对 blur 也用 AGSL 无法静态确认，故取较高者 33 作为整功能的安全下限，
    //   确保 33 以下设备绝不进入 drawBackdrop。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (!LocalView.current.isHardwareAccelerated) return false
    return LocalMeloXBackdrop.current != null
}

/**
 * 取当前可用的玻璃背板；不可用时返回 null。
 *
 * ⚠ **所有会真正画玻璃的 composable 都必须经这里取 backdrop。**
 *   返回 null 时每个材质函数都已写好 `background(...)` 实心回落分支，
 *   于是 `drawBackdrop` 一次都不跑，RenderEffect / AGSL / GraphicsLayer 全绕开。
 */
@Composable
internal fun meloXGlassBackdrop(): Backdrop? {
    if (!meloXGlassAvailable()) return null
    // 只做记录：给崩溃现场标注「当时玻璃在渲染」。不参与任何门控/降级。
    MeloXCrashRecorder.markGlassRendering()
    return LocalMeloXBackdrop.current
}

/**
 * 把按压进度洗回 [0, 1]。
 *
 * 必要性（实测代码路径）：
 * - `PublicInteractiveHighlight` 用 `spring(0.5f, 300f)` —— **欠阻尼 ζ=0.5**，
 *   过冲约 +16.3%、回弹约 −16.3% ⇒ `pressProgress` 真实区间 ≈ [−0.16, 1.16]。
 *   它经 `meloXLiquidButton` 喂给 `meloXGlassSurface`（登录页 / 云盘 / 资料库 / 收藏页的按钮）。
 *
 * ⚠ **`lens()` 的 AGSL 在 `refractionHeight == 0` 时产生 NaN**（逐句核对 shader 原文后的准确机理）：
 *   ```
 *   float sd = sdRoundedRect(...);          // 负=形状内，正=形状外
 *   if (-sd >= refractionHeight) return content.eval(coord);   // 深处提前返回
 *   sd = min(sd, 0.0);                      // ← 形状外的像素被夹到 0
 *   float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
 *   ```
 *   - 形状内那一圈（`-sd ∈ [0, refractionHeight)`）⇒ `x = 1 - (-sd)/H ∈ (0, 1]`，
 *     `circleMap(x) = 1 - sqrt(1 - x²)` 恒在定义域内 ⇒ **H > 0 时无论多小都安全**。
 *   - 形状**外**的像素（圆角矩形的角、抗锯齿边缘，`sd > 0`）被夹成 `sd = 0`
 *     ⇒ `x = 1 - 0/H`：**H > 0 ⇒ x = 1，安全；H = 0 ⇒ 0/0 = NaN**
 *     ⇒ `d = NaN` ⇒ `refractedCoord = NaN` ⇒ `content.eval(NaN)`。
 *   - 而 `H = 0` 恰好是**静息态**（`lens(H·p, A·p)`，`p = 0`）——底栏面板/捕获层/水珠
 *     每一帧都在画，所以这是每帧命中的路径。修法只能是「整段跳过 lens」，不能是「把 H 调小」。
 *
 * ⚠ 另有一个**与 p 无关、属上游库自身**的一像素级 NaN：
 *   `normalize(gradSdRoundedRect(...) + depthEffect * normalize(centeredCoord))` 里
 *   `centeredCoord == (0,0)`（图层正中心像素）会让 `normalize()` 拿到零向量 ⇒ NaN；
 *   IEEE `0 × NaN = NaN`，`depthEffect = 0` 也挡不住。影响单像素、不可见，不改（改了即偏离上游）。
 *
 * ⚠ 底栏水珠用的是 `PublicDampedDragAnimation` / `LiquidDragAnimation`，其 press spec 是
 *   `spring(1f, 1000f)`（临界阻尼、不过冲、静息恰好落在 0），所以本函数对它们恒等 ——
 *   它们真正需要的是 `lens` 的零值门，而不是区间清洗。
 */
internal fun safeProgress(value: Float): Float =
    if (value.isNaN() || value.isInfinite()) 0f else value.coerceIn(0f, 1f)

/**
 * 把任意 Float 洗成有限值（NaN / ±Inf → 0）。
 *
 * 用途：任何要写进 `GraphicsLayerScope`（= HWUI `RenderNode` 变换矩阵）的量。
 * 一旦 NaN 进到矩阵里，拍扁后的矩阵不可逆，绘制结果未定义 —— 这是「能跑玻璃的机型
 * 上因为自身数值问题崩」的典型入口，所以所有 `layerBlock` 出口都过一遍。
 */
internal fun Float.finiteOrZero(): Float = if (isNaN() || isInfinite()) 0f else this
