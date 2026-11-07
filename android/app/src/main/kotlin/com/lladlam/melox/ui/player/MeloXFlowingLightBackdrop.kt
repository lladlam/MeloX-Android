package com.lladlam.melox.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lladlam.melox.playback.MeloXAudioReactiveRuntime
import com.lladlam.melox.MeloXAppVisibility
import com.lladlam.melox.ui.MeloXUiMotionRuntime
import com.lladlam.melox.ui.settings.MeloXLyricsRenderingQuality
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun MeloXBlurredArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artworkModel = remember(context, artworkUrl) {
        ImageRequest.Builder(context).data(artworkUrl).size(320, 320).crossfade(220).build()
    }
    MeloXBlurredBackdrop(artworkModel, modifier)
}

/**
 * Shared renderer for the blurred artwork and custom-image backdrops: the
 * same 38dp blur, 18% overscan and Apple-style legibility scrims. Routing the
 * custom image through this renderer keeps lyric contrast identical to the
 * established mode instead of exposing a flatter, brighter background.
 */
@Composable
internal fun MeloXBlurredBackdrop(
    model: Any?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        // ★★★ 2026-11-06：**回到上游原版**（删掉 2026-11-04 加的 LOD=4 降采样 +
        //   动画期 22dp 降半径）。理由不是「想省事」，是这两笔已经被实测证伪：
        //
        //   · 11-05 严格 A/B（root 静默换包、同协议 5 轮 × 600 帧）：
        //     OPEN 方向更慢 0.5ms、CLOSE 方向更快 0.46ms ⇒ **方向相反、幅度 ±5%**，
        //     即无可测收益。
        //   · 根因：Skia 的 RenderEffect blur 对大 sigma **本来就自动降采样**（这正是
        //     「暂停态无模糊 9.88ms ≈ 播放态 38dp 全屏模糊 9.92ms」的原因），手写 LOD
        //     是重复劳动；省不到时间，却要额外付一次「1/4 光栅化 + 4× 放大」的代价 ——
        //     封面里的文字/发丝这类高频会被折成硬边块，放大后就是肉眼可见的「露馅」。
        //
        //   ⛔ 别再按「像素量 1/16、半径 1/4」的算术把它加回来：这笔账 Skia 已经算过。
        //   ⛔ 也别再把 `relaxed` 那种「动画期降半径」加回来 —— 同样无收益，且动画期
        //     正是背景被放大最多、最容易被看出糊得不均匀的时刻。
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }
                .blur(38.dp),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .30f))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .48f)),
                ),
            )
        }
    }
}

/**
 * Three slow artwork planes based on Apple Music's lyric background: 120s,
 * 90s and 70s linear rotations. Low quality deliberately keeps one plane to
 * avoid turning a lyric view into three full-screen blur passes.
 */
