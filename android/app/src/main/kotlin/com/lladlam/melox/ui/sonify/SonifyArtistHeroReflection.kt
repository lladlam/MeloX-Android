package com.lladlam.melox.ui.sonify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.os.Build
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 艺人页 Hero 倒影配方（头像→提取色的纵向过渡）：
 *
 * 取照片**最底部 [ArtistReflectionSourceFraction]（1/4）**那一条，关于照片底边翻转后
 * 纵向拉伸 [ArtistReflectionStretch] 倍（0.7 / 0.25 = 2.8:1）铺满整条带。接缝两侧同为
 * 照片底边那一行像素，无缝；横向保持 1:1，只有纵向被拉长。
 *
 * 为什么拉伸而不是 1:1 等比镜像：等比镜像等于在带内把照片下半部分原样再画一遍，
 * 人脸/logo 轮廓完整重复，模糊只把它糊成重影（观感「脏」）。纵向拉伸后轮廓被拖长成
 * 柔软色带，既保留照片的延续感，又不会读出「第二张脸」。
 *
 * 模糊引擎与顶栏同源：[ArtistHeroReflectionShader] 即 [yos.music.player.ui.widgets.basic]
 * TitleBarProgressiveBlur 的 32 点黄金角抖动多重采样 AGSL 渐进模糊（半径沿 y 以 t² 递增），
 * 经 RenderNode + RenderEffect + HardwareRenderer 在后台线程一次性烘焙成位图——
 * 滚动期间只做位图平移，零逐帧着色器成本（实时 RenderEffect 层每帧重执行着色器
 * 是此前滑动卡顿的根源）。
 *
 * 渐隐（alpha 溶入提取色）与模糊是**两条独立管线**：模糊在着色器里按半径公式算，
 * 渐隐在烘焙后用 DST_IN 打在位图 alpha 上，起点/幂次各自可调（见
 * [ArtistReflectionFadeStartFraction] / [ArtistReflectionFadePower]）。
 *
 * API < 33（无 AGSL）降级为金字塔降采样-回放分条模糊（[bakeHeroReflection] 内分支）。
 */
/**
 * 倒影带高占照片高的比例（0.7 = 照片高的七成）。
 *
 * v15：0.5 → 0.7。带子是渐进模糊的**下半程**，加长它才让「越来越糊」的路径真的走得更远；
 * 只抬起点不加长，等于把同一条曲线压进更短的行程，末端反而更容易看出截止。
 */
const val ArtistReflectionFraction = 0.7f

/**
 * 倒影截取的源高度占照片高的比例：取照片**最底部 1/4**。
 */
const val ArtistReflectionSourceFraction = 0.25f

/**
 * 倒影纵向拉伸倍率 = 带高 / 源高 = 0.7 / 0.25 = 2.8，即把底部 1/4 拉成 2.8:1。
 * 由 [ArtistReflectionFraction] 与 [ArtistReflectionSourceFraction] 相除得出，不单独配置。
 *
 * 拉伸越高，照片底缘的细节被拖得越薄（观感越「柔」而非「重影」）；这也是带子加长后
 * 不需要另加额外柔化就仍然好看的原因。
 */
val ArtistReflectionStretch: Float
    get() = ArtistReflectionFraction / ArtistReflectionSourceFraction

/**
 * 模糊起点：相对**照片高度**的比例（不是带高）。**只影响模糊**，不参与渐隐。
 *
 * 0.70 = 照片下 30% 起雾，往上完全锐利。
 *
 * v15：0（起点恰在照片底边）→ 0.70，即「往上延伸」。刻意按照片高而非带高定义：
 * 按带高定义时，带子一变长（见 [ArtistReflectionFraction]）起点就跟着往照片里钻
 * （0.6 倍带高在 0.7 带高上 = 42% 照片高，会糊到五官），两个参数互相耦合。
 * 锁在照片比例上，调带高不会移动这条边界。
 */
const val ArtistReflectionBlurStartPhotoFraction = 0.70f

/**
 * 模糊起点的像素坐标（layer 内 y 轴）。
 *
 * 起点**按照片高**定位而不是按带高：带子长度是另一个可调参数，若起点也写成带高的倍数，
 * 每次调带高都会连带把模糊往照片里推/往外拉（v15 调带高时踩过：同一份 0.6 倍带高，
 * 在 0.7 带上等于深入照片 42%，直接糊到五官）。所有着色器 uniform 都从这里取，别各写一份。
 */
