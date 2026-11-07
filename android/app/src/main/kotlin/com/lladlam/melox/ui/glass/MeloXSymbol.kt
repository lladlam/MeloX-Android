package com.lladlam.melox.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.lladlam.melox.R
import com.lladlam.melox.ui.animation.MeloXMotion

/**
 * ## 图标尺寸的三个旋钮（2026-10-25 定案，互相独立，默认值 = 0.6.5 观感）
 *
 * 上游 0.7.0 的 `0.83` **一个常量背了两个互不相关的职责**：
 *   ① 补偿 `ss16`（字体特性，墨迹 ×1.27）—— 只对 SF 字形有意义；
 *   ② 让手绘/矢量图标不贴边 —— 与字体无关，是纯设计选择。
 * 两者被绑死在同一处：字体一变（换版本 / 被 subset 裁掉 / 平台不认 feature），
 * ① 静默消失而 ② 照旧生效，净效果就从 1.055 掉到 0.83。本 fork 10-22 删掉 `ss16`
 * 之后正是如此 —— 图标整体小 17%（用户：「合并上游后 tab、播放页图标偏小」）。
 *
 * ⇒ 现在拆成三个旋钮，各自独立可调：
 *
 * | 旋钮 | 管谁 | 机制 | 0.6.5（默认） | 上游 0.7.0 |
 * |---|---|---|---|---|
 * | [MeloXSfGlyphScale]   | SF 字形 | `textSize` | 1f | 1.055（= 1.271 × 0.83） |
 * | [MeloXVectorIconScale]| 手绘 / 矢量 | 绘制期缩放 | 1f（铺满） | 0.83f |
 * | [MeloXInkFitTarget]   | 字形间归一 | em 目标 | null（不归一） | 0.98f |
 *
 * ### 为什么 SF 用 `textSize` 而不是继续用绘制期 `withTransform`
 * `MeloXNowPlayingCoreControls` 里 `rememberSfChevronSpec` 用 `Paint.getTextBounds`
 * 量 chevron 的 `pitch` —— 绘制期缩放**不改变测量结果**，于是测量值比实际渲染大 17%，
 * 滚动接缝位置对不上。放 `textSize` 后测量与渲染同源。（上游那处口径分裂仍在。）
 *
 * ### 想切到上游观感
 * 三个一起改：`1.055f` / `0.83f` / `0.98f`。**必须一起切** —— 上游那组数值本身就
 * 让「字形比手绘大 27%」，只切一个会把口径重新弄分裂。
 */
// ⚠ `internal` 而非 `private`：`MeloXIconMetricsTest` 要断言这两个默认值 ——
//   防止有人为了「试一下」改了数字却忘记改回来（那会让全仓图标静默变尺寸，
//   而且这种改动在 diff 里只是两个数字，code review 极容易漏掉）。
internal const val MeloXSfGlyphScale = 1f
internal const val MeloXVectorIconScale = 1f

/**
 * 手绘 / 矢量的视觉缩放入口（合并上游后由 0.7.0 引入，现只服务 [MeloXVectorIconScale]）。
 *
 * 1f ⇒ 恒等：直接返回 `this`，不叠 save/scale/restore。调用点（`LibraryScreen` 7 处 /
 * `MeloXIOSNowPlayingScene` / `MeloXNowPlayingCoreControls`）无需改动。
 */
internal fun Modifier.meloXIconDrawingScale(): Modifier {
    if (MeloXVectorIconScale == 1f) return this
    return drawWithContent {
        val pivot = Offset(size.width / 2f, size.height / 2f)
        withTransform({ scale(MeloXVectorIconScale, MeloXVectorIconScale, pivot) }) {
            this@drawWithContent.drawContent()
        }
    }
}

/**
 * 图标尺寸的**三种口径**（2026-10-25 排查记录，别再混用）。
 *
 * ⚠ 用户报障：「home 和 setting 跟其他 sf 字形的格式不太一样……最初是一样的，
 *   后面这两个图标是我改的，播放页其他图标我也改过，可能导致了范围不一样」。
 *   属实 —— 现在图标尺寸有三套互不相同的算法：
 *
 *   ① **SF 字形**（Explore / Library / Play / Pause / chevron…）
 *      走 [MeloXSymbolIcon] → `SfGlyphIcon`，按 `iconSize`(sp) 渲染。
 *      墨迹 = iconSize × 字形固有比例（实测 `play.fill` 净 0.615×0.688 em）。
 *   ② **字体度量换算型**（Mic / Plus）
 *      `iconSize × INK_UNITS/UPM × VIEWPORT/INK_H`，精确还原 ① ⇒ 天然对齐。
 *   ③ **铺满盒子型**（Home / Settings 的 VectorDrawable；Star / MoreVertical /
 *      HeartPulse 的手绘 Canvas）
 *      墨迹 = 调用方给的盒子（21/24dp），**完全无视 `iconSize`**。
 *
 *   ⇒ ③ 与 ① 的比例天然不一致：① 的墨迹只占 em 的 ~0.69~0.83，③ 占盒子 100%。
 *     但 ③ 的两个 VectorDrawable（`melox_home_tab` viewport 1000×1000、
 *     `ic_melox_brand_gear` 1093×1091）路径本身**铺满 viewport**，所以放大是
 *     真实存在的；而实测它们同屏只有 23.2dp / 16.5dp 高（SF 的 Library 是
 *     19.8dp），说明**盒子尺寸与 iconSize 也没对上**。
 *
 *   ⚠ 2026-10-25 试过给 ③ 加 0.83 归一 → 与 `meloXIconDrawingScale()` 的 0.83
 *     **相乘成 0.689**，Home/Settings 反而缩得过小（用户当场指出「过小」），已撤回。
 *     ⇒ 要调 ③ 只能动**盒子尺寸**（调用方给的 `Modifier.size`），不要再叠绘制期缩放。
 */

/**
 * Semantic icon inventory aligned with SF Symbols names. The bundled SF font is
 * the only icon font used by this component; unresolved symbols fail fast.
 */
enum class MeloXSymbol(
    val sfSymbolName: String,
    val materialLigature: String,
) {
    Home("house", "home"),
    Explore("safari", "explore"),
    Library("music.note.list", "library_music"),
    Settings("gear", "settings"),
    Person("person.crop.circle", "account_circle"),
    Search("magnifyingglass", "search"),
    ChevronLeft("chevron.left", "chevron_left"),
    ChevronRight("chevron.right", "chevron_right"),
    ChevronUpDown("chevron.up.chevron.down", "unfold_more"),
    Xmark("xmark", "close"),
    Ellipsis("ellipsis", "more_horiz"),
    Clock("clock", "schedule"),
    Plus("plus", "add"),
    Download("arrow.down.circle", "download"),
    Share("square.and.arrow.up", "ios_share"),
    Mail("paperplane", "mail"),
    Message("message", "chat_bubble"),
    Info("info.circle", "info"),
    Heart("heart", "favorite"),
    // 「心动模式」品牌图标：心形轮廓 + 贯穿心跳线（ECG）。三态模式按钮专用。
    HeartPulse("heart", "favorite"),
    Star("star", "star"),
    List("list.bullet", "format_list_bulleted"),
    Check("checkmark", "check"),
    ArrowUp("arrow.up.circle.fill", "arrow_upward"),
    ArrowDown("arrow.down.circle", "arrow_downward"),
    Refresh("arrow.clockwise", "refresh"),
    MusicNote("music.note", "music_note"),
    Calendar("calendar", "calendar_month"),
    Flame("flame.fill", "local_fire_department"),
    RadioWaves("dot.radiowaves.left.and.right", "graphic_eq"),
    Walk("figure.walk.motion", "directions_walk"),
    Sparkles("sparkles", "auto_awesome"),
    Quote("quote.bubble", "format_quote"),
    Devices("display", "devices"),
    Landscape("rectangle.landscape.rotate", "screen_rotation"),
    PictureInPicture("pip", "picture_in_picture"),
    Apps("circle.grid.2x2.fill", "apps"),
    Microphone("mic", "mic"),
    Storage("internaldrive", "storage"),
    Bug("ladybug", "bug_report"),
    Play("play.fill", "play_arrow"),
    Pause("pause.fill", "pause"),
    Previous("backward.fill", "skip_previous"),
    Next("forward.fill", "skip_next"),
    Shuffle("shuffle", "shuffle"),
    Repeat("repeat", "repeat"),
    RepeatOne("repeat.1", "repeat_one"),
    Infinity("infinity", "all_inclusive"),
    Lyrics("quote.bubble", "lyrics"),
    AutoMix("waveform", "graphic_eq"),
    AddToPlaylist("text.badge.plus", "playlist_add"),
    Trash("trash", "delete"),
    Moon("moon", "bedtime"),
    Switch("switch.2", "swap_horiz"),
    Book("book.pages", "menu_book"),
    Comment("bubble.left", "chat_bubble"),
    Volume("speaker.wave.2.fill", "volume_up"),
    Queue("text.line.first.and.arrowtriangle.forward", "queue_music"),
    MoreVertical("ellipsis", "more_vert"),
    Circle("circle", "radio_button_unchecked"),
    CheckCircle("checkmark.circle.fill", "check_circle"),
    Unknown("questionmark.circle", "help"),
}

enum class MeloXSymbolVariant {
    Regular,
    Fill,
}

/**
 * Full SF Pro contains the SF Symbols PUA glyphs, but four newer symbols used
 * by MeloX are absent from this build. Keep the old subset as a tiny fallback
 * for exactly those glyphs.
 */
private val MeloSfSubsetOnlyCodePoints = setOf(0x101411, 0x10163F, 0x10173E, 0x101EEF)