@Composable
internal fun MeloXLyricsArtworkBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    planeOverride: Int? = null,
) {
    val context = LocalContext.current
    val appInForeground = MeloXAppVisibility.isForeground
    val quality = MeloXSettingsRuntime.lyricRenderingQuality
    val basePlaneCount = when (quality) {
        MeloXLyricsRenderingQuality.Low -> 1
        MeloXLyricsRenderingQuality.Balanced -> 2
        MeloXLyricsRenderingQuality.High -> 3
    }
    // 每层都是一张全屏模糊离屏层，转场期同时挂满会在 RenderThread 的 Drawing 段
    // 叠出 4ms+（实测总帧 11~27ms，预算 11.07ms）。转场只有 350ms 且画面本身在
    // 快速位移，少一层模糊在观感上分辨不出来，所以转场期降级、落定后立刻复原。
    val planeCount = planeOverride?.coerceIn(1, basePlaneCount) ?: basePlaneCount
    val backgroundFrameRate = MeloXSettingsRuntime.lyricBackgroundFrameRate.coerceIn(15, 60)
    val saturation = if (MeloXSettingsRuntime.lyricReduceMotion) 3.5f else 2.5f
    // 每层的 ColorFilter 现在自带 alpha（见下方 2026-10-23 注释），不再共用同一个。
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val elapsedWhilePlayingMs = remember(artworkUrl) { mutableLongStateOf(0L) }
    val artworkModel = remember(context, artworkUrl) {
        ImageRequest.Builder(context).data(artworkUrl).size(384, 384).crossfade(220).build()
    }

    // The source implementation invalidates its Canvas roughly every 42ms
    // (~24fps). Throttling here is intentional: three blurred planes at 60fps
    // make the lyric page visibly hotter without improving the slow motion.
    // A track change must keep redrawing briefly even while paused: the lyric
    // control surface samples this recorded layer, and a layer that stops
    // drawing keeps the previous track in the blurred controls.
    val settleUntilMs = remember(artworkUrl) { SystemClock.elapsedRealtime() + 1_600L }
    LaunchedEffect(planeCount, artworkUrl, backgroundFrameRate, appInForeground) {
        var previousFrameAt = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if ((!latestIsPlaying && now >= settleUntilMs) || !appInForeground) {
                previousFrameAt = now
                delay(500L)
                continue
            }
            elapsedWhilePlayingMs.longValue += now - previousFrameAt
            previousFrameAt = now
            delay((1_000L / backgroundFrameRate.toLong()).coerceAtLeast(1L))
        }
    }

    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        // ★★ 2026-11-04（LOD 降采样）：每层模糊不再按全屏尺寸光栅化，推导见 `BackdropBlurLod`。
        //   单层原来是全屏 4.56M px × sigma≈72px 的高斯（本文件下方注释实测过 ≈4ms Drawing），
        //   High 质量三层就是 12ms ⇒ 必然超 11.07ms 的帧预算。降到 1/4 尺寸后
        //   像素量 1/16、半径 1/4，三层同时挂也进得了预算。
        val lod = backdropBlurLod
        // Compose keeps only one bitmap generation and one set of full-screen
        // blur nodes alive during artwork changes.
        //
        // ⚠⚠⚠ 2026-10-22（GPU 侧，第二批）：**旋转必须待在模糊层的「外面」**。
        //
        //   原来 `AsyncImage.graphicsLayer{scale,rotationZ,alpha}.blur(r)` 是**一个** RenderNode
        //   （Compose 会合并同一 Modifier 链上相邻的 graphicsLayer 与 blur）。而 `rotationZ` 由
        //   `elapsedWhilePlayingMs` 驱动、**每 42ms 变一次**（默认 24fps，见
        //   `lyricBackgroundFrameRate`）⇒ 那个层的 displaylist 每 42ms 失效一次 ⇒
        //   **整张全屏位图重新光栅化 + 重算一次高斯模糊**。本文件第 104~106 行的注释已经
        //   实测过单层成本 ≈ 4ms RenderThread Drawing，而帧预算只有 11.07ms、默认 High
        //   质量是 3 层（`MeloXLyricsRenderingQuality.High`）⇒ 每 42ms 必然攒出一个 12ms 的
        //   超预算帧。这就是「歌词页打开/收起、以及转屏时卡」的 GPU 侧主因（上一批只治了
        //   CPU 侧重组成，动不到这里）。
        //
        //   改法利用高斯模糊的**旋转等变性** `blur(rotate(θ,x)) ≡ rotate(θ,blur(x))`：
        //   把 rotationZ/alpha/translation 提到外层，模糊层的内容从此**恒定**
        //   （只在换歌/换图时变一次），每帧只更新外层矩阵 —— GPU 上退化成一次纹理采样。
        //
        //   ⚠ `scale` 必须**跟着一起提到外层**，不能留内层：内层纹理只有屏幕那么大，
        //     外层旋转 360° 时四角会露空。提到外层后，1.34 倍放大的是「已模糊的屏幕纹理」，
        //     等于把 overscan 补在旋转之后 ⇒ 不露角。
        //     代价是模糊半径被放大 1.34 倍，所以内层半径要除以 1.34 作为补偿：
        //     30dp / 1.34 ≈ 22.4dp、24dp / 1.34 ≈ 17.9dp —— 屏幕上看到的模糊强度**逐像素不变**。
        //     ⛔ 不要图省事把内层半径写回 30dp，那样背景会明显变糊。
        repeat(planeCount) { index ->
            // ★★ 2026-10-23：把每层的 `alpha` 从**外层 graphicsLayer** 搬到**内层 colorFilter**。
            //
            //   实测依据（真机 iQOO 15 / V2505A，dumpsys gfxinfo + atrace）：
            //     歌词页开闭 p50 total=14.7ms（预算 11.07ms@90Hz），GPU p50=6.74ms，
            //     而组合/布局只有 0.05ms ⇒ 瓶颈全在绘制管线，不在重组。
            //     trace 里 RenderThread **每 32~42ms**（正好等于 `lyricBackgroundFrameRate`
            //     的推进周期）就重传一整批纹理，其中含 2 张 `1196x2758` 全屏位图（各 ~13MB），
            //     且每次 image id 都不同 ⇒ **每帧新建 Image 并全屏重传**。
            //
            //   机制：外层 graphicsLayer 同时挂着 `alpha`(.48/.28) 与 `rotationZ`。
            //     · `alpha < 1` 让这一层**必须离屏**（非透明合成）；
            //     · `rotationZ` 每 42ms 变一次 ⇒ 这个全屏离屏层**每 42ms 重新光栅化**，
            //       连里面的高斯模糊一起重做，再整张重传纹理。3 个平面就是 3 份。
            //
            //   改法：高斯与合成都是线性的（`blur(a·x) = a·blur(x)`，
            //     src-over 合成里 `alpha=0.48` 等价于「源 alpha 预乘 0.48」），
            //     所以把 alpha 挪到内层 ColorMatrix 的 alpha 行（只缩 A，不动 RGB），
            //     与外层 alpha 的**逐像素结果完全相同**。
            //     外层于是只剩 scale/rotation/translation 这些**纯变换** ⇒ RenderNode
            //     每帧只更新矩阵，子树 displaylist 与模糊纹理全体复用，不再重光栅化。
            //
            //   ⚠ 逐像素等价的推导：非预乘存储下，(R,G,B,A) 绘制时预乘为 (R·A, G·A, B·A, A)。
            //     只缩 alpha 行 ⇒ (R·0.48A, …, 0.48A) = 0.48 × (R·A, …, A) ⇒ 与整体 alpha 一致。
            //     ⛔ 不要图省事把 RGB 行也乘 0.48，那样会变成 0.48² 双重变暗。
            //   ⚠ `scale = 1.34` 仍必须留在外层（旋转时四角防露空，见上方注释）；
            //     它只是纯变换，超出父边界的部分由外壳的 `clip = true` 裁掉，不产生离屏。
            val planeAlpha = if (index == 0) .48f else .28f
            val planeColorFilter = remember(saturation, planeAlpha) {
                ColorFilter.colorMatrix(
                    ColorMatrix().apply {
                        setToSaturation(saturation)
                        // 4x5 行优先 ⇒ index 18 就是 [3][3]（alpha 输出对 alpha 输入的系数）。
                        // 只缩 alpha 行、不动 RGB，才等价于整体 alpha（见上方推导）。
                        values[18] = planeAlpha
                    },
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.34f
                        scaleY = 1.34f
                        val elapsed = elapsedWhilePlayingMs.longValue.toFloat()
                        val duration = when (index) {
                            0 -> 120_000f
                            1 -> 90_000f
                            else -> 70_000f
                        }
                        val direction = if (index == 0) -1f else 1f
                        rotationZ = direction * (elapsed % duration) / duration * 360f
                        translationX = (index - 1) * 34f
                        translationY = (1 - index) * 22f
                        // ⛔ 这里不要再写 `alpha = …`：那会把这层重新变成离屏，回到
                        //    「每 42ms 全屏重光栅化」的老路（见上方 2026-10-23 注释）。
                    },
            ) {
                // 内层：按 1/LOD 测量并以 r/LOD 模糊，再放大回外层尺寸（见 [backdropBlurLod]）。
                // ⚠ 放大必须挂**这一层**、不能并进外层：外层要保持「只有纯变换」，
                //   模糊纹理才能跨帧复用（见上方 2026-10-23 的整套推导）。
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().backdropBlurLod()) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = planeColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(
                                // 外层放大 1.34 倍、本层再放大 lod 倍 ⇒ 内层半径等比例缩小，
                                // 屏幕上的强度不变。
                                (if (quality == MeloXLyricsRenderingQuality.High) 22.4.dp else 17.9.dp) / lod,
                            ),
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .34f))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .52f)),
                ),
            )
        }
    }
}