internal fun artistReflectionBlurStartPx(photoHeightPx: Int): Float =
    photoHeightPx * ArtistReflectionBlurStartPhotoFraction


/**
 * 渐隐上边界（100% 不透明处）：在照片底边**之上**、占倒影带高的比例。
 * 只影响 alpha 渐隐，与模糊解耦。0.5 时深入 hero 图下半部。
 */
const val ArtistReflectionFadeStartFraction = 0.5f

/**
 * 渐隐下边界（0% 透明处）：在照片底边**之下**、占倒影高的比例。只影响 alpha 渐隐。
 *
 * 0.24 对应该页「三圆钮行」播放键的中心（行上内边距 14dp + 按钮半径 32dp = 底边下 46dp，
 * 带高约 192dp → 46/192 ≈ 0.24）。即渐变到播放键中心处已完全透明，再往下是纯底色，
 * 播放键以下的倒影不再透出任何照片内容。
 *
 * v15 带高 0.5→0.7 后这个比例不变、绝对位置随带等比拉长：曲线形状（照片底边处 alpha
 * 仍 ≈ 0.54）与「起点在照片内 1/4 带高」都保持原样，只是整条渐隐也被拉长 1.4 倍——
 * 这正是「路径更长」要的效果。带尾落在照片底边下 ≈64dp（播放键竖向范围 14..78dp 之内），
 * 仍然在按钮里收净，不拖进「歌曲」区。
 */
const val ArtistReflectionFadeEndFraction = 0.24f

/**
 * 渐隐曲线幂次：`alpha = 1 − t^power`。**先慢后快**（照片尽量保持清晰，临近末尾才快速消隐）。
 *
 * 2.0 时前 1/4 只掉 6%（0.94）、后 1/4 掉 44%，即「慢慢开始、突然收尾」。
 * 1.0 是线性（各段等速），< 1 则是先快后慢——都不是这里要的观感。
 */
const val ArtistReflectionFadePower = 2f

/**
 * 倒影带底部（最糊处）的模糊半径，烘焙时换算成像素。
 *
 * 60 → 96（v12）：原值下带子中段只有约 15dp 半径（t² 增长前段太缓），观感「几乎没糊」。
 * 96 → 120（v15）：模糊路径从「照片底边→带底」（0.5 带高 = 192dp）拉长到
 * 照片下 30% + 整条 0.7 带（典型手机 384dp，正好翻倍），同一位置算出的 t 变小、
 * 半径随之变淡；上限提到 120dp 把这份稀释补回来，带底仍是「接近整条带高」的重糊。
 */
const val ArtistReflectionMaxBlurDp = 120f

/**
 * 模糊半径沿带的增长幂次：`radius = maxRadius · t^power`。
 *
 * 2.0（原值）前段增长极缓：t=0.5 时只有 25% 半径，整条带上半段几乎不糊。
 * 1.35（v12）t=0.5 有 39%、t=0.25 有 16%，前段就能看出模糊。
 * 1.1（v15）t=0.5 有 47%、t=0.25 有 22%：路径拉长后必须再放缓，否则中段仍是「微微发虚」，
 * 面积变大但看不出「越来越糊」。
 */
const val ArtistReflectionBlurPower = 1.1f