private val MeloXSfSymbolCodePoints = mapOf(
    "house" to 0x10039E,
    "safari" to 0x1003AC,
    "music.note.list" to 0x10046C,
    "gear" to 0x10035F,
    "person.crop.circle" to 0x10026D,
    "magnifyingglass" to 0x1002AB,
    "chevron.left" to 0x100189,
    "chevron.right" to 0x10018A,
    "chevron.up.chevron.down" to 0x10018F,
    "xmark" to 0x100184,
    "ellipsis" to 0x100360,
    "clock" to 0x10042B,
    "plus" to 0x10017C,
    "square.and.arrow.up" to 0x100202,
    "message" to 0x100324,
    "info.circle" to 0x100174,
    "heart" to 0x1002B4,
    "list.bullet" to 0x1002F2,
    "checkmark" to 0x100185,
    "arrow.down.circle" to 0x100078,
    "paperplane" to 0x10021F,
    "arrow.up.circle.fill" to 0x100077,
    "arrow.clockwise" to 0x100148,
    "music.note" to 0x10046A,
    "calendar" to 0x100249,
    "flame.fill" to 0x10066D,
    "dot.radiowaves.left.and.right" to 0x100319,
    "figure.walk.motion" to 0x101411,
    "sparkles" to 0x1001BF,
    "quote.bubble" to 0x10032E,
    "display" to 0x1008B9,
    "pip" to 0x100833,
    "circle.grid.2x2.fill" to 0x1007BF,
    "rectangle.landscape.rotate" to 0x101EEF,
    "mic" to 0x1002B0,
    "internaldrive" to 0x10097E,
    "ladybug" to 0x100BD4,
    "play.fill" to 0x100284,
    "pause.fill" to 0x100286,
    "backward.fill" to 0x10028A,
    "forward.fill" to 0x10028C,
    "shuffle" to 0x10029D,
    "repeat" to 0x10029E,
    "repeat.1" to 0x10029F,
    "infinity" to 0x100BE0,
    "waveform" to 0x10066B,
    "text.badge.plus" to 0x1002F8,
    "trash" to 0x100211,
    "moon" to 0x1001B9,
    "switch.2" to 0x10070A,
    "book.pages" to 0x10173E,
    "bubble.left" to 0x10032A,
    "speaker.wave.2.fill" to 0x1002A7,
    "text.line.first.and.arrowtriangle.forward" to 0x10163F,
    "questionmark.circle" to 0x10005C,
    "checkmark.circle.fill" to 0x100063,
)

@Composable
fun MeloXSymbolIcon(
    symbol: MeloXSymbol,
    modifier: Modifier = Modifier,
    color: Color,
    variant: MeloXSymbolVariant = MeloXSymbolVariant.Regular,
    iconSize: TextUnit = 24.sp,
    contentDescription: String? = null,
) {
    // ★★ 2026-10-25（用户：「大部分图标大小都有些不对」）：**在入口统一按字形墨迹归一**。
    //
    //   为什么必须在入口、而不是只在 SF 字形那一支：本函数有 6 条互不相同的绘制路径
    //   （SF 字形 / 品牌矢量 gear / mic / plus / star / 手绘 more-vertical），
    //   它们各自的"尺寸"定义完全不同（有的按 sp、有的按盒子、有的按字体度量换算）。
    //   只归一其中一支 ⇒ 同一行里又出现两种尺寸（这个坑 2026-10-25 已踩过）。
    //   ⇒ 归一 `iconSize` 后，6 条路径拿到的是**同一个已校正的字号**，
    //     再由各自的换算逻辑还原成墨迹，视觉高度才对得齐。
    //
    //   依据：SF 字体里各符号的 ink_h/em 从 0.195（ellipsis）到 1.200
    //   （rectangle.landscape.rotate），差 6 倍 —— 这才是"大部分图标都不对"的真因。
    //   数据见 [MeloXGlyphInkHeight]。
    val fittedIconSize = meloXInkFittedIconSize(iconSize, symbol.sfSymbolName)
    val iconModifier = modifier.meloXIconDrawingScale()
    // Brand overrides: these SF Symbol glyphs are replaced by traced vectors.
    //   Settings  -> the MeloX chainring disc instead of the SF gear.
    //   Microphone-> ic_melox_mic, extracted from the same `mic` glyph with its
    //                broken stem winding fixed (see the drawable's header note).
    // Both the bottom-bar Settings tab and the Settings-list "通用" item flow
    // through this symbol, so they pick up the brand disc automatically; likewise
    // every 听歌识曲 entry (search field + Home quick action card) gets the
    // sealed-off microphone without any change at the call sites.
    when (symbol) {
        MeloXSymbol.Circle -> {
            val density = LocalDensity.current
            val diameter = with(density) { fittedIconSize.toPx() }
            Canvas(
                modifier = iconModifier.semantics {
                    contentDescription?.let { this.contentDescription = it }
                },
            ) {
                val stroke = diameter * .065f
                drawCircle(
                    color = color,
                    radius = ((diameter * .42f) - stroke / 2f).coerceAtLeast(0f),
                    style = Stroke(width = stroke),
                )
            }
            return
        }
        // ── 2026-10-25 图标大统一 ────────────────────────────────────────────
        //
        // 用户原话：「相当一部分图标在 0.6.2 后被我引入，那些原本可能是 sf 字形，
        //   现在可能就是单纯的矢量……真正统一，**无损转 sf 字形**，
        //   然后保证跟之前一模一样」。
        //
        // ### 事实核对（git 历史 + 字体实测）
        // · **0.6.0**（用户开始提 PR 的版本）里 `MeloXSymbolIcon` **只有一条路径**，
        //   100% 走 `SfGlyphIcon`，**没有任何矢量特例** —— 这就是"最初是一样的"。
        // · 之后陆续引入 9 个矢量特例（Settings 齿轮 / mic / plus / ellipsis /
        //   heart / repeat / repeat.1 / shuffle / infinity）。它们与 SF 路径的
        //   **尺寸口径完全不同**：矢量铺满调用方盒子，SF 按 `iconSize`(sp) 渲染
        //   ⇒ 同一行里必然参差。这就是「大部分图标大小都不对」的机制来源。
        //
        // ### 做法：5 个转 SF，6 个保留矢量
        // 以下 5 个在 `sf_pro_vf.ttf` 里都有**形状等价、墨迹正常**的字形，
        // 直接删掉特例分支让它们回落到文件末尾的 `SfGlyphIcon`
        // ⇒ 与 0.6.0 **完全同一条渲染路径**（"无损"的最强保证：
        //   不是"调成看起来像"，而是"走同一段代码"）：
        //     mic 0.699×1.045 ｜ plus 0.496×0.483 ｜ ellipsis 0.488×0.111
        //     heart 0.970×0.896 ｜ infinity 0.828×0.392
        // （`heart` 这里指 `MeloXSymbol.Heart` 普通收藏心；`MeloXSymbol.HeartPulse`
        //   是心动模式品牌图标，**不**在此列，见下方保留矢量。）
        //
        // ### ⚠ 保留矢量的 6 个（2026-10-25 裁定 + 队列页回归修复）
        //   · **Settings**：MeloX 品牌链轮盘是**品牌资产**，不是通用齿轮。
        //     虽然 SF 有 `gear`（0x10035F，4 轮廓 / 18 齿 / 实心盘 + 中心孔），
        //     但它带 3 根辐条，且是从 macOS 齿轮演化的通用图标 —— 换成它等于
        //     **改掉品牌识别**。已实测对比 7 个 SF 版本/字族（Rounded/Text/
        //     Display/Compact × Text/Rounded + 项目内 2 个字体），**没有任何一个
        //     与品牌盘一致**。⇒ 保留矢量。
        //   · **Star**：`sf_pro_vf.ttf` 里**没有 star 字形**（cmap 全表 0 命中），
        //     无法转 SF。
        //   · **Shuffle / Repeat / RepeatOne**：播放模式品牌字形。几何来自
        //     [MeloXPlaybackModeGlyph]（`SfGlyphPaths`，1:1 复刻 sf_pro_subset.ttf 的
        //     对应轮廓，绕向已归一化），与播放页「列表按钮右上角徽标」
        //     `ScenePlaybackModeBadge` **逐像素同源**。2026-10-25 的「图标大统一」
        //     误把它们回落到完整 sf_pro_vf 的 `drawText` 字形，导致队列页左侧两个
        //     图标变粗、与徽标口径不一致（用户 2026-10-25 报障）。恢复矢量兜底。
        //   · **HeartPulse**：心动模式品牌图标（心形轮廓 + 贯穿 ECG 心跳线），同样
        //     复用 [HeartPulseVectorIcon]，与播放页徽标同源。若回落 SF 的 `heart`
        //     字形会变成普通收藏心、丢失 ECG 线（用户报障「图标不见了，采用的是
        //     音乐库那个版本的」）。
        //
        //   这些保留项**尺寸口径必须统一**：矢量路径绘制盒 = `iconSize` 体系
        //   （Settings/Home/Star 走 [meloXVectorRenderSize]；Shuffle/Repeat/RepeatOne
        //   走 `MeloXPlaybackModeGlyph` 的 `iconSize × inkEm`；HeartPulse 铺满盒子），
        //   而不是"铺满调用方盒子"。
        //   ⚠ 不要再叠第二层绘制期缩放：`meloXIconDrawingScale()` 已是 0.83，
        //      再乘一次会变成 0.689（2026-10-25 已踩过，Settings 当场缩得过小）。
        MeloXSymbol.Settings -> {
            // ⚠⚠ 必须用**原始** `iconSize`，不能用 `fittedIconSize`：
            //   `fittedIconSize` 已按 gear 的 ink_h/em(1.086) 把 24sp 缩到 21.65sp
            //   （那是给 **SF 字形**用的归一）。矢量路径再过一次
            //   `meloXVectorRenderSize` 就是**双重归一** ⇒ 实测墨迹只有 17.8dp，
            //   比 Library 的 19.5dp 小 9%（2026-10-25 真机实测）。
            //
            // ⚠ `inkFill = 1.0`：gear 的 pathData bbox = (1.05,1.38)-(1093.76,1089.47)，
            //   viewport 1093×1091 ⇒ 占宽 99.97% / 占高 99.73%，**几乎完全铺满**。
            //   写 0.912 会把它放到 21.4dp（过大）—— 那个数是把"墨迹/em"误当成
            //   "墨迹/viewport"算出来的，已修正。
            Icon(
                painter = painterResource(R.drawable.ic_melox_brand_gear),
                contentDescription = contentDescription,
                modifier = Modifier
                    .requiredSize(meloXVectorRenderSize(iconSize))
                    .then(iconModifier),
                tint = color,
            )
            return
        }
        MeloXSymbol.Home -> {
            // Home 底栏用**自绘矢量**（`melox_home_tab`，按用户给的图描的），不用 SF 的 house
            // 字形。2026-10-25 从 `MeloXApp.RootGlyphIcon` 的特例迁进来，与 Settings 同一条路
            // —— 此前它由 `Icon(painter, Modifier.size(23.6.dp))` 直接铺满盒子，**绕过**
            // 本函数，于是底栏出现"两种口径"（用户原话：「home 和 setting 跟其他 sf 字形的
            // 格式不太一样」）。
            //
            // ⚠ 尺寸口径：`meloXVectorRenderSize(iconSize)` ⇒ 盒子 = iconSize，路径铺满
            //   viewport（-0.1~999.6 / 1000）⇒ 墨迹 ≈ 24dp。0.6.5 里 Home 就是 24dp 盒子
            //   铺满 = 23.86dp，**逐像素等价**。
            //   那个 23.6dp 是 0.83 时代的过度补偿（23.6 × 0.825 ≈ 19.5dp，用来对齐当时的
            //   Library 19.5dp）；0.83 撤掉后两者同比放大，23.6dp 已无意义，已删除。
            //
            // ⚠ 同 Settings：用**原始** `iconSize`，不要用 `fittedIconSize`（那是给 SF 字形
            //   算 ink_h/em 用的），否则矢量会被双重归一。
            Icon(
                painter = painterResource(R.drawable.melox_home_tab),
                contentDescription = contentDescription,
                modifier = Modifier
                    .requiredSize(meloXVectorRenderSize(iconSize))
                    .then(iconModifier),
                tint = color,
            )
            return
        }
        MeloXSymbol.Star -> {
            // ⚠ 同 Settings：用**原始** `iconSize`，避免与 `fittedIconSize` 双重归一。
            StarVectorIcon(
                modifier = iconModifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
                variant = variant,
            )
            return
        }
        // ── 播放模式品牌字形：保留矢量，不走 SF 字形 ──────────────────────────
        //
        // 这三个（随机 / 循环 / 单曲循环）与「心动模式」是**播放模式专用品牌字形**，
        // 几何全部来自 [MeloXPlaybackModeGlyph]（1:1 复刻 sf_pro_subset.ttf 的对应轮廓），
        // 与播放页「列表按钮右上角徽标」`ScenePlaybackModeBadge` **逐像素同源**。
        //
        // ⚠ 2026-10-25 的「图标大统一」把它们误回落到 SF 字形（完整 sf_pro_vf 的
        //   `drawText`），导致：① 左侧两个（随机 / 循环）看起来变粗、与播放页徽标
        //   口径不一致（用户 2026-10-25 报障「队列页四个按钮的左边两个过粗」）；
        //   ② 轮廓经 `drawText` 渲染在小尺寸下发虚、不如矢量 `drawPath` 干净。
        // ⇒ 这里恢复矢量兜底，强制与播放页徽标同款。
        MeloXSymbol.Shuffle, MeloXSymbol.Repeat, MeloXSymbol.RepeatOne -> {
            PlaybackModeVectorIcon(
                symbol = symbol,
                modifier = iconModifier,
                color = color,
                iconSize = iconSize,
                contentDescription = contentDescription,
            )
            return
        }
        // 「心动模式」品牌矢量：心形轮廓 + 贯穿 ECG 心跳线。
        // 选 B 方案 = 纯单色（激活=黑、未激活=白），只靠形状区分。
        // ★ 与播放页徽标（ScenePlaybackModeBadge）逐像素同源。
        // ⚠ 绝不能回落 SF 的 `heart` 字形：那只是普通收藏心，会被读成「音乐库收藏」
        //   图标，且丢失心动模式专属的 ECG 线（用户 2026-10-25 报障
        //   「心动模式的图标不见了，现在采用的是音乐库那个版本的」）。
        MeloXSymbol.HeartPulse -> {
            // ★ 品牌矢量图标必须按 `iconSize` 约束绘制盒：不能让 `HeartPulseVectorIcon`
            //   内部的 `min(size)` 被调用方的 `fillMaxSize` 撑成铺满卡片的巨图。
            //   发现页卡片即此场景——同卡片的 SF 字形靠 `iconSize`(textSize) 控大小，
            //   唯独 HeartPulse 走内部 min(size)，被 fillMaxSize 撑大（2026-10-25 用户报
            //   「首页/发现页的心动模式图标变成了巨大图标」）。
            //   改用 `Modifier.size(iconSize)`：盒固定为 iconSize，矢量按盒绘制，与
            //   同页 SF 字形视觉尺寸一致。播放页徽标 / 音乐库入口是 `HeartPulseVectorIcon`
            //   的直接调用、自带 `Modifier.size(...)`，不走此分支，不受影响。
            HeartPulseVectorIcon(
                modifier = Modifier.size(iconSize.value.dp),
                color = color,
                contentDescription = contentDescription,
            )
            return
        }
        else -> Unit
    }
    // ⚠ SF 字形**不走** `meloXIconDrawingScale()`：它的尺寸由 [MeloXSfGlyphScale] 在
    //   `textSize` 里给（见 SfGlyphIcon），与「手绘/矢量」的 [MeloXVectorIconScale] 分开。
    //   绘制期缩放的副作用是 `Paint.getTextBounds` 量不到 —— `MeloXNowPlayingCoreControls`
    //   的 chevron 测量正踩这个坑，所以字形必须走字号。
    val codePoint = requireNotNull(MeloXSfSymbolCodePoints[symbol.sfSymbolName]) {
        "Missing SF Symbol mapping: ${symbol.sfSymbolName}"
    }
    SfGlyphIcon(
        codePoint = codePoint,
        modifier = modifier,
        color = color,
        // ★ 2026-10-25 终局：**不做字形间归一**（[MeloXInkFitTarget] = null），
        //   `fittedIconSize` 目前恒等于 `iconSize`。
        //   保留取值点是为了将来单独讨论「字形之间更一致」时有唯一入口：
        //   届时只把 MeloXInkFitTarget 设成 0.98f，调用点不动。
        //   （历史：曾按 0.98em 目标放大偏小字形，但会让 play.fill / forward.fill
        //    超过 0.6.5 的原始大小，与「恢复合并前尺寸」冲突。）
        iconSize = fittedIconSize,
        contentDescription = contentDescription,
        weight = if (variant == MeloXSymbolVariant.Fill) FontWeight.Medium else FontWeight.Normal,
    )
}