/**
 * Android renderer for MeloX's artwork-driven Flowing Light background.
 * Palette extraction is identical in shape to ArtworkAccentColorProvider:
 * 160px downsample -> 3x3 cell averages. Android has no SwiftUI MeshGradient
 * equivalent on all supported API levels, so the nine control colors are laid
 * out as overlapping radial fields and then sampled through a time-varying
 * domain warp. The warp is what makes the field flow; the earlier version only
 * moved the field centers and read as breathing rather than motion.
 *
 * Brightness is fixed. When a real beat analysis is available its energy/beat
 * drive the warp speed and amplitude; otherwise the field drifts at a constant
 * speed from the synthetic playback clock.
 */
@Composable
internal fun MeloXFlowingLightBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    mediaId: String? = null,
    modifier: Modifier = Modifier,
    mixOutgoingArtworkUrl: String? = null,
    mixIncomingArtworkUrl: String? = null,
) {
    val context = LocalContext.current
    val appInForeground = MeloXAppVisibility.isForeground
    var targetPalette by remember { mutableStateOf(ArtworkDynamicPalette.Fallback) }
    val renderingQuality = MeloXSettingsRuntime.lyricRenderingQuality
    val backgroundFrameRate = MeloXSettingsRuntime.lyricBackgroundFrameRate.coerceIn(15, 60)
    val meshWidth = when (renderingQuality) {
        MeloXLyricsRenderingQuality.Low -> 24
        MeloXLyricsRenderingQuality.Balanced -> 32
        MeloXLyricsRenderingQuality.High -> 48
    }
    val meshHeight = meshWidth * 23 / 10
    val meshBitmaps = remember(meshWidth, meshHeight) {
        List(2) { Bitmap.createBitmap(meshWidth, meshHeight, Bitmap.Config.ARGB_8888) }
    }
    // ⛔ 不要给这两张位图预填任何颜色（哪怕只是「平均色的纯色底」）：全 0 的透明位图
    //   正是「暂停且从未渲染过 ⇒ 背景黑」这条**有意设计**的实现（判据在下方渲染循环的
    //   暂停门 `if (mediaId != null && !sample.isPlaying) continue`）。任何预填都会让
    //   「暂停时打开播放页」显示成一片纯色而非黑 —— 2026-10-25 用户实测报障，已撤回。
    val meshImages = remember(meshBitmaps) { meshBitmaps.map(Bitmap::asImageBitmap) }
    var meshImage by remember(meshImages) { mutableStateOf(meshImages.first()) }
    val currentColors = remember(meshWidth, meshHeight) {
        MutableList(9) { index ->
            ArtworkDynamicPalette.Fallback.cells.getOrElse(index) { ArtworkDynamicPalette.Fallback.average }
        }
    }
    val currentAverage = remember(meshWidth, meshHeight) { arrayOf(ArtworkDynamicPalette.Fallback.average) }
    val phase = remember(meshWidth, meshHeight) { floatArrayOf(0f) }

    // Deliberately no recycle() on dispose. The render coroutine runs on a
    // background dispatcher and can still be inside setPixels() when this
    // composable leaves or the mesh size changes; recycling here raced with
    // that write and crashed with "Can't call setPixels() on a recycled
    // bitmap". The two bitmaps are tiny (<= 48x110), so the GC reclaims them.

    LaunchedEffect(context, artworkUrl, mixOutgoingArtworkUrl, mixIncomingArtworkUrl) {
        // Only cover identities key extraction: AutoMix progress updates must not
        // decode artwork on every frame. Keep both decks stable until handoff.
        val sources = listOf(mixOutgoingArtworkUrl ?: artworkUrl, mixIncomingArtworkUrl)
            .filterNotNull().filter(String::isNotBlank).distinct()
        val palettes = coroutineScope {
            sources.map { source -> async { ArtworkDynamicPaletteProvider.paletteFor(context, source) } }.awaitAll()
        }
        targetPalette = palettes.reduceOrNull { outgoing, incoming -> outgoing.combinedWith(incoming) }
            ?: ArtworkDynamicPalette.Fallback
    }

    LaunchedEffect(isPlaying, artworkUrl, mediaId, renderingQuality, backgroundFrameRate, meshBitmaps, targetPalette, appInForeground) {
        val requestedFrameDelayMs = (1_000L / backgroundFrameRate.toLong()).coerceAtLeast(1L)
        val frameDelayMs = when (renderingQuality) {
            MeloXLyricsRenderingQuality.Low -> requestedFrameDelayMs.coerceAtLeast(50L)
            MeloXLyricsRenderingQuality.Balanced -> requestedFrameDelayMs.coerceAtLeast(33L)
            MeloXLyricsRenderingQuality.High -> requestedFrameDelayMs.coerceAtLeast(16L)
        }
        var energy = .18f
        var beatPulse = 0f
        var writeIndex = 1
        val pixels = IntArray(meshWidth * meshHeight)
        val centersX = FloatArray(currentColors.size)
        val centersY = FloatArray(currentColors.size)
        if (MeloXSettingsRuntime.reduceMotion) {
            currentColors.indices.forEach { index ->
                currentColors[index] = targetPalette.cells.getOrElse(index) { targetPalette.average }
            }
            currentAverage[0] = targetPalette.average
            withContext(Dispatchers.Default) {
                fillFlowingMeshPixels(
                    pixels = pixels,
                    meshWidth = meshWidth,
                    meshHeight = meshHeight,
                    colors = currentColors,
                    average = currentAverage[0],
                    phase = phase[0],
                    energy = energy,
                    beatPulse = beatPulse,
                    reactive = false,
                    saturation = MeloXSettingsRuntime.flowingLightSaturation.coerceIn(0f, 2f),
                    brightness = MeloXSettingsRuntime.flowingLightBrightness.coerceIn(.4f, 1.6f),
                    centersX = centersX,
                    centersY = centersY,
                )
                val bitmap = meshBitmaps[writeIndex]
                if (!bitmap.isRecycled) {
                    bitmap.setPixels(pixels, 0, meshWidth, 0, 0, meshWidth, meshHeight)
                }
            }
            meshImage = meshImages[writeIndex]
            awaitCancellation()
        }
        var lastRenderNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (!MeloXAppVisibility.isForeground) continue
            // ★ 2026-10-22 第二轮：播放页**运动期**把网格出帧降到 ≤15fps。
            //
            //   为什么这不只是"省后台线程"：这张位图是 `playerControlBackdrop` 的**宿主内容**，
            //   而底栏那块「全屏宽 × 279dp」的玻璃正是从它采样。位图每更新一次，背板的那份
            //   录制就脏一次 ⇒ 那一帧要重新录制 + 重做高斯。所以「每帧更新网格」等于
            //   「每帧让全场每块玻璃重做一次模糊」，这才是页间切换 / 转屏里那笔最贵的账。
            //
            //   ⚠ 降档幅度**必须按各自的设置值算**，不能写死毫秒数：见下面 `reliefDelayMs`。
            //   观感上不可辨还因为运动期间人眼锁的是移动中的封面 / 列表行，
            //   而网格本身是缓慢漂移的大色块（`ConstantFlowMotion`）。
            //
            //   ⚠ **不是冻结**：`phase` 仍按真实 `elapsedMs` 累加（见下面第 371 行），
            //     只是少写几次位图 ⇒ 落定后恢复 16ms 也不会跳变。上一轮试过的 `frozen`
            //     方案会跳，原因写在 SharedHost 那段「已撤回」注释里，别再混用。
            // ★ 2026-10-22 第六轮：降档从「写死 66ms」改成「按当前设置翻倍，再夹紧」。
            //
            //   写死 `coerceAtLeast(66)` 的毛病是它表达的是**绝对帧间隔**（帧率不超过 15），
            //   于是收益完全取决于用户把滑条放在哪：
            //     设置 24fps → 41→66ms，只省 38%（默认值，也就是大多数人的实际情况）
            //     设置 60fps → 17→66ms，省 75%
            //   同一个常量在两档设置下差了 2 倍收益 ⇒ 等于这条降档只对高帧率用户有意义。
            //
            //   改成先按设置翻倍、再夹进同一个观感区间：
            //     24fps(41ms) → 82ms(12fps) ｜ 60fps(17ms) → 66ms(15fps) ｜ 15fps(67ms) → 84ms(12fps)
            //   三种设置落到 12~15fps 同一条带上：观感一致、收益一致、不依赖用户选了什么。
            //
            //   ⚠ 夹紧上限是**观感门槛**而不是性能门槛：网格是缓慢漂移的大色块，12fps 与
            //     15fps 在这个位移量下不可辨（15fps 已在真机上被接受）；再往上
            //     100ms(10fps) / 125ms(8fps) 收益更大，但没有实测过，⛔ 别擅自越线。
            val reliefDelayMs = (frameDelayMs * 2).coerceIn(ReliefMinDelayMs, ReliefMaxDelayMs)
            val effectiveDelayMs = when {
                MeloXUiMotionRuntime.anyPlayerMotion -> reliefDelayMs
                // ★ 2026-10-22 第四轮：回前台**结算窗口**（见 `MeloXUiMotionRuntime`）。
                //   同一条账：位图是 `playerControlBackdrop` 的宿主内容，更新一次就让全场
                //   玻璃重新录制 + 重做高斯。回前台那 1~1.4s 实测 79fps / 掉 45 帧，
                //   这段时间先按 15fps 出网格，窗口一过自动恢复（按 `elapsedRealtime` 自过期，
                //   没有「谁负责清标志」这种慢性病）。
                //   ⚠ 与上面那条一样是**降档不是冻结**：`phase` 照真实 elapsed 累加。
                MeloXUiMotionRuntime.foregroundSettling -> reliefDelayMs
                com.lladlam.melox.playback.MeloXAudioAnalysisLoad.isBusy -> reliefDelayMs
                else -> frameDelayMs
            }
            if (lastRenderNanos != 0L && frameNanos - lastRenderNanos < effectiveDelayMs * 1_000_000L) continue
            // ⚠ 上限 100f 是**相位补偿**的夹紧（长时间挂起后别一口气补太多），不是帧间隔。
            //   若哪天把 `ReliefMaxDelayMs` 调到 >100，这里必须同步，否则实际帧间隔会被它盖住
            //   （下面 `elapsedMs` 只用于推进 phase，不参与出帧判定，所以不会真的节流 —— 但
            //   调色板混合量会因此偏小）。
            val elapsedMs = if (lastRenderNanos == 0L) frameDelayMs.toFloat()
            else ((frameNanos - lastRenderNanos) / 1_000_000f).coerceIn(1f, 100f)
            lastRenderNanos = frameNanos
            val sample = MeloXAudioReactiveRuntime.sample(mediaId)
            // Freeze in step with the music. A backdrop bound to a track pauses
            // with it; pages that render this without a media id (playlist
            // covers, dead scenes) keep drifting.
            //
            // ⛔⛔ 2026-10-22 恢复作者原意（用户指正）：**暂停时不出帧是设计，不是 bug**。
            //   我在修上一条「转场黑屏」时顺手加了 `&& hasRendered`，理由是「暂停打开播放页
            //   会停在空位图上 ⇒ 整片黑」。但用户确认那个黑屏是作者有意的 —— 这一行的本意
            //   就是「跟着音乐一起停」，从未渲染过的时候停在空白位图上正是这个语义的结果。
            //   ⇒ 已回退成原样。**不要再把它当缺陷「修」一次。**
            //   真正属于回归的是下面 `frozen` 那一段（转场 LOD 冻在从未绘制的位图上），
            //   那里的 `hasRendered` 门槛**必须保留**，两者互不相干。
            if (mediaId != null && !sample.isPlaying) continue
            // Shape follows the music only when a real beat analysis exists.
            // Otherwise the mesh drifts at a constant speed instead of tracking
            // the synthetic playback clock, so its brightness never pulses.
            val reactive = sample.hasAnalysis
            // 「减少动态效果」时不重算调色板（上游 0.6.2 7a5a7ef）。
            val animatePalette = !MeloXSettingsRuntime.reduceMotion
            energy += (sample.energy - energy) * .18f
            beatPulse += (sample.beat - beatPulse) * .32f
            val motion = if (reactive) {
                .021f + energy.coerceIn(0f, 1f) * .030f + beatPulse * .013f
            } else {
                ConstantFlowMotion
            }
            // 用户可调的速度倍率（设置 → 播放器外观 → 流动速度）。
            val speed = MeloXSettingsRuntime.flowingLightSpeed.coerceIn(.25f, 2f)
            phase[0] = (phase[0] + motion * speed * elapsedMs / (1_000f / 60f)) % FlowPhasePeriod
            if (animatePalette) {
                val paletteBlend = (elapsedMs / 280f).coerceIn(.08f, .45f)
                currentColors.indices.forEach { index ->
                    currentColors[index] = lerpColor(
                        currentColors[index],
                        targetPalette.cells.getOrElse(index) { targetPalette.average },
                        paletteBlend,
                    )
                }
                currentAverage[0] = lerpColor(currentAverage[0], targetPalette.average, paletteBlend)
            }
            val saturation = MeloXSettingsRuntime.flowingLightSaturation.coerceIn(0f, 2f)
            val brightness = MeloXSettingsRuntime.flowingLightBrightness.coerceIn(.4f, 1.6f)
            val bitmap = meshBitmaps[writeIndex]
            withContext(Dispatchers.Default) {
                fillFlowingMeshPixels(
                    pixels = pixels,
                    meshWidth = meshWidth,
                    meshHeight = meshHeight,
                    colors = currentColors,
                    average = currentAverage[0],
                    phase = phase[0],
                    energy = energy,
                    beatPulse = beatPulse,
                    reactive = reactive,
                    saturation = saturation,
                    brightness = brightness,
                    centersX = centersX,
                    centersY = centersY,
                )
                if (!bitmap.isRecycled) {
                    bitmap.setPixels(pixels, 0, meshWidth, 0, 0, meshWidth, meshHeight)
                }
            }
            meshImage = meshImages[writeIndex]
            writeIndex = 1 - writeIndex
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawImage(
            image = meshImage,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )

        // MeloX keeps the lower control region darker for white text/controls.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.04f),
                    0.52f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Black.copy(alpha = 0.48f),
                ),
            ),
        )
    }
}