/**
 * 烘焙配方版本，必须拼进调用侧的缓存键（[yos.music.player.code.utils.artist.HeroBakeCache]）。
 * 改动着色器/渐隐/几何公式后 +1：磁盘缓存的键不含任何算法指纹，不换版会让旧的
 * 已烘焙 PNG 被无限期复用，等于这次修复在回访用户身上不可见。
 * v2：带内渐隐从着色器移到读回后的 DST_IN（硬件路径输出不透明，旧图带尾为实色）。
 * v3：渐隐与渐进模糊对齐到同一条路径（原先是带内 1→0.2 再 0.2→0 的两段式）。
 * v4：倒影改纵向拉伸（旧版为 1:1 等比镜像/平板薄切片），过渡起点移到照片底边附近。
 * v5：拉伸倍率改为自接缝 1:1 渐变到带底 2 倍（统一倍率会在接缝处留斜率折痕）。
 * v6：加大渐隐力度与长度——带高 0.5→0.68、渐隐幂次 t²→t^1.35。
 * v7：渐隐起点与模糊起点解耦，起点抬高到照片约 71% 处、幂次降到 0.9，带高 0.68→0.9。
 * v8：倒影改为截取照片底部 1/4 拉伸成 2:1（带高回到 0.5），删除分条绘制（条界锯齿）。
 * v9：渐隐改为线性（power=1）、上边界吃进 hero 内部 0.07 倍带高。
 * v10：渐隐两端整体上移——上边界 0.07→0.5（深入 hero 下半部），下边界改为可调 0.62。
 * v11：下边界抬到三圆钮行播放键中心（0.24），曲线改 t²「先慢后快」。
 * v12：加大渐进模糊——maxBlur 60→96dp、半径增长幂次 t²→t^1.35（前段就可见模糊）。
 * v13：倒影切片改「向内取整」，修掉接缝下方那条 1px 拉伸成的色差窄带（缝隙）。
 * v14：渐隐矩形画满到带底、靠 CLAMP 归零，删掉 CLEAR 补尾——原写法渐变（带 AA）
 *      与 CLEAR（无 AA）在 end 的小数边界上覆盖不一致，留下一条残留行（缝隙）。
 * v15：渐进模糊整体加长加大——起点按照片高抬到 0.70（照片下 30% 起雾）、带高 0.5→0.7、
 *      maxBlur 96→120dp、半径幂次 1.35→1.1（路径变长后补回中段浓度）。
 */
const val ArtistHeroBakeRecipeVersion = 15

/** 金字塔降级模糊的分条数（仅 API < 33 回退分支使用）。 */
private const val FallbackStrips = 32

/**
 * 32 点黄金角抖动多重采样渐进模糊（移植自 TitleBarProgressiveBlur，去掉
 * tint/presence）。半径自 blurStart（照片下缘附近）向带底以 t² 递增：接缝处近似 0。
 * 采样坐标 clamp 在图层内：输入出界是透明像素，不 clamp 会在边缘出现黑边。
 *
 * 带内内容由 [drawPhotoAndStretchedReflection] 预绘进图层：照片关于底边翻转后纵向
 * 拉伸铺满整条带，源 y=照片底边映射到底边本身，故接缝两侧同像素、模糊跨缝采样也连续。
 * 着色器因此只有一种模式，只需按图层边界 clamp。
 * 渐隐**不在着色器里做**：RenderEffect 的输出会合成进 ImageReader 的不透明硬件缓冲，
 * alpha 被固化成 255，带尾变成不透明实色（就是一条与提取色硬分割的纯色带）。
 * 着色器只负责采样与模糊，渐隐在 [applyReflectionFade] 里用 DST_IN 打在
 * 读回后的位图上，且与这里 blurStart → bandBottom 的路径逐点对齐。
 */
const val ArtistHeroReflectionShader = """
uniform shader content;
uniform float2 layerSize;
uniform float photoBottom;
uniform float bandBottom;
uniform float blurStart;
uniform float maxRadius;
uniform float blurPower;

float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 coord) {
    float2 bounds = max(layerSize - 1.0, float2(0.0));
    float span = max(bandBottom - blurStart, 1.0);
    float t = clamp((coord.y - blurStart) / span, 0.0, 1.0);
    float radius = maxRadius * pow(t, blurPower);
    half4 color;
    if (radius < 0.5) {
        color = content.eval(clamp(coord, float2(0.0), bounds));
    } else {
        float h = hash12(coord);
        float2 dir = float2(cos(h * 6.2831853), sin(h * 6.2831853));
        float2 g = float2(cos(2.39996323), sin(2.39996323));
        half4 sum = half4(0.0);
        float wsum = 0.0;
        for (int i = 0; i < 32; i++) {
            float fi = float(i);
            float ff = (fi + 0.5) / 32.0;
            float r = radius * sqrt(ff);
            r *= 0.90 + 0.20 * fract(h * 93.9898 + fi * 0.7548776662);
            float2 o = dir * r;
            float w = exp(-ff / 0.85);
            half4 c = content.eval(clamp(coord + o, float2(0.0), bounds));
            sum += c * w;
            wsum += w;
            dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
        }
        color = wsum < 0.0001 ? content.eval(clamp(coord, float2(0.0), bounds)) : sum / wsum;
    }
    return color;
}
"""