/**
 * 各 SF 符号的**墨迹高度 / em**（从 `sf_pro_vf.ttf` / `sf_pro_subset.ttf` 的
 * glyf 表逐字形实测，2026-10-25）。
 *
 * ⚠ 这张表是「图标大小不一致」的**唯一真相**：`SfGlyphIcon` 用
 *   `canvas.drawText` 按 `iconSize`(sp) 渲染字形，墨迹高度 = iconSize × 本表值。
 *   所以同一个 iconSize 下，`rectangle.landscape.rotate`（1.200）比
 *   `ellipsis`（0.195）**大 6 倍** —— 这不是谁写错了数字，而是 SF 字体里
 *   各符号的设计墨迹本来就差得很远。
 *
 * ⚠ 只收录 `MeloXSfSymbolCodePoints` 里真实用到的符号；新增符号时**必须补测**
 *   （用 fontTools 读 glyf 的 yMax-yMin / unitsPerEm），否则会退回不归一。
 */
internal val MeloXGlyphInkHeight: Map<String, Float> = mapOf(
    "rectangle.landscape.rotate" to 1.200f,
    "ladybug" to 1.149f,
    "figure.walk.motion" to 1.118f,
    "book.pages" to 1.115f,
    "sparkles" to 1.114f,
    "square.and.arrow.up" to 1.102f,
    "trash" to 1.087f,
    "gear" to 1.086f,
    "flame.fill" to 1.056f,
    "arrow.clockwise" to 1.051f,
    "mic" to 1.045f,
    "house" to 1.025f,
    "message" to 1.023f,
    "paperplane" to 1.023f,
    "pip" to 1.023f,
    "quote.bubble" to 1.010f,
    "bubble.left" to 1.010f,
    "safari" to 0.996f,
    "person.crop.circle" to 0.996f,
    "clock" to 0.996f,
    "info.circle" to 0.996f,
    "arrow.down.circle" to 0.996f,
    "arrow.up.circle.fill" to 0.996f,
    "questionmark.circle" to 0.996f,
    "checkmark.circle.fill" to 0.996f,
    "moon" to 0.988f,
    "music.note.list" to 0.984f,
    "music.note" to 0.984f,
    "display" to 0.984f,
    "text.line.first.and.arrowtriangle.forward" to 0.949f,
    "magnifyingglass" to 0.928f,
    "circle.grid.2x2.fill" to 0.927f,
    "switch.2" to 0.922f,
    "text.badge.plus" to 0.919f,
    "shuffle" to 0.910f,
    "repeat.1" to 0.900f,
    "calendar" to 0.899f,
    "waveform" to 0.899f,
    "heart" to 0.896f,
    "repeat" to 0.895f,
    "dot.radiowaves.left.and.right" to 0.877f,
    "chevron.up.chevron.down" to 0.864f,
    "speaker.wave.2.fill" to 0.861f,
    "chevron.left" to 0.848f,
    "chevron.right" to 0.848f,
    "checkmark" to 0.834f,
    "internaldrive" to 0.833f,
    "play.fill" to 0.827f,
    "plus" to 0.806f,
    "pause.fill" to 0.795f,
    "xmark" to 0.777f,
    "backward.fill" to 0.767f,
    "forward.fill" to 0.767f,
    "list.bullet" to 0.713f,
    "infinity" to 0.578f,
    "ellipsis" to 0.195f,
)

