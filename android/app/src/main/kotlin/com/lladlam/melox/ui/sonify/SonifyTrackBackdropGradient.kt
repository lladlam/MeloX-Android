package com.lladlam.melox.ui.sonify

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import me.tatarka.google.material.hct.Hct
import me.tatarka.google.material.palettes.TonalPalette
import me.tatarka.google.material.quantize.QuantizerCelebi
import me.tatarka.google.material.score.Score

/**
 * 封面取色后的整页柔和纵向渐变。
 *
 * 设计目标是**相邻色标几乎看不出分界**，但仍保留真实的纵向层次。所有可读性约束都在
 * [TrackGradientPalette.sample] 里一次算死，下游（页面底衬 / 顶栏 tint / 底栏遮罩）
 * 只消费结果，不再做二次判断——于是全局文字色恒为深/浅两套之一，永远不会出现
 * 「底色提亮了而文字没跟着翻」。
 *
 * [stops] 与 [positions] 一一对应；两个画笔构造期就建好，绘制期不再分配。
 */
@Immutable
class TrackBackdropGradient(
    val stops: List<Color>,
    val positions: List<Float>
) {
    val brush: Brush = Brush.verticalGradient(
        *Array(stops.size) { positions[it] to stops[it] }
    )

    /**
     * 供「3× 尺寸种子层」使用的画笔。
     *
     * 顶栏 backdrop 的种子层要画 3 倍尺寸（见 `rememberTitleBackdrop`），而录制图层
     * 会被裁到节点范围——于是可见区域只看到那 3 倍画布的**中间三分之一**。直接铺
     * [brush] 会让页面只显示渐变的中段（顶部不够亮、底部不够暗）。这里把整条渐变
     * 重映射到 1/3–2/3，上下各三分之一用首尾色平铺，可见区域才是完整的 0→1 渐变。
     */
    val tripleCanvasBrush: Brush = run {
        val points = ArrayList<Pair<Float, Color>>(stops.size + 4)
        points += 0f to top
        points += (1f / 3f) to top
        for (i in stops.indices) points += ((1f + positions[i]) / 3f) to stops[i]
        points += (2f / 3f) to bottom
        points += 1f to bottom
        Brush.verticalGradient(*points.toTypedArray())
    }

    /** 屏幕顶端色：顶栏渐进模糊的 tint 与 3× 采样区上缘填充色。 */
    val top: Color get() = stops.first()

    /** 屏幕底端色：底栏渐隐遮罩的默认色（不跟随会在底部出现一条白/黑色带）。 */
    val bottom: Color get() = stops.last()

    /** 全屏平均色：NavHost 交叉淡化期间的底衬色，避免转场中点透出主题白/黑。 */
    val average: Color
        get() {
            var r = 0f
            var g = 0f
            var b = 0f
            for (stop in stops) {
                r += stop.red
                g += stop.green
                b += stop.blue
            }
            val n = stops.size.toFloat()
            return Color(r / n, g / n, b / n)
        }

    override fun equals(other: Any?): Boolean =
        other is TrackBackdropGradient && other.stops == stops && other.positions == positions

    override fun hashCode(): Int = 31 * stops.hashCode() + positions.hashCode()

    companion object {
        /** 切歌交叉淡化：把旧渐变的每个色标逐点 lerp 到新渐变。 */
        fun lerp(
            from: TrackBackdropGradient,
            to: TrackBackdropGradient,
            fraction: Float
        ): TrackBackdropGradient = TrackBackdropGradient(
            stops = from.stops.indices.map { lerp(from.stops[it], to.stops[it], fraction) },
            positions = from.positions
        )
    }
}

/**
 * 封面 → 柔和渐变的取色引擎。
 *
 * 算法与 Android 12 Monet 选壁纸主色同源，分三步：
 *
 *  1. **量化 + 选种子色**：[QuantizerCelebi] 把整张封面聚成 ≤128 个颜色簇，
 *     再交给 [Score] 按「好看程度」排序并过滤掉发灰、低饱和、过亮过暗的候选，
 *     取第一名作种子。这一步是「不脏」的根本——比按横带取均值稳得多：封面上
 *     的文字、logo、黑边会被量化聚类自然稀释掉，而不会被均值平均成脏色。
 *
 *  2. **HCT 色彩空间 + TonalPalette 生成明度阶梯**：以种子的色相为基准，
 *     用**固定的低色度**（[NEUTRAL_CHROMA]）生成一整条同色系调色板，然后按
 *     固定的 tone 值取 5 个色标。
 *
 *     为什么必须用 HCT 而不是 HSV：HSV 的 V 不是感知明度，两个 V 相同的颜色
 *     人眼看起来可能一明一暗；HCT 的 tone 就是 CIELAB 的 L*，是感知均匀的。
 *     因此「相邻色标看不出分界」和「文字永远够对比」这两条在 HCT 里可以用数字
 *     算死，在 HSV 里只能近似。
 *
 *     为什么用固定低色度而不是图片自身的饱和度：色度锁死在 ~10 意味着整条渐变
 *     的色相**恒定不变**（TonalPalette 的定义就是同色相同色度、只变明度），
 *     于是绝不可能出现色带或杂色。这就是「高级灰」——色相还在，但视觉上只是
 *     「带一点点封面色彩的中性底」。鲜活度交给封面本身与强调件，背景不需要。
 *
 *  3. **明度走受控斜坡**：色标 tone 固定从「顶部亮 → 底部暗」，不用图片自身的
 *     亮度分布。封面若上暗下亮，直接沿用会在渐变中段出现「中间比两头亮」的
 *     闷感，且顶部不够亮会影响标题可读性。色相已经足够承载封面的身份。
 *
 * 结果恒满足：浅色模式所有色标 tone ∈ [93, 97]、色度 ≤ 10；深色模式 tone
 * ∈ [8, 16]、色度 ≤ 10。这两条区间是「文字永不翻色」的前提，由单测钉死。
 */