private fun lerpColor(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

/** Constant angular speed (radians per 60 Hz frame) when no beat analysis exists. */
private const val ConstantFlowMotion = .032f

/**
 * 全屏背景高斯模糊的降采样倍率（LOD）。
 *
 * 背景模糊的离屏表面原来等于整块屏幕（本机 1440×3168 ≈ 4.56M px），半径 38dp @640dpi
 * 对应 sigma=152px —— 这是播放页 / 歌词页 RenderThread Drawing 段最贵的一笔。
 *
 * 高斯的**尺度等变**允许「降采样 → 小半径模糊 → 放大」：`blur_r(I)` 与
 * `upsample_s(blur_{r/s}(downsample_s(I)))` 在 s ≪ r 时肉眼不可分辨。背景本就糊成色块，
 * 细节不存在 ⇒ LOD=4 时像素量降到 1/16、半径降到 1/4，采样量按面积×半径算降约两个数量级。
 *
 * ⚠ 视觉安全边界：LOD 只有在 `r/s` 仍远大于 1 时成立。最薄的用例是歌词页背景的
 *   17.9dp@High（≈72px sigma，LOD=4 ⇒ 18px）—— 仍远大于 1，安全。
 * ⚠ 想加大到 8 之前先拿诊断包 A/B：`8 → 8px sigma` 已接近可见的方块化。
 */
private const val BackdropBlurLod = 4

/**
 * 实际生效的 LOD 倍率：API < 31 时 `Modifier.blur` 是空操作，降采样只会变成马赛克，
 * 因此直接退化成 1（保持原路径）。
 */
private val backdropBlurLod: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BackdropBlurLod else 1

/**
 * 让**内部带 `Modifier.blur` 的子树**只在 1/[backdropBlurLod] 的尺寸上测量 / 光栅化，
 * 再用一个纯缩放层放大回原来的布局尺寸。
 *
 * 为什么不是 `graphicsLayer { scaleX = 4f }`：那是绘制期矩阵，节点仍按原尺寸光栅化，
 * 模糊照样跑在全屏像素上，一分不省。真正省钱的只有「**测量**出来就是小尺寸」。
 *
 * 为什么不是 `BoxWithConstraints`：子组合要等布局阶段才发生，背景会晚一帧出现
 * （打开播放页时闪一下空底）。测量期修饰符是同步组合的，没有这个代价。
 *
 * ⚠ 本修饰符必须挂在**模糊的外面**（`Modifier.fillMaxSize().backdropBlurLod(){ 内容 .blur(r) }`），
 *   写反了模糊就还在全尺寸上做。
 * ⚠ 需求无界约束（如放进可滚动容器）时直接退化成普通测量，避免除零 / 异常。
 */
private fun Modifier.backdropBlurLod(): Modifier {
    val lod = backdropBlurLod
    if (lod <= 1) return this
    return layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val width = (constraints.maxWidth / lod).coerceAtLeast(1)
            val height = (constraints.maxHeight / lod).coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = lod.toFloat()
                    scaleY = lod.toFloat()
                    // 从左上角放大，才能正好铺回原来的矩形。
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/**
 * 运动期放宽后的帧间隔**下限**（66ms ≈ 15fps）。
 *
 * 只有在用户把设置调高时才会碰到它：60fps 的 `frameDelayMs * 2` 只有 33ms，被抬到这里。
 * 换句话说它是「高帧率设置的收益上限点」，对默认 24fps 不起作用。
 */
private const val ReliefMinDelayMs = 66L

/**
 * 运动期放宽后的帧间隔**上限**（84ms ≈ 12fps）。
 *
 * ⚠ 这是**观感门槛**，不是性能门槛。网格是缓慢漂移的大色块，12fps 与 15fps 在这个位移量下
 * 不可辨（15fps 已获真机接受）；100ms(10fps) / 125ms(8fps) 收益更大但从未实测，
 * 改这里之前先拿诊断包 A/B 一次，别凭感觉往上推。
 */
private const val ReliefMaxDelayMs = 84L

/**
 * Phase wrap point. Every time-varying term below multiplies the phase by a
 * multiple of a quarter turn, so wrapping at four full turns (8π) is exact and
 * the warp stays continuous instead of jumping when the phase rolls over.
 */
private const val FlowPhasePeriod = 25.132742f

/**
 * Base amplitude of the domain warp, in normalised screen units. The warp
 * displaces the sampling coordinate so the whole colour field advects, which
 * is what the previous symmetric radial fields could not do.
 */
private const val FlowWarpStrength = .15f

/**
 * Per-source travel frequencies. All are multiples of a quarter turn so the
 * 8π phase wrap in [fillFlowingMeshPixels] stays continuous. Different values
 * keep the nine colours from sweeping the field in lockstep.
 */
private val RoamFrequencyX = floatArrayOf(.50f, .25f, .75f, .50f, .25f, .75f, .50f, .25f, .75f)
private val RoamFrequencyY = floatArrayOf(.25f, .75f, .50f, .75f, .50f, .25f, .50f, .25f, .75f)

private fun fillFlowingMeshPixels(
    pixels: IntArray,
    meshWidth: Int,
    meshHeight: Int,
    colors: List<Color>,
    average: Color,
    phase: Float,
    energy: Float,
    beatPulse: Float,
    reactive: Boolean,
    saturation: Float,
    brightness: Float,
    centersX: FloatArray,
    centersY: FloatArray,
) {
    // Only a real beat analysis is allowed to shape the flow. With no analysis
    // the warp and the source orbit keep a fixed size while `phase` advances at
    // a constant speed, so the structure stays steady. Brightness is never
    // modulated here: only the field moves.
    val drive = if (reactive) 1f else 0f
    val energyDrive = energy.coerceIn(0f, 1f) * drive
    val beatDrive = beatPulse * drive
    // Kernels are sharper than before so the nine artwork colours actually read
    // as regions; a near-uniform field would swallow any warp.
    val radiusNormalized = (0.45f + energyDrive * .07f + beatDrive * .03f).coerceAtLeast(.01f)
    // Each colour source roams across the whole background on its own slow path
    // instead of orbiting its artwork cell, so the palette keeps visiting new
    // places. Frequencies are quarter-turn multiples so the 8π phase wrap stays
    // seamless (see FlowPhasePeriod).
    for (index in colors.indices) {
        val freqX = RoamFrequencyX[index]
        val freqY = RoamFrequencyY[index]
        centersX[index] = .5f + .3f * sin(phase * freqX + index * 1.7f)
        centersY[index] = .5f + .3f * cos(phase * freqY + index * .9f)
    }
    val maxDimension = maxOf(meshWidth, meshHeight).toFloat()
    val widthScale = meshWidth / maxDimension
    val heightScale = meshHeight / maxDimension
    val baseWeight = .16f
    val warp = FlowWarpStrength + energyDrive * .05f + beatDrive * .02f
    val t = phase
    var pixelIndex = 0
    for (yIndex in 0 until meshHeight) {
        val v = yIndex.toFloat() / (meshHeight - 1).coerceAtLeast(1).toFloat()
        for (xIndex in 0 until meshWidth) {
            val u = xIndex.toFloat() / (meshWidth - 1).coerceAtLeast(1).toFloat()
            // Two octaves of a slow curl-like field. Advancing `t` advects the
            // field, so the colours visibly flow instead of breathing in place.
            // Time multipliers are kept to multiples of a quarter turn so the
            // 8π phase wrap above is seamless (see FlowPhasePeriod).
            val warpX = sin(v * 3.1f + t) * cos(u * 2.3f - t * .75f) +
                .5f * sin((u + v) * 4.9f - t * 1.25f)
            val warpY = cos(u * 2.9f - t) * sin(v * 2.6f + t * .5f) +
                .5f * cos((u - v) * 5.4f + t)
            var sampleU = u + warp * warpX
            var sampleV = v + warp * warpY
            // A finer second pass gives the edges a marbled rather than a rigid
            // look.
            sampleU += warp * .28f * sin(sampleV * 6.7f - t * 1.5f)
            sampleV += warp * .28f * cos(sampleU * 7.1f + t * 1.5f)
            var totalWeight = baseWeight
            var red = average.red * baseWeight
            var green = average.green * baseWeight
            var blue = average.blue * baseWeight
            for (colorIndex in colors.indices) {
                val dx = (sampleU - centersX[colorIndex]) * widthScale / radiusNormalized
                val dy = (sampleV - centersY[colorIndex]) * heightScale / radiusNormalized
                val distanceSquared = dx * dx + dy * dy
                val falloff = 1f / (1f + distanceSquared * 4.5f)
                val weight = falloff * falloff
                val color = colors[colorIndex]
                totalWeight += weight
                red += color.red * weight
                green += color.green * weight
                blue += color.blue * weight
            }
            var r = red / totalWeight
            var g = green / totalWeight
            var b = blue / totalWeight
            // User colour controls. Intensity scales the chroma around the
            // pixel's own luminance so it never changes brightness; brightness
            // is the separate, explicit multiplier.
            if (saturation != 1f) {
                val luminance = r * .2126f + g * .7152f + b * .0722f
                r = luminance + (r - luminance) * saturation
                g = luminance + (g - luminance) * saturation
                b = luminance + (b - luminance) * saturation
            }
            if (brightness != 1f) {
                r *= brightness
                g *= brightness
                b *= brightness
            }
            pixels[pixelIndex++] = (0xFF shl 24) or
                ((r.coerceIn(0f, 1f) * 255f).toInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255f).toInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255f).toInt()
        }
    }
}