/**
 * 归一目标：只对**偏小**的字形生效（ink_h/em < 本值才会被放大）。
 *
 * ⚠ 当前**未被引用**（[MeloXInkFitTarget] = null ⇒ 归一整体关闭）。保留取值备查：
 *   将来要开归一，直接用 0.98f（≈ SF 图标 ink_h/em 的中位数，实测 56 个符号中位 0.984），
 *   配合 [MeloXInkFitMinScale] = 1f ⇒ 「ink_h/em ≥ 0.98 的字形完全不动」，
 *   大的（gear 1.086）保持大，只把明显偏小的补上来。
 */
private const val MeloXGlyphTargetInk = 0.98f

/**
 * 「设计上就扁」的符号：按**高度**对齐会把它们放大到失真，所以单独给归一目标。
 *
 * ⚠ 这些不是"画错了"，而是 SF 符号本来就这样：
 *   · `ellipsis`（横向三点）ink_h/em 只有 **0.195** —— 若按 0.98 对齐要放大 5 倍，
 *     三点会变成三根粗棒。正确做法是**保持原样**（scale=1），只做轻微补偿。
 *   · `infinity`（∞）0.578、`list.bullet` 0.713 —— 都是宽扁形，同理。
 * ⇒ 对它们把目标降到"够用即可"，避免被 1.35 上限硬顶后仍显得突兀。
 */
private val MeloXFlatGlyphTarget: Map<String, Float> = mapOf(
    // 三点：只补 1.15 倍（视觉上"略大一点"，但仍是三点），不再追高度。
    "ellipsis" to 0.195f * 1.15f,
    // ∞ 与列表：补到接近方形图标的视觉重量（各自 ~1.2 倍）。
    "infinity" to 0.578f * 1.20f,
    "list.bullet" to 0.713f * 1.20f,
)

/**
 * 单次归一的缩放范围。
 *
 * ⚠⚠ 2026-10-25 第二次修正：**MinScale 从 0.75 提到 1.0**，即「只放大偏小的字形，
 *   不压缩偏大的」。
 *
 * ### 为什么
 * 第一版把范围设成 [0.75, 1.35]，本意是"把明显偏大/偏小的都拉回中间"。但真机实测
 * （底栏 tab，缩放已改回 1.0）发现它把 0.6.5 本来就正常的字形**压小了**：
 *     safari        0.6.5 = 23.9dp → 归一后 22.0dp（-8%）
 *     music.note.list 0.6.5 = 23.6dp → 归一后 22.0dp（-7%）
 *     gear          0.6.5 = 26.1dp → 归一后 22.0dp（-16%）
 * 用户对照 0.6.5 反馈「tab 图标好像比 0.6.5 小」，这就是原因。
 *
 * ### 0.6.5 的真实行为
 * 0.6.5 **完全没有归一**：墨迹 = iconSize × ink_h/em，大的字形（gear 1.086）
 * 天然比小的（ellipsis 0.195）大 5 倍。用户从未抱怨这件事 —— 说明 SF 字体的
 * 原始比例是设计意图，不该被"平均化"。
 *
 * ### 因此归一的目标收窄为
 * 只补**明显偏小**的字形（play.fill 0.827 / forward.fill 0.767 / ellipsis 0.195
 * 这类在同一行里显得过小的），让它们不至于比相邻图标小一圈；
 * 而 ink_h/em ≥ 0.98 的字形**一律保持原样**（= 0.6.5 行为）。
 */
private const val MeloXInkFitMaxScale = 1.35f
private const val MeloXInkFitMinScale = 1f

/**
 * 字形墨迹归一的**目标墨迹高度**（em）—— **null = 不归一**（2026-10-25 定案，
 * 与本仓三个旋钮默认 1f 配套）。
 *
 * 0.6.5 从未归一：墨迹 = `iconSize × ink_h/em`，gear(1.086) 比 ellipsis(0.195) 大 5~6 倍
 * 是 SF 字体本身的原始比例，用户从未抱怨过。而归一（哪怕只放大偏小的）会把
 * `play.fill`(+18%) / `forward.fill`(+28%) 这类字形**放大到超过 0.6.5**
 * ⇒ 与「回到合并前大小」的目标直接冲突，所以默认关闭。
 *
 * 表格 [MeloXGlyphInkHeight] 与算法**原样保留**：将来若要在「整体尺寸 = 0.6.5」
 * 的前提下单独讨论「字形之间更一致」，把它设成 0.98f 即可（= 上游 0.7.0 的行为），
 * 不要顺手打开 —— 一旦打开，播放页的传输键会明显大于 0.6.5。
 *
 * ⚠ 这张表是按 **`ss16` 关闭**（标准字形）实测的，与上游 0.7.0 的语境不同：上游开着
 *   ss16（墨迹统一 ×1.27），所以他要的 0.98 与我们的 0.98 不是同一个视觉目标。
 *   本仓要开的话应重新标定，不要抄上游的数字。
 */
internal val MeloXInkFitTarget: Float? = null

/**
 * 把 `iconSize` 按字形墨迹归一，使各符号的**视觉高度**接近一致。
 *
 * 未收录的符号原样返回（宁可不动，也不要猜一个系数）。
 */
internal fun meloXInkFittedIconSize(iconSize: TextUnit, sfSymbolName: String): TextUnit {
    val globalTarget = MeloXInkFitTarget ?: return iconSize
    val ink = MeloXGlyphInkHeight[sfSymbolName] ?: return iconSize
    if (ink <= 0f) return iconSize
    // 「设计上就扁」的符号用各自的温和目标，避免被按高度对齐放大到失真。
    val target = MeloXFlatGlyphTarget[sfSymbolName] ?: globalTarget
    val raw = target / ink
    val scale = raw.coerceIn(MeloXInkFitMinScale, MeloXInkFitMaxScale)
    if (scale == 1f) return iconSize
    // TextUnit 只能用 sp 重建：入参本身就是 sp（默认 24.sp），按比例缩放即可。
    return (iconSize.value * scale).sp
}

/**
 * **矢量图标**的统一尺寸：让它的墨迹高度与「同 iconSize 的 SF 字形」一致。
 *
 * ### 为什么需要它
 * `MeloXSymbolIcon` 有两类实现，尺寸口径必须严格区分：
 *   · **SF 字形**：按 `iconSize`(sp) 渲染，墨迹 = iconSize × 该字形 ink_h/em。
 *   · **矢量/手绘**：Canvas 或 VectorDrawable **铺满**调用方给的盒子，
 *     墨迹 = 盒子尺寸（与 iconSize 无关）。
 *     → 若直接铺满，盒子大小就成了唯一变量，和 SF 字形的字号体系**天然对不上**
 *       （这就是用户说的「范围不一样」）。
 *
 * ### 2026-10-25 修正：不再乘 0.98 目标
 * 第一版写 `iconSize × 0.98 / inkFill`，本意是"对齐到统一墨迹高"。但
 * [MeloXInkFitMinScale] 改成 1f 后（只放大偏小的、不压缩偏大的），SF 字形
 * 已经回到 0.6.5 原始比例；矢量再乘 0.98 反而比 SF 小 2%。
 * ⇒ 现在直接用 `iconSize / inkFill`：矢量墨迹 = 盒子 × 自身占比，
 *   与「ink_h/em = 1.0 的 SF 字形」等价，量级正确。
 *
 * @param inkFill 该矢量的墨迹占其绘制盒的比例（铺满 = 1.0；星形外接圆 ≈ 0.92）。
 */
@Composable
private fun meloXVectorRenderSize(iconSize: TextUnit, inkFill: Float = 1f): Dp {
    val density = LocalDensity.current
    val safeFill = if (inkFill <= 0f) 1f else inkFill
    return remember(density, iconSize, safeFill) {
        with(density) { (iconSize.toPx() / safeFill).toDp() }
    }
}

// sf_pro_subset.ttf 中 mic 字形的度量，用于把矢量还原到与字号渲染同尺寸。
private const val MIC_UPM = 2048f
private const val MIC_INK_UNITS = 2140f
// ic_melox_mic.xml 的 viewport 与墨迹高度。
private const val MIC_VIEWPORT = 24f
private const val MIC_INK_H = 23.90f

// sf_pro_subset.ttf 中 plus（U+10017C）字形的度量，同样用于把矢量还原到与字号渲染同尺寸。
// 该 glyph 由两条独立轮廓（竖直棒 + 水平棒）组成，两条都是实体，无绕向问题。
// 字体墨迹 bbox = (255,-103)-(1905,1547)，即 1650 × 1650 units。
private const val PLUS_UPM = 2048f
private const val PLUS_INK_UNITS = 1650f
// ic_melox_plus.xml 的 viewport 与墨迹高度（18.29 ≈ 1650/2048 × 24 缩放后，与字模同形）。
private const val PLUS_VIEWPORT = 24f
private const val PLUS_INK_H = 18.29f

/**
 * 听歌识曲麦克风。字体里那支 `mic` 字形（U+1002B0）在 nonzero 规则下自身是破的
 * —— 竖杆绕向与其余实体相反，在「拾音弧底部」「底座横线」两处被挖成孔，
 *   所以改为使用 [R.drawable.ic_melox_mic]：同一字形 glyf 轮廓的 1:1 提取，
 *   只把竖杆绕向翻过来，余下像素逐点未变（推导见该 drawable 的头部注释）。
 *
 * ⚠ **尺寸刻意不跟随调用方的 Box**：SF 字形本来就是按 `iconSize`（sp）渲染的，
 *   外面的 18dp / 22dp 图标盒只起占位作用 —— mic 的墨迹纵向本来就会溢出。
 *   所以这里照字体的度量换算，而不是直接把矢量塞进 `modifier`：
 *     字体 unitsPerEm = 2048，mic 墨迹 = 1431 × 2140 单位
 *     ⇒ 墨迹 = iconSize × 2140/2048，默认 24.sp 时 = 16.77 × 25.08 dp
 *     本矢量墨迹占 23.90/24 ⇒ 渲染尺寸 = 墨迹 ÷ (23.90/24)
 *   这样默认 24.sp 得到 25.2dp，首页快捷卡那种 `iconSize = 72.sp` 的大水印得到
 *   75.6dp，**任何字号下都与原字形同尺寸**，换成矢量不会被压缩掉光学字号。
 *
 * ⚠ alpha 走 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint。单路径
 *   fill 本身不会双重混合，但一旦将来把它拆成多条路径，逐路径施加的
 *   ColorFilter.tint 会在轮廓叠合处二次混合出深斑；图层级 alpha 免疫这个问题。
 */