/**
 * 去噪第二道（与 TitleBarProgressiveBlur 同款）：大半径下 32 采样会出现抖动细噪，
 * 8 点小半径盒式平均压噪，半径与局部模糊强度成正比。
 */
const val ArtistHeroDenoiseShader = """
uniform shader content;
uniform float2 layerSize;
uniform float photoBottom;
uniform float blurStart;
uniform float bandBottom;
uniform float maxRadius;
uniform float blurPower;

half4 main(float2 coord) {
    float2 bounds = max(layerSize - 1.0, float2(0.0));
    float span = max(bandBottom - blurStart, 1.0);
    float t = clamp((coord.y - blurStart) / span, 0.0, 1.0);
    float r = maxRadius * pow(t, blurPower) * 0.18;
    float2 c0 = clamp(coord, float2(0.0), bounds);
    if (r < 0.4) {
        return content.eval(c0);
    }
    half4 sum = content.eval(c0);
    float wsum = 1.0;
    float2 dir = float2(1.0, 0.0);
    float2 g = float2(cos(0.7853981634), sin(0.7853981634));
    for (int i = 0; i < 8; i++) {
        float2 sc = clamp(coord + dir * r, float2(0.0), bounds);
        sum += content.eval(sc);
        wsum += 1.0;
        dir = float2(dir.x * g.x - dir.y * g.y, dir.x * g.y + dir.y * g.x);
    }
    return sum / wsum;
}
"""

/**
 * 画照片 + 拉伸倒影。
 *
 * 照片按 Crop 等比铺满 [photoHeightPx]，并裁剪在照片区内（dst 矩形可能比盒子高，
 * 不裁会溢出到倒影带里）。
 *
 * 倒影：取照片**最底部 [ArtistReflectionSourceFraction]（1/4）**那一条，关于照片底边
 * 翻转后纵向拉伸铺满整条带（带高 = 照片高 × [ArtistReflectionFraction]，即 2:1）。
 * 接缝处两侧同为照片底边那一行像素，无缝；统一倍率下一次 canvas 变换即可画完，
 * 不做分条——分条会在条界留下量化台阶（观感即锯齿）。
 */
private fun drawPhotoAndStretchedReflection(
    canvas: Canvas,
    src: Bitmap,
    widthPx: Int,
    photoHeightPx: Int,
    bandHeightPx: Int
) {
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val scale = maxOf(widthPx.toFloat() / src.width, photoHeightPx.toFloat() / src.height)
    val dw = src.width * scale
    val dh = src.height * scale
    val left = (widthPx - dw) / 2f
    val top = (photoHeightPx - dh) / 2f
    val dst = android.graphics.RectF(left, top, left + dw, top + dh)
    // 照片只画在自己的区域内：Crop 后 dst 高可能超过照片区，不裁会溢出到倒影带里
    canvas.save()
    canvas.clipRect(0f, 0f, widthPx.toFloat(), photoHeightPx.toFloat())
    canvas.drawBitmap(src, null, dst, paint)
    canvas.restore()
    if (bandHeightPx <= 0) return

    // 倒影切片必须取自**照片实际可见的底边**。照片按 Crop 等比铺满，故可见底边恒为
    // photoHeightPx（clipRect 已保证画面被裁到那里）。源区间用「向外取整」会多带入
    // 约 1px 照片区之外的源内容，这 1px 被纵向拉伸 2 倍后正好在接缝下方形成一条
    // 颜色不同的窄带（用户实锤的「缝隙」）。故改用「向内取整」：下界 floor、上界 ceil，
    // 宁可少 1px 也不能多——少的那点由相邻条像素自然衔接，不会显色差。
    val sliceH = photoHeightPx * ArtistReflectionSourceFraction
    val sliceTopY = photoHeightPx - sliceH
    val srcTopF = ((sliceTopY - top) / dh * src.height).coerceIn(0f, src.height.toFloat())
    val srcBottomF = ((photoHeightPx - top) / dh * src.height).coerceIn(0f, src.height.toFloat())
    val srcRect = android.graphics.Rect(
        0, ceil(srcTopF).toInt().coerceIn(0, src.height - 1),
        src.width, floor(srcBottomF).toInt().coerceIn(1, src.height)
    )
    if (srcRect.height() <= 0) return
    // 单次绘制：源切片 → 带区。关于接缝翻转（y' = 2·photoH − y），故翻转前的目标矩形
    // 取 [photoH−band, photoH]，翻转后正好落在 [photoH, photoH+band]。
    // 横向仍按 dst 宽度铺满，故是「横向 1:1、纵向 band/slice = 2:1」的非等比拉伸。
    // 目标矩形横向用整幅宽度（0..widthPx）而不是 dst 宽度：dst 比盒子窄时，
    // 用 dst 宽度会在两侧留出未绘制的竖条，与照片区的裁切边缘不一致。
    canvas.save()
    canvas.translate(0f, 2f * photoHeightPx)
    canvas.scale(1f, -1f)
    canvas.drawBitmap(
        src, srcRect,
        android.graphics.RectF(
            0f, (photoHeightPx - bandHeightPx).toFloat(),
            widthPx.toFloat(), photoHeightPx.toFloat()
        ),
        paint
    )
    canvas.restore()
}