internal object TrackGradientPalette {
    /** 非等距色标：顶部亮区占比更大（标题区更亮），底部收得更快。 */
    val POSITIONS: List<Float> = listOf(0f, 0.28f, 0.55f, 0.80f, 1f)

    /**
     * 背景色度上限。10 是「高级灰」的关键数值：
     * 再高会开始显色（脏、花），再低就完全看不出封面的色相。
     */
    private const val NEUTRAL_CHROMA = 10.0

    /**
     * 浅色模式 tone 斜坡：97 → 93，**等距 1 个 tone**。
     *
     * 为什么必须等距而不是「97/96/94/92/91」这种先慢后快的分布：浅色端接近纯白，
     * tone 每变 1 对 RGB 的影响比深色端更大，非等距会让中间那段在观感上被拉宽，
     * 实测相邻色标 RGB 差会从 8 跳到 13（正好卡在可辨边缘）。等距后各段观感一致。
     */
    private val LIGHT_TONES = intArrayOf(97, 96, 95, 94, 93)

    /** 深色模式 tone 斜坡：16 → 8，等距 2 个 tone。 */
    private val DARK_TONES = intArrayOf(16, 14, 12, 10, 8)

    /** 量化聚类的颜色簇上限。128 是官方推荐值，再高收益递减。 */
    private const val QUANTIZE_MAX_COLORS = 128

    /**
     * 取不到有效种子时的兜底色（中性暖灰，HCT tone 50 / chroma 4）。
     * 与 [Score] 的官方兜底同值，保证任何封面都产出一致的中性观感。
     */
    private const val FALLBACK_SEED = 0xFF6B5E54.toInt()

    fun sample(
        pixels: IntArray,
        width: Int,
        height: Int,
        isDark: Boolean
    ): TrackBackdropGradient? {
        if (width <= 0 || height <= 0) return null
        if (pixels.size < width * height) return null

        val opaque = opaquePixels(pixels, width * height) ?: return null
        val seedArgb = pickSeedColor(opaque) ?: FALLBACK_SEED
        val seed = Hct.fromInt(seedArgb)

        // 色度不跟随封面：锁死在 NEUTRAL_CHROMA，整条渐变色相恒定。
        // 这样封面上再花的内容也只会影响「用哪个色相」，不会影响「有多艳」。
        val palette = TonalPalette.fromHueAndChroma(seed.hue, NEUTRAL_CHROMA)
        val tones = if (isDark) DARK_TONES else LIGHT_TONES
        val stops = tones.map { Color(palette.tone(it)) }
        return TrackBackdropGradient(stops, POSITIONS)
    }

    /**
     * 去掉透明像素并强制不透明。
     *
     * 必须去掉 alpha：透明区域的 RGB 往往是 0（纯黑），混进量化会把主色拉黑。
     * 返回 null 表示整张图没有可用像素（调用方回退主题色）。
     */
    private fun opaquePixels(pixels: IntArray, count: Int): IntArray? {
        val out = IntArray(count)
        var n = 0
        for (i in 0 until count) {
            val pixel = pixels[i]
            if ((pixel ushr 24) == 0) continue
            out[n++] = pixel or 0xFF000000.toInt()
        }
        if (n == 0) return null
        return if (n == count) out else out.copyOf(n)
    }

    /**
     * 用官方 Monet 算法选出「最好看」的种子色。
     *
     * [Score] 的排序依据是色度与明度的组合：中等色度、中等明度的颜色得分最高，
     * 近黑近白与低饱和的颜色会被压到后面。`filter = true` 还会剔除过于接近
     * 灰白的候选——正是这一层保证了背景永远不会取到一个「脏」的颜色。
     */
    private fun pickSeedColor(pixels: IntArray): Int? {
        val quantized = QuantizerCelebi.quantize(pixels, QUANTIZE_MAX_COLORS)
        if (quantized.isEmpty()) return null
        val ranked = Score.score(
            quantized,
            /* desired = */ 1,
            /* fallbackColorArgb = */ FALLBACK_SEED,
            /* filter = */ true
        )
        return ranked.firstOrNull()
    }
}