@Composable
private fun MicrophoneVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val renderSize = remember(density, iconSize) {
        val emPx = with(density) { iconSize.toPx() }
        with(density) { (emPx * MIC_INK_UNITS / MIC_UPM * MIC_VIEWPORT / MIC_INK_H).toDp() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_melox_mic),
            contentDescription = contentDescription,
            // ⚠ 必须 requiredSize 而不是 size：`size()` 会服从父约束，而调用方给的
            //   18dp / 22dp 图标盒只是占位，mic 本来就会溢出它。用 `size()` 会被
            //   压成 18dp（实测墨迹掉到 12 × 18 dp，整整小三成）—— `requiredSize`
            //   才等价于原来 Canvas 里按 24.sp 自由绘制的行为。
            modifier = Modifier.requiredSize(renderSize).alpha(color.alpha),
            tint = color.copy(alpha = 1f),
        )
    }
}

/**
 * SF Symbols `plus`（U+10017C）的矢量兜底。
 *
 * 换矢量的动机 —— 原先走 `SfGlyphIcon` 靠 `Paint.getTextBounds` 计算 baseline，
 *   但不同 Android 版本 / Skia 版本对同一 glyph 的 `bounds.top` / `bounds.bottom`
 *   会因 hinting、`SUBPIXEL_TEXT_FLAG` 等差异而差 1~2 像素，导致「加号」在不同机型
 *   上纵向偏移 2~4dp（用户 2026-09-26 反馈：其他机型加号偏下）。改走矢量与
 *   [MicrophoneVectorIcon] / [MeloXSymbol.Settings] 同一条兜底路径，跨设备一致。
 *
 * 尺寸刻意不跟随调用方的 Box：调用方给的 `Modifier.size(20.dp)` 只是**占位**，
 *   字号仍是 24.sp；本矢量按同样的字体度量换算，任何 `iconSize` 下都还原成与
 *   原字形**同墨迹尺寸**的矢量（默认 24.sp ⇒ 25.37dp），换成矢量不会被压扁。
 *
 * ⚠ alpha 走 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint（与 mic 一致）：
 *   单路径 fill 本身不会双重混合，但一旦将来把两条棒拆成两条 path，逐路径施加的
 *   ColorFilter.tint 会在叠合处二次混合出深斑；图层级 alpha 免疫这个问题。
 */
@Composable
private fun PlusVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val renderSize = remember(density, iconSize) {
        val emPx = with(density) { iconSize.toPx() }
        with(density) { (emPx * PLUS_INK_UNITS / PLUS_UPM * PLUS_VIEWPORT / PLUS_INK_H).toDp() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_melox_plus),
            contentDescription = contentDescription,
            // 同 mic：requiredSize 而非 size，避免被父 20dp 图标盒压扁。
            modifier = Modifier.requiredSize(renderSize).alpha(color.alpha),
            tint = color.copy(alpha = 1f),
        )
    }
}

/**
 * 播放页收藏按钮星形。两个子集字体都没有 star 字形，所以这里用手绘矢量五角星兜底
 * （与 mic / plus 同一条品牌矢量路径）：
 *   - [MeloXSymbolVariant.Regular] → 描边（未收藏）
 *   - [MeloXSymbolVariant.Fill]    → 实心（已收藏）
 *
 * 内凹比 0.40 取自示意图量取的实体/内点半径比；星形外缘占满调用方给的图标盒
 * （与 ellipsis 同样 22.dp），保证两个按钮视觉重量一致（用户要求“协调好大小”）。
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而不是带 alpha 的 tint，
 *   与 mic / plus 一致：单路径 fill 不会双重混合，但描边在轮廓叠合处若用逐路径
 *   tint 会二次混合出深斑；图层级 alpha 免疫该问题。
 */
@Composable
private fun StarVectorIcon(
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
    variant: MeloXSymbolVariant,
) {
    // ★ 2026-10-25：**改为由 iconSize 决定墨迹尺寸**（原先只看盒子 `min(w,h)`）。
    //   原实现 `outer = min(w,h)/2 * 0.92` 意味着"墨迹 = 调用方盒子"，而 SF 字形
    //   是按 iconSize(sp) 画的 —— 同一个 MeloXSymbolIcon 下两条口径必然对不上
    //   （这正是用户说的"范围不一样"）。现在统一走 mic/plus 那条**字体度量换算**：
    //   星形墨迹外接圆直径取 iconSize 的 0.92（≈ SF 字形 0.98 em 的视觉当量），
    //   再叠加 `meloXIconDrawingScale()` 的全局缩放，与 SF 路径同源。
    val density = LocalDensity.current
    val renderSize = remember(density, iconSize) {
        with(density) { (iconSize.toPx() * 0.92f).toDp() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .requiredSize(renderSize)
                .alpha(color.alpha)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics(mergeDescendants = true) {
                            this.contentDescription = contentDescription
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(size.width, size.height) / 2f * 0.92f
        val innerRatio = 0.40f
        val strokeW = outer * 0.16f
        // 描边态沿路径中心线绘制，若中心线取 outer，外缘会比实心态多出 strokeW/2。
        // 这里把描边态的路径内缩半个描边宽，使外缘恰好落在 outer 上，
        // 两种态外轮廓半径一致 → 实心星与描边星视觉大小相同。
        val drawOuter = if (variant == MeloXSymbolVariant.Fill) outer else outer - strokeW / 2f
        val inner = drawOuter * innerRatio
        val path = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) drawOuter else inner
                val angle = (-Math.PI / 2.0 + i * Math.PI / 5.0).toFloat()
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 1f),
            style = if (variant == MeloXSymbolVariant.Fill) {
                Fill
            } else {
                Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
            },
        )
        }
    }
}

/**
 * SF Symbols 播放模式字形的**真实填充轮廓**（`shuffle` 0x10029D / `repeat` 0x10029E /
 * `repeat.1` 0x10029F）：fontTools 从 `sf_pro_subset.ttf` 抽出的 100×100 归一化盒
 * （max-span = 100）SVG path data（M/L/Q/Z 绝对坐标，全部二次曲线 ⇒ Compose
 * `quadraticTo` 一一对应）。数据由 `.workbuddy/tmp/normalize_winding.py` 生成。
 *
 * ⚠ **绕向已归一化**：TrueType 走 nonzero 填充；`repeat` / `repeat.1` 的第 4 段轮廓
 * 绕向与其余段相反，和左下箭头重叠处绕数相消 ⇒ 渲染器会**掏出空洞**。这里每段字形的
 * 所有轮廓已统一成同一绕向，nonzero 即等价于并集，观感 = SF 原版。
 * `shuffle` 四段本就同向（shoelace 有向面积逐段核验：4 段同号）。
 */
private object SfGlyphPaths {

    const val SHUFFLE = 0
    const val REPEAT = 1
    const val REPEAT_ONE = 2
    const val HEART_PULSE = 3
    const val INFINITY = 4

    private val SHUFFLE_PATHS = listOf(
        "M77.5 13.3Q77.5 12.0 78.2 11.3Q78.9 10.6 80.2 10.6Q80.9 10.6 81.4 10.8Q82.0 11.0 82.5 11.4L99.0 25.0Q100.0 25.9 100.0 27.1Q100.0 28.3 99.0 29.2L82.5 42.8Q82.0 43.2 81.4 43.4Q80.9 43.6 80.2 43.6Q78.9 43.6 78.2 42.9Q77.5 42.2 77.5 40.9Z",
        "M0.0 72.3Q0.0 70.7 1.1 69.7Q2.2 68.7 4.0 68.7L14.1 68.7Q17.4 68.7 20.0 67.3Q22.5 66.0 25.4 62.7L51.7 31.9Q55.6 27.3 59.3 25.5Q62.9 23.8 68.4 23.8L83.9 23.8Q85.4 23.8 86.4 24.8Q87.5 25.9 87.5 27.4Q87.5 28.9 86.4 30.0Q85.4 31.0 83.9 31.0L68.6 31.0Q66.2 31.0 64.3 31.6Q62.3 32.2 60.6 33.5Q58.8 34.8 56.9 37.0L30.5 67.8Q26.6 72.3 23.0 74.1Q19.3 75.9 13.9 75.9L4.0 75.9Q2.2 75.9 1.1 74.9Q0.0 73.9 0.0 72.3Z",
        "M77.5 86.7L77.5 59.1Q77.5 57.8 78.2 57.1Q78.9 56.4 80.2 56.4Q80.9 56.4 81.4 56.6Q82.0 56.8 82.5 57.2L99.0 70.8Q100.0 71.7 100.0 72.9Q100.0 74.1 99.0 75.0L82.5 88.6Q82.0 89.0 81.4 89.2Q80.9 89.4 80.2 89.4Q78.9 89.4 78.2 88.7Q77.5 88.0 77.5 86.7Z",
        "M0.0 27.7Q0.0 26.1 1.1 25.1Q2.2 24.1 4.0 24.1L13.9 24.1Q19.3 24.1 23.0 25.9Q26.6 27.7 30.5 32.2L56.9 63.0Q58.8 65.2 60.6 66.5Q62.3 67.8 64.3 68.4Q66.2 69.0 68.6 69.0L83.9 69.0Q85.4 69.0 86.4 70.0Q87.5 71.1 87.5 72.6Q87.5 74.1 86.4 75.2Q85.4 76.2 83.9 76.2L68.4 76.2Q62.9 76.2 59.3 74.5Q55.6 72.7 51.7 68.1L25.4 37.3Q22.5 34.0 20.0 32.7Q17.4 31.3 14.1 31.3L4.0 31.3Q2.2 31.3 1.1 30.3Q0.0 29.3 0.0 27.7Z",
    )