/**
 * 烘焙「照片 + 纵向拉伸倒影」位图（宽 [widthPx]，上部 [photoHeightPx] 照片区、
 * 下部 [bandHeightPx] 倒影带）。[shader] 非 null 且 API≥33 走 AGSL 真渐进模糊
 * （HardwareRenderer 离屏渲染，质量与顶栏同源）；否则退化为金字塔降采样-回放
 * 分条模糊（近似质量，仅低版本回退）。必须在后台线程调用。
 *
 * 带内渐隐统一在模糊之后由 [applyReflectionFade] 打在位图 alpha 上——硬件路径的
 * RenderEffect 输出只有不透明缓冲，着色器里的 alpha 会被丢弃（见 [ArtistHeroReflectionShader]）。
 */
fun bakeHeroReflection(
    src: Bitmap,
    widthPx: Int,
    photoHeightPx: Int,
    bandHeightPx: Int,
    maxBlurRadiusPx: Float,
    shader: RuntimeShader?
): Bitmap {
    if (shader != null && Build.VERSION.SDK_INT >= 33) {
        // 渐隐失败（硬件位图无法复制）时整条硬件结果作废，回落到能保留 alpha 的软件路径
        val faded = runCatching {
            val hardware = bakeWithHardwareRenderer(src, widthPx, photoHeightPx, bandHeightPx, maxBlurRadiusPx, shader)
            applyReflectionFade(hardware, photoHeightPx, bandHeightPx)
        }.getOrNull()
        if (faded != null) return faded
        android.util.Log.w("ArtistDetail", "hero reflection: hardware bake/fade failed, falling back to pyramid blur")
    }
    val pyramid = bakeWithPyramidBlur(src, widthPx, photoHeightPx, bandHeightPx, maxBlurRadiusPx)
    return applyReflectionFade(pyramid, photoHeightPx, bandHeightPx) ?: pyramid
}

/**
 * 带内渐隐：上下边界分别由 [ArtistReflectionFadeStartFraction]（照片底边之上）与
 * [ArtistReflectionFadeEndFraction]（照片底边之下）控制，中间按
 * [ArtistReflectionFadePower] 过渡（1.0 = 线性）。
 *
 * 两端都能独立抬高/下压，故可整体上移整条渐变、也可单独收窄跨度——渐变不必被拉满
 * 整条倒影带，落差可以集中在有真实颜色差的一段上。
 *
 * 与模糊解耦：模糊起点由 [ArtistReflectionBlurStartPhotoFraction] 单独控制，调渐隐不会
 * 连带改动照片下缘的模糊程度。
 *
 * 用 DST_IN 直接打在位图 alpha 上（预乘语义下 SrcOver 到页面底色恰好是
 * 「照片色 → 提取色」的线性过渡）。位图不可变时先复制一份再打；复制不出来返回 null 让调用方回落。
 */
