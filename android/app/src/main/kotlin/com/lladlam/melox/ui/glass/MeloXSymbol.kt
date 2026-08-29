package com.lladlam.melox.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.ui.animation.MeloXMotion

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
    // Material Symbols uses a taller font box than SF Symbols. Rendering the
    // glyph at a slightly smaller em size leaves a real optical inset inside
    // callers' 18/20/24dp icon boxes, instead of clipping the gear and arrows
    // at their ascender/descender edges.
    val codePoint = requireNotNull(MeloXSfSymbolCodePoints[symbol.sfSymbolName]) {
        "Missing SF Symbol mapping: ${symbol.sfSymbolName}"
    }
    SfGlyphIcon(
        codePoint = codePoint,
        modifier = modifier,
        color = color,
        iconSize = iconSize,
        contentDescription = contentDescription,
        weight = if (variant == MeloXSymbolVariant.Fill) FontWeight.Medium else FontWeight.Normal,
    )
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
    val typeface = androidx.compose.runtime.remember(context, weight) {
        val font = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.sf_pro_subset)
            ?: error("SF Symbols font could not be loaded")
        android.graphics.Typeface.create(font, weight.weight)
    }
    androidx.compose.foundation.Canvas(
        modifier = modifier,
    ) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color.toArgb()
                this.typeface = typeface
                textSize = with(density) { iconSize.toPx() }
                textAlign = android.graphics.Paint.Align.LEFT
                fontFeatureSettings = "'ss16' 1"
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
        contentDescription = semanticLabel,
    )
}