    private val REPEAT_PATHS = listOf(
        "M58.5 11.3Q58.5 9.8 59.3 9.1Q60.1 8.3 61.5 8.3Q62.1 8.3 62.8 8.5Q63.4 8.7 63.9 9.2L81.6 23.9Q82.8 24.9 82.8 26.1Q82.8 27.4 81.6 28.3L63.9 43.0Q63.4 43.4 62.8 43.7Q62.1 43.9 61.5 43.9Q60.1 43.9 59.3 43.1Q58.5 42.3 58.5 40.9Z",
        "M3.9 51.7Q2.3 51.7 1.1 50.5Q0.0 49.4 0.0 47.7L0.0 42.7Q0.0 36.3 2.8 31.7Q5.5 27.2 10.6 24.6Q15.7 22.1 22.7 22.1L65.4 22.1Q67.0 22.1 68.2 23.3Q69.3 24.4 69.3 26.0Q69.3 27.6 68.2 28.8Q67.0 29.9 65.4 29.9L21.9 29.9Q15.6 29.9 11.7 33.6Q7.9 37.2 7.9 43.2L7.9 47.7Q7.9 49.4 6.8 50.5Q5.6 51.7 3.9 51.7Z",
        "M41.5 88.7Q41.5 90.1 40.7 90.9Q40.0 91.7 38.5 91.7Q37.9 91.7 37.2 91.4Q36.6 91.2 36.1 90.8L18.4 76.1Q17.2 75.1 17.2 73.9Q17.2 72.6 18.4 71.6L36.1 57.0Q36.6 56.5 37.2 56.3Q37.9 56.1 38.5 56.1Q40.0 56.1 40.7 56.9Q41.5 57.6 41.5 59.1Z",
        "M98.9 49.5Q97.8 48.3 100.0 52.3Q100.0 50.6 100.0 57.3Q100.0 57.3 97.2 68.2Q100.0 63.6 89.4 75.4Q94.5 72.8 77.3 77.9Q84.3 77.9 34.6 77.9Q34.6 77.9 31.8 76.7Q33.0 77.9 30.7 73.9Q30.7 75.6 31.8 71.2Q30.7 72.3 34.6 70.0Q33.0 70.0 78.1 70.0Q78.1 70.0 88.3 66.4Q84.4 70.0 92.1 56.8Q92.1 62.8 92.1 52.3Q92.1 52.3 93.3 49.5Q92.1 50.6 96.1 48.3Q94.4 48.3 96.1 48.3Z",
    )

    private val HEART_PULSE_PATHS = listOf(
        // 实心心形（100x100 归一化盒）：底尖起笔，两瓣上凸，中顶凹收。
        // ⚠ 2026-10-18：播放页徽标已改为直接复用 [HeartPulseVectorIcon]（心形轮廓 +
        //   ECG 线，与队列面板无尽播放按钮同款）——本 ink path 仅作
        //   MeloXPlaybackModeGlyph 的通用兜底，不再服务于徽标。
        "M50.0 86.0Q21.0 63.0 11.0 44.0Q3.0 29.0 17.0 23.0Q31.0 17.0 42.0 28.0Q50.0 35.0 50.0 41.0Q50.0 35.0 58.0 28.0Q69.0 17.0 83.0 23.0Q97.0 29.0 89.0 44.0Q79.0 63.0 50.0 86.0Z",
    )

    /**
     * `infinity`（无尽播放，0x100BE0）：SF 原版**描边 ∞**。外框 + 左右两瓣，
     * 瓣 = 描边带的内沿，与外框**异向** ⇒ nonzero 掏出内洞成带。
     * ⚠ 保持字体原始绕向，勿统一（见类 KDoc）。
     */
    private val INFINITY_PATHS = listOf(
        "M0.0 50.0Q0.0 43.1 2.7 37.9Q5.4 32.8 10.3 30.0Q15.1 27.1 21.5 27.1Q26.7 27.1 31.2 29.4Q35.7 31.6 40.5 36.2L50.0 45.3L59.5 36.2Q64.3 31.6 68.8 29.4Q73.3 27.1 78.5 27.1Q84.9 27.1 89.7 30.0Q94.6 32.8 97.3 37.9Q100.0 43.1 100.0 50.0Q100.0 56.9 97.3 62.1Q94.6 67.2 89.7 70.0Q84.9 72.9 78.5 72.9Q73.3 72.9 68.8 70.6Q64.3 68.4 59.5 63.8L50.0 54.7L40.5 63.8Q35.7 68.4 31.2 70.6Q26.7 72.9 21.5 72.9Q15.1 72.9 10.3 70.0Q5.4 67.2 2.7 62.1Q0.0 56.9 0.0 50.0Z",
        "M6.9 50.0Q6.9 54.8 8.8 58.4Q10.6 62.0 13.9 64.0Q17.2 65.9 21.5 65.9Q25.2 65.9 28.5 64.1Q31.9 62.4 35.6 58.9L45.1 50.0L35.6 41.1Q31.9 37.6 28.5 35.9Q25.2 34.1 21.5 34.1Q17.2 34.1 13.9 36.0Q10.6 38.0 8.8 41.6Q6.9 45.1 6.9 50.0Z",
        "M54.9 50.0L64.4 58.9Q68.1 62.4 71.5 64.1Q74.8 65.9 78.5 65.9Q82.8 65.9 86.1 64.0Q89.4 62.0 91.2 58.4Q93.1 54.8 93.1 50.0Q93.1 45.1 91.2 41.6Q89.4 38.0 86.1 36.0Q82.8 34.1 78.5 34.1Q74.8 34.1 71.5 35.9Q68.1 37.6 64.4 41.1L54.9 50.0Z",
    )

    private val REPEAT_ONE_PATHS = listOf(
        "M48.3 12.0Q48.3 10.6 49.1 9.8Q49.9 9.1 51.3 9.1Q52.0 9.1 52.6 9.3Q53.2 9.5 53.8 9.9L71.4 24.6Q72.6 25.6 72.6 26.9Q72.5 28.1 71.4 29.1L53.8 43.7Q53.2 44.1 52.6 44.3Q52.0 44.6 51.3 44.6Q49.9 44.6 49.1 43.8Q48.3 43.0 48.3 41.6Z",
        "M3.9 52.3Q2.3 52.3 1.1 51.2Q0.0 50.0 0.0 48.4L0.0 43.4Q0.0 37.1 2.8 32.5Q5.5 27.9 10.6 25.4Q15.7 22.9 22.6 22.9L55.2 22.9Q56.8 22.9 58.0 24.0Q59.1 25.2 59.1 26.8Q59.1 28.4 58.0 29.5Q56.8 30.6 55.2 30.6L21.9 30.6Q15.5 30.6 11.7 34.3Q7.9 37.9 7.9 43.9L7.9 48.4Q7.9 50.0 6.8 51.2Q5.6 52.3 3.9 52.3Z",
        "M41.4 89.3Q41.4 90.7 40.6 91.5Q39.9 92.3 38.4 92.3Q37.8 92.3 37.1 92.0Q36.5 91.8 36.0 91.4L18.3 76.7Q17.2 75.8 17.2 74.5Q17.2 73.2 18.3 72.3L36.0 57.6Q36.5 57.2 37.1 57.0Q37.8 56.8 38.4 56.8Q39.9 56.8 40.6 57.5Q41.4 58.3 41.4 59.7Z",
        "M98.6 50.2Q97.5 49.0 99.8 53.0Q99.8 51.3 99.8 58.0Q99.8 58.0 97.0 68.9Q99.8 64.3 89.2 76.0Q94.2 73.5 77.1 78.5Q84.1 78.5 34.5 78.5Q34.5 78.5 31.8 77.3Q32.9 78.5 30.6 74.5Q30.6 76.2 31.8 71.8Q30.6 73.0 34.5 70.7Q32.9 70.7 77.9 70.7Q77.9 70.7 88.1 67.0Q84.2 70.7 91.9 57.4Q91.9 63.4 91.9 53.0Q91.9 53.0 93.0 50.2Q91.9 51.3 95.8 49.0Q94.2 49.0 95.8 49.0Z",
        "M95.9 38.9Q93.9 38.9 92.8 37.8Q91.7 36.7 91.7 34.7L91.7 15.0L91.2 15.0L86.2 19.0Q85.3 19.7 84.3 19.7Q83.1 19.7 82.3 19.0Q81.6 18.3 81.6 17.2Q81.6 16.5 81.8 15.9Q82.1 15.4 82.8 14.8L89.1 10.0Q90.6 8.9 91.8 8.3Q93.1 7.7 95.0 7.7Q97.2 7.7 98.6 9.1Q100.0 10.5 100.0 12.9L100.0 34.7Q100.0 36.7 98.9 37.8Q97.8 38.9 95.9 38.9Z",
    )

    fun paths(kind: Int): List<String> = when (kind) {
        SHUFFLE -> SHUFFLE_PATHS
        REPEAT_ONE -> REPEAT_ONE_PATHS
        HEART_PULSE -> HEART_PULSE_PATHS
        INFINITY -> INFINITY_PATHS
        else -> REPEAT_PATHS
    }
}

/**
 * 把 [SfGlyphPaths] 的 SVG path data 解析成**单个** Compose [Path]：多轮廓靠
 * `moveTo` 开新子路径，nonzero 填充即 SF 原版字形。
 * 只支持本数据出现的 M / L / Q / Z（全绝对坐标；无隐式重复、无相对指令）。
 * 坐标从 100×100 归一化盒缩放到目标 [w]×[h]。
 */
private fun buildSfGlyphPath(data: String, w: Float, h: Float): Path {
    val sx = w / 100f
    val sy = h / 100f
    var i = 0
    var cmd = ' '
    val n = data.length
    val path = Path()

    fun skipSep() {
        while (i < n && (data[i] == ' ' || data[i] == ',')) i++
    }

    fun nextFloat(): Float {
        skipSep()
        val start = i
        if (i < n && (data[i] == '-' || data[i] == '+')) i++
        while (i < n && (data[i].isDigit() || data[i] == '.')) i++
        return data.substring(start, i).toFloat()
    }

    while (i < n) {
        skipSep()
        if (i >= n) break
        if (data[i].isLetter()) {
            cmd = data[i]
            i++
            if (cmd == 'Z') {
                path.close()
                continue
            }
        }
        when (cmd) {
            'M' -> {
                val x = nextFloat()
                val y = nextFloat()
                path.moveTo(x * sx, y * sy)
            }
            'L' -> {
                val x = nextFloat()
                val y = nextFloat()
                path.lineTo(x * sx, y * sy)
            }
            'Q' -> {
                val cx = nextFloat()
                val cy = nextFloat()
                val x = nextFloat()
                val y = nextFloat()
                path.quadraticTo(cx * sx, cy * sy, x * sx, y * sy)
            }
        }
    }
    return path
}