private fun applyReflectionFade(source: Bitmap, photoHeightPx: Int, bandHeightPx: Int): Bitmap? {
    val total = photoHeightPx + bandHeightPx
    if (bandHeightPx <= 0 || photoHeightPx <= 0) return source
    val out = if (source.isMutable) source else {
        val copy = runCatching { source.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull()
            ?: return null
        runCatching { source.recycle() }
        copy
    }
    // 上边界在照片底边之上（吃进 hero），下边界在照片底边之下（带内），各自独立可调
    val start = photoHeightPx - bandHeightPx * ArtistReflectionFadeStartFraction
    val end = (photoHeightPx + bandHeightPx * ArtistReflectionFadeEndFraction)
        .coerceIn(start + 1f, total.toFloat())
    // alpha = 1 − t^power 曲线按 16 段折线近似：power=1（线性）时折线即精确直线
    val steps = 16
    val colors = IntArray(steps + 1)
    val positions = FloatArray(steps + 1)
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val a = ((1f - t.pow(ArtistReflectionFadePower)) * 255f).roundToInt().coerceIn(0, 255)
        colors[i] = (a shl 24) or 0x00FFFFFF
        positions[i] = t
    }
    val canvas = Canvas(out)
    val fade = Paint(Paint.ANTI_ALIAS_FLAG)
    fade.shader = android.graphics.LinearGradient(
        0f, start, 0f, end,
        colors, positions,
        android.graphics.Shader.TileMode.CLAMP
    )
    fade.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    // 渐变矩形**画满到带底**，不画到 end 就停：positions 末位 1.0 对应 alpha=0，
    // CLAMP 会把该值一直延续到 total，故 end 以下天然全透明，不需要另画 CLEAR 补尾。
    //
    // 曾经的写法是「渐变画到 end + 另用无 AA 的 Paint 从 end 起 CLEAR」，两者在
    // end 这个小数坐标上抗锯齿不一致：渐变（带 AA）只覆盖了 floor(end) 那一行的
    // 76%，留下约 20% 不透明度的倒影内容；CLEAR（无 AA）按整数取整从 ceil(end) 才开始，
    // 那一行谁都没管到 → 屏幕上一条随 FadeEndFraction 平移的缝隙。画满整幅后
    // 不存在第二个矩形，也就不存在交接行。
    canvas.drawRect(0f, start, out.width.toFloat(), total.toFloat(), fade)
    fade.xfermode = null
    fade.shader = null
    return out
}

/**
 * AGSL 渐进模糊着色器实例（每次烘焙新建，uniform 按几何赋值）。
 */
fun newArtistHeroReflectionShader(
    widthPx: Int,
    photoHeightPx: Int,
    bandHeightPx: Int,
    maxBlurRadiusPx: Float
): RuntimeShader? {
    if (Build.VERSION.SDK_INT < 33) return null
    return runCatching {
        val blurStart = artistReflectionBlurStartPx(photoHeightPx)
        RuntimeShader(ArtistHeroReflectionShader).apply {
            setFloatUniform("layerSize", widthPx.toFloat(), (photoHeightPx + bandHeightPx).toFloat())
            setFloatUniform("photoBottom", photoHeightPx.toFloat())
            setFloatUniform("bandBottom", (photoHeightPx + bandHeightPx).toFloat())
            setFloatUniform("blurStart", blurStart)
            setFloatUniform("maxRadius", maxBlurRadiusPx)
            setFloatUniform("blurPower", ArtistReflectionBlurPower)
        }
    }.getOrNull()
}

/**
 * RenderNode 录制「照片+拉伸倒影」→ 挂 AGSL RenderEffect → HardwareRenderer 渲进
 * ImageReader 的硬件缓冲 → 回读成可变软件位图。整条链路与系统模糊材质同一实现，
 * RenderEffect 真实作用于像素内容（与 Paint.maskFilter 的掩码语义完全不同）。
 *
 * 返回软件位图而非硬件位图：着色器算出的 alpha 在合成进不透明缓冲时会被丢弃，
 * 所以渐隐必须由调用方在位图 alpha 上补（见 [applyReflectionFade]），而硬件位图不可写。
 */
private fun bakeWithHardwareRenderer(
    src: Bitmap,
    widthPx: Int,
    photoHeightPx: Int,
    bandHeightPx: Int,
    maxBlurRadiusPx: Float,
    shader: RuntimeShader
): Bitmap {
    val total = photoHeightPx + bandHeightPx
    val node = RenderNode("artistHeroBake").apply { setPosition(0, 0, widthPx, total) }
    val canvas = node.beginRecording(widthPx, total)
    drawPhotoAndStretchedReflection(canvas, src, widthPx, photoHeightPx, bandHeightPx)
    node.endRecording()
    // 两道链式效果：32 采样渐进散焦 → 8 点去噪（大半径下抖动细噪必须压，
    // 与 TitleBarProgressiveBlur 的双 shader 管线同构）
    val blurEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val denoise = RuntimeShader(ArtistHeroDenoiseShader).apply {
        setFloatUniform("layerSize", widthPx.toFloat(), total.toFloat())
        setFloatUniform("photoBottom", photoHeightPx.toFloat())
        setFloatUniform("blurStart", artistReflectionBlurStartPx(photoHeightPx))
        setFloatUniform("bandBottom", total.toFloat())
        setFloatUniform("maxRadius", maxBlurRadiusPx)
        setFloatUniform("blurPower", ArtistReflectionBlurPower)
    }
    val denoiseEffect = RenderEffect.createRuntimeShaderEffect(denoise, "content")
    node.setRenderEffect(RenderEffect.createChainEffect(blurEffect, denoiseEffect))
    val renderer = HardwareRenderer()
    val reader = android.media.ImageReader.newInstance(
        widthPx, total, PixelFormat.RGBA_8888, 1,
        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
    )
    try {
        renderer.setSurface(reader.surface)
        renderer.setContentRoot(node)
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        var image: android.media.Image? = null
        // 死线放宽到 3s：GPU 繁忙（转场/滚动）时 acquireLatestImage 偶尔要等，1s 过紧会让
        // 烘焙概率性失败（调用方 getOrNull → 无倒影硬衔接）。正常情况 syncAndDraw 后即取到。
        val deadline = android.os.SystemClock.uptimeMillis() + 3000
        while (image == null && android.os.SystemClock.uptimeMillis() < deadline) {
            image = reader.acquireLatestImage()
            if (image == null) Thread.sleep(4)
        }
        val hardwareBuffer = image?.hardwareBuffer
            ?: throw IllegalStateException("hero bake: no image from HardwareRenderer")
        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, ColorSpace.get(ColorSpace.Named.SRGB))
            ?: throw IllegalStateException("hero bake: wrapHardwareBuffer failed")
        // 必须在 image.close() 之前回读成可变软件位图：后续 DST_IN 渐隐要写入 alpha，
        // 且硬件缓冲释放后再复制会失败。复制只做一次（这里），applyReflectionFade 原地打。
        val software = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("hero bake: copy to ARGB_8888 failed")
        bitmap.recycle()
        image.close()
        return software
    } finally {
        renderer.setSurface(null)
        renderer.destroy()
        reader.close()
    }
}

/**
 * API < 33 回退：金字塔降采样-回放分条模糊（链式 2x 缩小 + 双线性放大回原尺寸，
 * 级 ≈ log2(r)）。注意 Paint.maskFilter(BlurMaskFilter) 只模糊图元边缘掩码、
 * 不模糊位图内容，不可用作内容模糊。
 */