/**
 * 播放模式字形（随机 / 循环 / 单曲循环）的**唯一几何来源**。
 *
 * 消费方有两处，共用同一份字形 ⇒ 永远「同款」：
 *   1. 播放页「列表按钮右上角徽标」`MeloXNowPlayingCoreControls.ScenePlaybackModeBadge`；
 *   2. [MeloXSymbolIcon] 渲染 [MeloXSymbol.Repeat] / [MeloXSymbol.RepeatOne] 时，
 *      —— 即队列面板顶部模式开关里的「列表循环 / 单曲循环」按钮。
 *
 * [size] = 归一化盒（max-span = size）边长；字形自带 SF 光学边距。
 */
@Composable
fun MeloXPlaybackModeGlyph(
    symbol: MeloXSymbol,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color,
) {
    val kind = when (symbol) {
        MeloXSymbol.Shuffle -> SfGlyphPaths.SHUFFLE
        MeloXSymbol.RepeatOne -> SfGlyphPaths.REPEAT_ONE
        MeloXSymbol.HeartPulse -> SfGlyphPaths.HEART_PULSE
        MeloXSymbol.Infinity -> SfGlyphPaths.INFINITY
        else -> SfGlyphPaths.REPEAT
    }
    val density = LocalDensity.current
    val glyphPath = remember(kind, size, density.density, density.fontScale) {
        val px = with(density) { size.toPx() }
        buildSfGlyphPath(SfGlyphPaths.paths(kind).joinToString(separator = ""), px, px)
    }
    Canvas(modifier = modifier.size(size)) {
        drawPath(glyphPath, color)
    }
}

/**
 * 三个播放模式字形在 SF 字体里的 ink 宽度（字体单位 / upm）。矢量按「max-span = size」
 * 归一化，故 `iconSize` 要乘上该系数才能还原成与原字体字形**同光学尺寸**的矢量。
 */
private val MeloXPlaybackModeInkEm = mapOf(
    MeloXSymbol.Shuffle to 2298f / 2048f,
    MeloXSymbol.Repeat to 2134f / 2048f,
    MeloXSymbol.RepeatOne to 2139f / 2048f,
)

/**
 * [MeloXSymbolIcon] 里 [MeloXSymbol.Repeat] / [MeloXSymbol.RepeatOne] 的矢量兜底：
 * 用 [MeloXPlaybackModeGlyph] 渲染，尺寸按 SF 字体度量换算（**不跟随调用方 Box**，
 * 与 Mic / Plus 同一惯用法），任何 `iconSize` 下都与原字体字形同尺寸。
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而非带 alpha 的 tint，避免多轮廓
 *   填充在重叠处二次混合出深斑（与 mic / plus / star / kebab 一致）。
 */
@Composable
private fun PlaybackModeVectorIcon(
    symbol: MeloXSymbol,
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
) {
    val density = LocalDensity.current
    val inkEm = MeloXPlaybackModeInkEm[symbol] ?: 1f
    val renderSize = remember(density, iconSize, inkEm) {
        with(density) { (iconSize.toPx() * inkEm).toDp() }
    }
    Box(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXPlaybackModeGlyph(
            symbol = symbol,
            size = renderSize,
            color = color.copy(alpha = 1f),
        )
    }
}

/**
 * Apple Music-style search affordance: the magnifier and back arrow share one
 * 300ms path-like transition instead of abruptly replacing the glyph.
 */
@Composable
fun MeloXSearchBackMorphIcon(
    focused: Boolean,
    modifier: Modifier = Modifier,
    color: Color,
    contentDescription: String? = null,
) {
    AnimatedContent(
        targetState = focused,
        transitionSpec = {
            fadeIn(tween(MeloXMotion.IconEnterMillis)) togetherWith
                fadeOut(tween(MeloXMotion.IconExitMillis))
        },
        modifier = modifier,
        label = "search-back-sf-transition",
    ) { isFocused ->
        MeloXSymbolIcon(
            symbol = if (isFocused) MeloXSymbol.ChevronLeft else MeloXSymbol.Search,
            modifier = Modifier,
            color = color,
            iconSize = 24.sp,
            contentDescription = contentDescription,
        )
    }
}

/**
 * **SF 子集字体的进程级 Typeface 缓存**（按 weight，2026-10-14 卡顿修复，源自第三方
 * 0.6.4-jank-fix 包的 F1）。
 *
 * ### 为什么加
 * 原实现里每个 `SfGlyphIcon` 调用点各自
 * `remember(context, weight) { ResourcesCompat.getFont(...); Typeface.create(...) }`
 * —— `remember` 是**每调用点一份**的，一屏 20 个 SF 图标 = 首帧上执行 20 次
 * `getFont`（`TypefaceCompat.findFromCache` 查找 + 首次真实读资源）+ 20 次
 * `Typeface.create`（native 调用）。这些全部压在首帧主线程上，而页面推入转场
 * （`Theme.MeloX.Page` 原生 Activity 转场）恰好在等这一帧。
 *
 * 改后：进程内每个 weight 只做一次真实加载，其余全是 map 查找（微秒级）。
 * **渲染行为逐像素不变** —— 同一个字体文件、同一个 weight、Paint 参数原样。
 *
 * ⚠ `internal` 而非 `private`：播放页核心按钮（`SceneCupertinoGlyph` 的歌词镂空路径，
 * `MeloXNowPlayingCoreControls`）有同一份同步加载代码，同样要走本缓存。
 */
internal object MeloSfTypefaceCache {
    private val lock = Any()
    private val cache = HashMap<Int, android.graphics.Typeface>()

    fun obtain(
        context: android.content.Context,
        weight: Int,
        subsetOnly: Boolean = false,
    ): android.graphics.Typeface? {
        val key = weight or if (subsetOnly) 0x10000 else 0
        synchronized(lock) { cache[key]?.let { return it } }
        // The full SF Pro file also contains the symbol PUA glyphs. Only the
        // four newer symbols missing from that font use the old subset.
        val font = androidx.core.content.res.ResourcesCompat.getFont(
            context,
            if (subsetOnly) R.font.sf_pro_subset else R.font.sf_pro_vf,
        ) ?: return null
        val typeface = android.graphics.Typeface.create(font, weight)
        synchronized(lock) { cache.putIfAbsent(key, typeface) }
        return typeface
    }
}

/**
 * 「更多」横向三点 `…` 的手绘矢量兜底（与 mic / plus / star 同一条品牌矢量路径）。
 *
 * 动机：原本 `MoreVertical` 落到 [SfGlyphIcon] 渲染 `ellipsis`（0x100360）字形，
 *   而该字形在不同 Android / Skia 版本下对同一 glyph 的 `getTextBounds` 因
 *   hinting、`SUBPIXEL_TEXT_FLAG` 差 1~2px，导致省略号三点被算得参差错位
 *   （issue #76 截图里「三点错位」即此）。改走矢量，彻底脱离字体度量，跨设备一致。
 *
 * ⚠ **保持横向 `…` 形态，不改成竖向**：原版落到 `ellipsis`（0x100360）横向省略号，
 *   用户机型（vivo）上本就显示为横向三点、且视为「正常」；问题只在部分机型三点被
 *   算得参差，故矢量必须沿用横向排布，只把三点画整齐。
 *
 * 三个实心圆横向等距排列、垂直居中，尺寸随调用方 Box 自适应（不依赖任何字体度量）：
 *   - 圆点半径 = min(w,h) × 0.10
 *   - 左右两点到中心的偏移 = min(w,h) × 0.30 ⇒ 三点跨度约 0.80·min，上下留白约 0.10·min
 *
 * ⚠ alpha 走图层级 [androidx.compose.ui.draw.alpha] 而非带 alpha 的 tint（与
 *   mic / plus / star 一致）：单色 fill 不会被双重混合，但仍统一此惯用法。
 */
@Composable
internal fun HeartPulseVectorIcon(
    modifier: Modifier,
    color: Color,
    contentDescription: String?,
) {
    Canvas(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val m = min(size.width, size.height)
        val stroke = m * 0.075f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 1f).toArgb()
            this.style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = stroke
            this.strokeJoin = android.graphics.Paint.Join.ROUND
            this.strokeCap = android.graphics.Paint.Cap.ROUND
        }
        drawIntoCanvas { canvas ->
            val nx = { x: Float -> x * size.width }
            val ny = { y: Float -> y * size.height }
            // 心形轮廓
            val heart = android.graphics.Path().apply {
                moveTo(nx(0.5f), ny(0.34f))
                cubicTo(nx(0.5f), ny(0.22f), nx(0.30f), ny(0.12f), nx(0.16f), ny(0.24f))
                cubicTo(nx(0.02f), ny(0.36f), nx(0.04f), ny(0.56f), nx(0.20f), ny(0.68f))
                cubicTo(nx(0.32f), ny(0.77f), nx(0.44f), ny(0.84f), nx(0.5f), ny(0.90f))
                cubicTo(nx(0.56f), ny(0.84f), nx(0.68f), ny(0.77f), nx(0.80f), ny(0.68f))
                cubicTo(nx(0.96f), ny(0.56f), nx(0.98f), ny(0.36f), nx(0.84f), ny(0.24f))
                cubicTo(nx(0.70f), ny(0.12f), nx(0.5f), ny(0.22f), nx(0.5f), ny(0.34f))
                close()
            }
            canvas.nativeCanvas.drawPath(heart, paint)
            // 贯穿的心跳线（ECG）：独立细笔 + 压低振幅 + 圆滑转角。
            // 小尺寸（队列按钮 ~20dp）下生硬折角与深尖峰会糊成一团，故：
            //   ① 笔触取轮廓的 0.72 倍，与轮廓拉开层次；
            //   ② 振幅压到中线 ±0.08（R 峰 0.44 / S 谷 0.60），不再是纵贯心形的深 V；
            //   ③ 全部 quadTo 圆角过渡，单 Path 连续无断点，首尾贴心形内缘。
            val ecgStroke = stroke * 0.72f
            val ecgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.copy(alpha = 1f).toArgb()
                this.style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = ecgStroke
                this.strokeJoin = android.graphics.Paint.Join.ROUND
                this.strokeCap = android.graphics.Paint.Cap.ROUND
            }
            val ecg = android.graphics.Path().apply {
                moveTo(nx(0.10f), ny(0.52f))
                lineTo(nx(0.38f), ny(0.52f))
                quadTo(nx(0.44f), ny(0.50f), nx(0.49f), ny(0.52f))
                lineTo(nx(0.55f), ny(0.52f))
                quadTo(nx(0.58f), ny(0.44f), nx(0.61f), ny(0.52f))
                quadTo(nx(0.64f), ny(0.60f), nx(0.67f), ny(0.52f))
                lineTo(nx(0.72f), ny(0.52f))
                quadTo(nx(0.76f), ny(0.49f), nx(0.80f), ny(0.52f))
                lineTo(nx(0.90f), ny(0.52f))
            }
            canvas.nativeCanvas.drawPath(ecg, ecgPaint)
        }
    }
}

@Composable
private fun MoreVerticalVectorIcon(
    modifier: Modifier,
    color: Color,
    contentDescription: String?,
) {
    Canvas(
        modifier = modifier
            .alpha(color.alpha)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val m = min(size.width, size.height)
        val dotR = m * 0.10f
        val gap = m * 0.30f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 1f).toArgb()
        }
        drawIntoCanvas { canvas ->
            for (dx in listOf(-gap, 0f, gap)) {
                canvas.nativeCanvas.drawCircle(cx + dx, cy, dotR, paint)
            }
        }
    }
}

@Composable
private fun SfGlyphIcon(
    codePoint: Int,
    modifier: Modifier,
    color: Color,
    iconSize: TextUnit,
    contentDescription: String?,
    weight: FontWeight,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val subsetOnly = codePoint in MeloSfSubsetOnlyCodePoints
    val typeface = androidx.compose.runtime.remember(context, weight, subsetOnly) {
        MeloSfTypefaceCache.obtain(context, weight.weight, subsetOnly)
    }
    // 保持原实现的 fail-fast：字体加载失败在组合期抛，而不是拖到绘制期 NPE。
    val paintTypeface = typeface ?: error("SF Symbols font could not be loaded")
    androidx.compose.foundation.Canvas(
        modifier = modifier,
    ) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color.toArgb()
                this.typeface = paintTypeface
                // ★ 尺寸旋钮在这里生效（2026-10-25 拆旋钮）：SF 字形只受
                //   [MeloXSfGlyphScale] 控制，默认 1f ⇒ 墨迹 = iconSize × ink_h/em，
                //   与 0.6.5 逐像素一致。
                //   ⚠ 刻意**不**在 modifier 上叠 `meloXIconDrawingScale()`：那样
                //     `getTextBounds` 量不到缩放（chevron 的 pitch 会算错 17%）。
                textSize = with(density) { (iconSize.value * MeloXSfGlyphScale).sp.toPx() }
                textAlign = android.graphics.Paint.Align.LEFT
                // ⚠ 这里**不要**再设 `fontFeatureSettings = "'ss16' 1"`。
                //
                //   那行从 2026-02（0.4.1-Beta）起就写在这里，但一直渲染的是
                //   `sf_pro_subset.ttf` —— 该子集字体的 GSUB **根本没有 ss16**
                //   （只有 calt / ccmp / frac / locl），所以它是一句从未生效的死代码。
                //
                //   0.6.5 上游把图标字体换成完整的 `sf_pro_vf.ttf` 之后，ss16 突然
                //   活了：它的 lookup 是 `gid -> gid+1`，覆盖 2924 个字形，而 SF Pro
                //   的符号字形成对排列（标准尺寸 / +1 放大变体）。逐字形实测 25 个
                //   MeloX 符号，墨迹高度**统一 ×1.28**（24/25 个 > 1.2，见 gear 2211×2185
                //   → 2891×2854），于是全部图标都撑破调用方的 18/20/24dp 占位盒 ——
                //   2026-10-22 用户反馈「相当大部分图标变得异常巨大」就是这个。
                //
                //   删掉后墨迹与旧子集逐点一致（25 个符号高度比中位数 1.000）。
                //   `MeloXNowPlayingCoreControls` 里两处复刻本类 Paint 参数的地方
                //   （chevron 墨迹测量 / 队列选中态镂空）同步删了，三处必须保持一致。
            }
            val glyph = String(Character.toChars(codePoint))
            val bounds = android.graphics.Rect()
            paint.getTextBounds(glyph, 0, glyph.length, bounds)
            val x = size.width / 2f - (bounds.left + bounds.right) / 2f
            val baseline = size.height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.nativeCanvas.drawText(glyph, x, baseline, paint)
        }
    }
}

@Composable
fun MeloXActionIcon(
    token: String,
    modifier: Modifier = Modifier,
    color: Color,
    enabled: Boolean = true,
    contentDescription: String? = null,
    // ★ SF 字形是按字号渲染的，`Modifier.size(...)` 只是占位框、不裁剪溢出墨迹。
    //   默认 24.sp 对「墨迹近乎满 em」的字形（Share / Plus / 方框类）会溢出到
    //   ~26dp 高 —— 放在 30dp 圆底按钮里就会把圆底顶满。需要贴合框时显式调小。
    iconSize: TextUnit = 24.sp,
) {
    val symbol = when (token) {
        "◷" -> MeloXSymbol.Clock
        "+", "＋" -> MeloXSymbol.Plus
        "↓×" -> MeloXSymbol.Download
        "↗" -> MeloXSymbol.Share
        "✉" -> MeloXSymbol.Mail
        "◎", "◌" -> MeloXSymbol.Message
        "i", "#" -> MeloXSymbol.Info
        "♡", "♥" -> MeloXSymbol.Heart
        "▣", "⇥", "♬" -> MeloXSymbol.List
        "✓" -> MeloXSymbol.Check
        "○" -> MeloXSymbol.Circle
        "♫", "♬" -> MeloXSymbol.MusicNote
        "✦" -> MeloXSymbol.Sparkles
        "❞" -> MeloXSymbol.Quote
        "▣" -> MeloXSymbol.Devices
        "▱" -> MeloXSymbol.Landscape
        "▤" -> MeloXSymbol.PictureInPicture
        "☷", "▦", "▥" -> MeloXSymbol.Apps
        "⌁" -> MeloXSymbol.Microphone
        "▰" -> MeloXSymbol.Storage
        "⚙" -> MeloXSymbol.Settings
        "ⓘ" -> MeloXSymbol.Info
        "⌘" -> MeloXSymbol.Bug
        "↑" -> MeloXSymbol.ArrowUp
        "↓" -> MeloXSymbol.Download
        "‹" -> MeloXSymbol.ChevronLeft
        "›" -> MeloXSymbol.ChevronRight
        "•••", "…" -> MeloXSymbol.Ellipsis
        "×" -> MeloXSymbol.Xmark
        "♪" -> MeloXSymbol.MusicNote
        "↻" -> MeloXSymbol.Refresh
        else -> MeloXSymbol.Unknown
    }
    val semanticLabel = contentDescription ?: when (symbol) {
        MeloXSymbol.Clock -> "历史记录"
        MeloXSymbol.Plus -> "添加"
        MeloXSymbol.Download -> "下载"
        MeloXSymbol.Share -> "分享"
        MeloXSymbol.Mail -> "私信"
        MeloXSymbol.Message -> "消息"
        MeloXSymbol.Info -> "信息"
        MeloXSymbol.Heart -> "收藏"
        MeloXSymbol.Star -> "收藏"
        MeloXSymbol.List -> "列表"
        MeloXSymbol.Check -> "完成"
        MeloXSymbol.MusicNote -> "音乐"
        MeloXSymbol.Sparkles -> "智能功能"
        MeloXSymbol.Quote -> "引用"
        MeloXSymbol.Devices -> "设备"
        MeloXSymbol.Landscape -> "横屏"
        MeloXSymbol.PictureInPicture -> "画中画"
        MeloXSymbol.Apps -> "页面布局"
        MeloXSymbol.Microphone -> "听歌识曲"
        MeloXSymbol.Storage -> "存储"
        MeloXSymbol.Settings -> "设置"
        MeloXSymbol.Bug -> "诊断"
        MeloXSymbol.Play -> "播放"
        MeloXSymbol.Pause -> "暂停"
        MeloXSymbol.Previous -> "上一首"
        MeloXSymbol.Next -> "下一首"
        MeloXSymbol.Shuffle -> "随机播放"
        MeloXSymbol.Repeat -> "重复播放"
        MeloXSymbol.Volume -> "音量"
        MeloXSymbol.Queue -> "播放队列"
        MeloXSymbol.MoreVertical -> "更多操作"
        MeloXSymbol.Circle -> "未选择"
        MeloXSymbol.CheckCircle -> "已选择"
        MeloXSymbol.ArrowUp -> "上移"
        MeloXSymbol.ArrowDown -> "下移"
        MeloXSymbol.ChevronLeft -> "返回"
        MeloXSymbol.ChevronRight -> "打开"
        MeloXSymbol.ChevronUpDown -> "选择选项"
        MeloXSymbol.Ellipsis -> "更多操作"
        MeloXSymbol.Xmark -> "关闭"
        MeloXSymbol.Refresh -> "刷新"
        MeloXSymbol.Unknown -> "未知操作图标 $token"
        else -> symbol.sfSymbolName
    }
    MeloXSymbolIcon(
        symbol = symbol,
        modifier = modifier,
        color = color.copy(alpha = if (enabled) color.alpha else color.alpha * 0.38f),
        variant = if (token == "♥") MeloXSymbolVariant.Fill else MeloXSymbolVariant.Regular,
        iconSize = iconSize,
        contentDescription = semanticLabel,
    )
}