private fun bakeWithPyramidBlur(
    src: Bitmap,
    widthPx: Int,
    photoHeightPx: Int,
    bandHeightPx: Int,
    maxBlurRadiusPx: Float
): Bitmap {
    val total = photoHeightPx + bandHeightPx
    val out = Bitmap.createBitmap(widthPx, total, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    drawPhotoAndStretchedReflection(canvas, src, widthPx, photoHeightPx, bandHeightPx)

    val blurStart = artistReflectionBlurStartPx(photoHeightPx)
    val span = total - blurStart
    val base = out.copy(Bitmap.Config.ARGB_8888, false)
    val maxLevel = 6
    // 折半链：downChain[k] 与旧实现「每级从 base 重新 createScaledBitmap 折半 k 次」逐像素相同
    // （同一条 w/2f→roundToInt 递推、同一 filter=true、同样的起点），故放大回全尺寸的结果不变，
    // 因此**不需要** bump ArtistHeroBakeRecipeVersion（否则所有用户缓存集体失效、重烘一次）。
    // 前提是条带循环里 level 单调不减（BlurPower > 0 保证 t 递增）；若改为负 power 或倒序条带，
    // downTo 会因回退到已回收的级而崩（不会静默出错），那时须保留全部折半级。
    val downChain = arrayOfNulls<Bitmap>(maxLevel + 1)
    downChain[0] = base
    var builtLevel = 0
    var bw = widthPx
    var bh = total
    fun downTo(level: Int): Bitmap {
        // 按需构建：条带半径随 y 单调递增，实际只会逐级向上推进，不会回退
        while (builtLevel < level) {
            bw = (bw / 2f).roundToInt().coerceAtLeast(1)
            bh = (bh / 2f).roundToInt().coerceAtLeast(1)
            builtLevel++
            // 尺寸到 1px 后 createScaledBitmap 可能返回源图本身（同尺寸不复制），
            // 此时 downChain 相邻两级持有同一实例——回收时靠 isShared 判重（见下方）。
            downChain[builtLevel] = Bitmap.createScaledBitmap(downChain[builtLevel - 1]!!, bw, bh, true)
        }
        return downChain[level]!!
    }

    // 全尺寸回放图只保留当前一级：旧实现把 7 张全尺寸位图（每张 ~6MB）全程驻留并在最后才
    // 回收，单次烘焙瞬时可达 54MB+。这里换级时立刻回收上一张，峰值只多 1 张 ≈ 6MB。
    // replayOwned 记录这张是不是独立分配：createScaledBitmap 在尺寸相同时**返回源对象本身**
    // （level 0 的回放图就是 base），此时不能回收，否则会回收掉 downChain[0]。
    var replay: Bitmap? = null
    var replayLevel = -1
    var replayOwned = false
    fun replayFor(level: Int): Bitmap {
        val current = replay
        if (current != null && replayLevel == level) return current
        if (current != null && replayOwned) current.recycle()
        val src = downTo(level)
        val next = Bitmap.createScaledBitmap(src, widthPx, total, true)
        replay = next
        replayLevel = level
        replayOwned = next !== src
        return next
    }

    val copyPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until FallbackStrips) {
        val y0 = blurStart + span * i / FallbackStrips
        val y1 = blurStart + span * (i + 1) / FallbackStrips
        val t = (((y0 + y1) / 2f - blurStart) / span).coerceIn(0f, 1f)
        val r = maxBlurRadiusPx * t.pow(ArtistReflectionBlurPower)
        val level = if (r < 1f) 0 else ceil(ln(r.toDouble()) / ln(2.0))
            .roundToInt().coerceIn(0, maxLevel)
        val pad = (1 shl level) * 2f
        val sy0 = (y0 - pad).coerceAtLeast(0f)
        val sy1 = (y1 + pad).coerceAtMost(total.toFloat())
        canvas.save()
        canvas.clipRect(0f, y0, widthPx.toFloat(), y1 + 1f)
        canvas.drawBitmap(
            replayFor(level),
            Rect(0, sy0.roundToInt(), widthPx, sy1.roundToInt()),
            android.graphics.RectF(0f, sy0, widthPx.toFloat(), sy1),
            copyPaint
        )
        canvas.restore()
    }
    // ★回收红线：createScaledBitmap 在目标尺寸与源相同时**返回源对象本身**（不复制），
    // 极端 / 退化几何（宽或高先折到 1px 后不再变化）会让 downChain 相邻级、乃至 replay
    // 与 base 持有同一实例；按引用逐个 recycle 会双重回收而崩。故用按身份去重的一次性收集，
    // 每个实例只回收一次。旧实现同样有此隐患（7 张全尺寸位图各自 recycle 也有同实例风险）。
    val released = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
    replay?.let { if (released.add(it)) it.recycle() }
    for (k in 1..maxLevel) downChain[k]?.let { if (released.add(it)) it.recycle() }
    if (released.add(base)) base.recycle()
    // 带内渐隐由 bakeHeroReflection 末尾统一施加（见 applyReflectionFade）
    return out
}
