package com.lladlam.melox.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of the iOS EVA title-card engine (`Features/Player/Lyrics/EVA`).
 *
 * A lyric line is split into a deterministic set of typographic blocks. The
 * family (solo / short / long / latin) decides which compositions are allowed,
 * and a stable per-session shuffle picks one variant per line so repeated lines
 * do not always look identical.
 */
internal enum class EvaLayoutFamily(val seed: Long) {
    Solo(0xC2B2AE3D27D4EB4FUL.toLong()),
    Latin(0x165667B19E3779F9UL.toLong()),
    Long(0x85EBCA77C2B2AE63UL.toLong()),
    Short(0x27D4EB2F165667C5UL.toLong()),
}

internal enum class EvaAlignment { Leading, Center, Trailing }

internal data class EvaTextBlock(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val vertical: Boolean,
    val alignment: EvaAlignment,
    val sizeScale: Float,
    val widthScale: Float,
)

internal data class EvaComposition(val blocks: List<EvaTextBlock>)

internal object EvaLayoutEngine {
    private const val SEPARATORS = "、，,。！？!?；;：:—–-"

    fun normalizedDisplayText(source: String): String {
        val text = source.split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ")
        return text.ifEmpty { "……" }
    }

    fun isPredominantlyLatin(text: String): Boolean {
        var visible = 0
        var latin = 0
        for (codePoint in text.codePoints()) {
            val character = String(Character.toChars(codePoint))
            if (character.isBlank() || character.all { it.isPunctuationOrSymbol() }) continue
            visible++
            if (codePoint < 128 && character[0].isLetter()) latin++
        }
        return visible > 0 && latin.toFloat() / visible >= .7f
    }

    fun family(source: String): EvaLayoutFamily {
        val text = normalizedDisplayText(source)
        val count = text.codePointCount(0, text.length)
        if (count == 1) return EvaLayoutFamily.Solo
        if (isPredominantlyLatin(text) && count > 7) return EvaLayoutFamily.Latin
        if (count > 14) return EvaLayoutFamily.Long
        return EvaLayoutFamily.Short
    }

    fun composition(source: String, sessionSeed: Long, sequence: Int): EvaComposition {
        val text = normalizedDisplayText(source)
        val characters = text.codePoints().toArray().map { String(Character.toChars(it)) }
        return when (family(text)) {
            EvaLayoutFamily.Solo -> soloComposition(
                text,
                shuffledVariant(sessionSeed, sequence, 3, EvaLayoutFamily.Solo),
            )
            EvaLayoutFamily.Latin -> when (
                shuffledVariant(sessionSeed, sequence, 3, EvaLayoutFamily.Latin)
            ) {
                0 -> latinComposition(text)
                1 -> centeredLatinComposition(text)
                else -> latinPosterComposition(text)
            }
            EvaLayoutFamily.Long -> when (
                shuffledVariant(sessionSeed, sequence, 3, EvaLayoutFamily.Long)
            ) {
                0 -> stackedComposition(characters)
                1 -> bandedComposition(characters)
                else -> columnComposition(characters)
            }
            EvaLayoutFamily.Short -> when (
                shuffledVariant(sessionSeed, sequence, 5, EvaLayoutFamily.Short)
            ) {
                0 -> cornerComposition(characters)
                1 -> verticalLeadComposition(characters)
                2 -> horizontalLeadComposition(characters)
                3 -> offsetComposition(characters)
                else -> centeredVerticalComposition(text)
            }
        }
    }

    private fun shuffledVariant(
        sessionSeed: Long,
        sequence: Int,
        count: Int,
        family: EvaLayoutFamily,
    ): Int {
        val cycle = sequence / count
        val position = sequence % count
        val order = MutableList(count) { it }
        var state = splitMix64(sessionSeed xor family.seed xor (cycle.toLong() * -7046029254386353131L))
        if (count > 1) {
            for (index in count - 1 downTo 1) {
                state = splitMix64(state + index)
                val swapIndex = ((state ushr 1) % (index + 1)).toInt()
                val temporary = order[index]
                order[index] = order[swapIndex]
                order[swapIndex] = temporary
            }
        }
        return order[position]
    }

    private fun splitMix64(value: Long): Long {
        var z = value + -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    private fun titleParts(characters: List<String>): Pair<String, String> {
        if (characters.isEmpty()) return "……" to "……"
        val middle = characters.size / 2.0
        val separatorIndex = characters.indices
            .filter { it > 0 && it < characters.lastIndex && isSeparator(characters[it]) }
            .minByOrNull { (it - middle).absoluteValue }
        val split = separatorIndex ?: min(
            ((characters.size * .42).roundToInt()).coerceAtLeast(1),
            characters.lastIndex.coerceAtLeast(1),
        )
        val leading = cleanedText(characters.subList(0, split))
        val trailing = cleanedText(
            characters.subList(split, characters.size).dropWhile(::isSeparator),
        )
        return (leading.ifEmpty { characters.first() }) to
            (trailing.ifEmpty { characters.last() })
    }

    private fun cleanedText(characters: List<String>): String =
        characters.joinToString("").trim().trim(*SEPARATORS.toCharArray())

    private fun isSeparator(character: String): Boolean =
        character.isNotEmpty() && character.all { it in SEPARATORS }

    private fun cornerComposition(characters: List<String>): EvaComposition {
        val (leading, trailingText) = titleParts(characters)
        val trailing = trailingText.codePoints().toArray().map { String(Character.toChars(it)) }
        val bend = maxOf(trailing.size / 2, if (trailing.size > 1) 1 else 0)
        val upperTrailing = cleanedText(trailing.subList(0, bend))
        val lowerTrailing = cleanedText(trailing.subList(bend, trailing.size))
        val blocks = mutableListOf(
            block("corner-leading", leading, .01f, .04f, .49f, .46f, false, EvaAlignment.Leading, 1f, .9f),
            punctuationBlock("corner-punctuation", .39f, .18f, .18f, .3f),
        )
        if (upperTrailing.isNotEmpty()) {
            blocks += block("corner-upper-trailing", upperTrailing, .54f, .03f, .43f, .43f, false, EvaAlignment.Trailing, 1.02f, .9f)
        }
        if (lowerTrailing.isNotEmpty()) {
            blocks += block("corner-lower-trailing", lowerTrailing, .72f, .39f, .25f, .58f, true, EvaAlignment.Trailing, 1f, .92f)
        }
        return EvaComposition(blocks)
    }

    private fun verticalLeadComposition(characters: List<String>): EvaComposition {
        val (leading, trailing) = titleParts(characters)
        return EvaComposition(
            listOf(
                block("vertical-lead", leading, .03f, .05f, .27f, .88f, true, EvaAlignment.Leading, 1f, .92f),
                punctuationBlock("vertical-lead-punctuation", .22f, .5f, .2f, .31f),
                block("vertical-lead-trailing", trailing, .34f, .52f, .62f, .4f, false, EvaAlignment.Trailing, 1.04f, .86f),
            ),
        )
    }

    private fun horizontalLeadComposition(characters: List<String>): EvaComposition {
        val (leading, trailing) = titleParts(characters)
        return EvaComposition(
            listOf(
                block("horizontal-lead", leading, .02f, .06f, .66f, .42f, false, EvaAlignment.Leading, 1.02f, .88f),
                punctuationBlock("horizontal-lead-punctuation", .51f, .12f, .19f, .3f),
                block("horizontal-lead-trailing", trailing, .7f, .07f, .27f, .88f, true, EvaAlignment.Trailing, 1f, .9f),
            ),
        )
    }

    private fun offsetComposition(characters: List<String>): EvaComposition {
        val split = min(
            ((characters.size * .78).roundToInt()).coerceAtLeast(1),
            characters.lastIndex.coerceAtLeast(1),
        )
        val upper = cleanedText(characters.subList(0, split))
        val lower = cleanedText(characters.subList(split, characters.size))
        return EvaComposition(
            listOf(
                block("offset-upper", upper.ifEmpty { characters.first() }, .06f, .06f, .88f, .44f, false, EvaAlignment.Leading, 1f, .82f),
                block("offset-lower", lower.ifEmpty { characters.last() }, .08f, .56f, .52f, .36f, false, EvaAlignment.Leading, 1.08f, .9f),
            ),
        )
    }

    private fun centeredVerticalComposition(text: String): EvaComposition =
        EvaComposition(
            listOf(block("centered-vertical", text, .36f, .03f, .28f, .94f, true, EvaAlignment.Center, 1f, .94f)),
        )

    private fun soloComposition(text: String, variant: Int): EvaComposition {
        val origins = listOf(
            .04f to EvaAlignment.Leading,
            .31f to EvaAlignment.Center,
            .58f to EvaAlignment.Trailing,
        )
        val (x, alignment) = origins[variant.coerceIn(0, origins.lastIndex)]
        return EvaComposition(listOf(block("solo", text, x, .03f, .38f, .88f, false, alignment, 1f, .86f)))
    }

    private fun stackedComposition(characters: List<String>): EvaComposition {
        val targetRows = ((characters.size + 5) / 6).coerceIn(2, 5)
        val chunkLength = ((characters.size + targetRows - 1) / targetRows).coerceAtLeast(1)
        val chunks = characters.chunked(chunkLength)
        val rowHeight = .88f / chunks.size
        return EvaComposition(
            chunks.mapIndexed { index, chunk ->
                val indented = index % 3 != 0
                val trailing = index % 2 != 0
                block(
                    "stacked-$index",
                    chunk.joinToString(""),
                    if (indented) .11f else .04f,
                    .04f + index * rowHeight * .94f,
                    if (indented) .84f else .91f,
                    rowHeight * 1.08f,
                    false,
                    if (trailing) EvaAlignment.Trailing else EvaAlignment.Leading,
                    if (index == chunks.lastIndex) 1.05f else 1f,
                    if (trailing) .82f else .9f,
                )
            },
        )
    }

    private fun bandedComposition(characters: List<String>): EvaComposition {
        val middle = (characters.size + 1) / 2
        return EvaComposition(
            bandBlocks(characters.subList(0, middle), "upper", .03f) +
                bandBlocks(characters.subList(middle, characters.size), "lower", .52f),
        )
    }

    private fun bandBlocks(characters: List<String>, prefix: String, y: Float): List<EvaTextBlock> {
        val first = characters.firstOrNull() ?: return emptyList()
        val remainder = characters.drop(1).joinToString("")
        return buildList {
            add(block("band-$prefix-initial", first, .03f, y, .3f, .4f, false, EvaAlignment.Leading, 1f, .84f))
            if (remainder.isNotEmpty()) {
                add(block("band-$prefix-remainder", remainder, .29f, y + .08f, .67f, .29f, false, EvaAlignment.Trailing, 1f, .82f))
            }
        }
    }

    private fun columnComposition(characters: List<String>): EvaComposition {
        val columnCount = ((characters.size + 6) / 7).coerceIn(2, 4)
        val chunkLength = ((characters.size + columnCount - 1) / columnCount).coerceAtLeast(1)
        val columns = characters.chunked(chunkLength)
        val columnWidth = .82f / columns.size
        return EvaComposition(
            columns.mapIndexed { index, chunk ->
                val reversed = columns.size - index - 1
                block(
                    "column-$index",
                    chunk.joinToString(""),
                    .09f + reversed * columnWidth,
                    if (index % 2 == 0) .04f else .13f,
                    columnWidth * .78f,
                    if (index % 2 == 0) .9f else .8f,
                    true,
                    EvaAlignment.Center,
                    1f,
                    if (index % 2 == 0) .94f else .84f,
                )
            },
        )
    }

    private fun latinComposition(text: String): EvaComposition {
        val lines = latinLines(text)
        val rowHeight = .88f / lines.size
        return EvaComposition(
            lines.mapIndexed { index, line ->
                block(
                    "latin-$index",
                    line.uppercase(),
                    if (index % 2 == 0) .03f else .12f,
                    .05f + index * rowHeight * .96f,
                    if (index % 2 == 0) .92f else .83f,
                    rowHeight * 1.08f,
                    false,
                    if (index % 2 == 0) EvaAlignment.Leading else EvaAlignment.Trailing,
                    if (index == 0) 1f else .88f,
                    if (index % 2 == 0) .84f else .74f,
                )
            },
        )
    }

    private fun centeredLatinComposition(text: String): EvaComposition {
        val lines = latinLines(text)
        val rowHeight = .84f / lines.size
        return EvaComposition(
            lines.mapIndexed { index, line ->
                block(
                    "latin-centered-$index",
                    line.uppercase(),
                    if (index % 2 == 0) .1f else .18f,
                    .08f + index * rowHeight * .96f,
                    if (index % 2 == 0) .8f else .64f,
                    rowHeight * 1.06f,
                    false,
                    EvaAlignment.Center,
                    if (index == lines.lastIndex) 1.05f else 1f,
                    if (index % 2 == 0) .78f else .9f,
                )
            },
        )
    }

    private fun latinPosterComposition(text: String): EvaComposition {
        val lines = latinLines(text)
        val headline = lines.firstOrNull() ?: return soloComposition(text, 1)
        val remaining = lines.drop(1)
        val remainingHeight = .4f / remaining.size.coerceAtLeast(1)
        return EvaComposition(
            buildList {
                add(block("latin-poster-headline", headline.uppercase(), .03f, .05f, .94f, .48f, false, EvaAlignment.Leading, 1f, .76f))
                remaining.forEachIndexed { index, line ->
                    add(
                        block(
                            "latin-poster-support-$index",
                            line.uppercase(),
                            if (index % 2 == 0) .12f else .26f,
                            .55f + index * remainingHeight,
                            if (index % 2 == 0) .82f else .7f,
                            remainingHeight * 1.04f,
                            false,
                            EvaAlignment.Trailing,
                            .9f,
                            .82f,
                        ),
                    )
                }
            },
        )
    }

    private fun latinLines(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.size <= 1) {
            return text.chunked(10).ifEmpty { listOf(text) }
        }
        val targetLength = ((text.length + 2) / 3).coerceAtLeast(6)
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (candidate.length > targetLength && current.isNotEmpty()) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun block(
        id: String,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        vertical: Boolean,
        alignment: EvaAlignment,
        sizeScale: Float,
        widthScale: Float,
    ) = EvaTextBlock(id, text, x, y, width, height, vertical, alignment, sizeScale, widthScale)

    private fun punctuationBlock(id: String, x: Float, y: Float, width: Float, height: Float) =
        block(id, "、", x, y, width, height, false, EvaAlignment.Center, .88f, .7f)
}

internal fun Char.isPunctuationOrSymbol(): Boolean =
    Character.getType(this) in setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt(),
    )

private val evaWarmWhite = Color(0xFFFFFAE8)
private val evaGlow = Color(0xFFE0921F)

@Composable
internal fun MeloXEvaLyricsPanel(
    playback: MeloXPlaybackUiState,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
    active: Boolean = true,
) {
    val state = rememberAlternativeLyrics(playback, active)
    if (state.isUnsynchronized) {
        MeloXUnsynchronizedLyrics(state.lines, modifier)
        return
    }
    val sessionSeed = remember { System.nanoTime() }
    val fontScale = MeloXSettingsRuntime.lyricFontScale.coerceIn(.78f, 1.35f)
    Box(
        modifier.fillMaxSize().background(Color.Black).clickable(onClick = onInteraction),
        contentAlignment = Alignment.Center,
    ) {
        AlternativeLoading(state)
        if (state.lines.isNotEmpty()) {
            val safeIndex = state.index.coerceIn(state.lines.indices)
            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                label = "eva-title-card",
            ) { index ->
                val line = state.lines.getOrNull(index) ?: return@AnimatedContent
                val sequence = remember(state.lines, index) {
                    val family = EvaLayoutEngine.family(line.text)
                    state.lines.take(index).count { EvaLayoutEngine.family(it.text) == family }
                }
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    EvaCompositionCanvas(
                        text = line.text,
                        sessionSeed = sessionSeed,
                        sequence = sequence,
                        fontScale = fontScale,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onSeek = { playback.seekTo(line.timeMs) },
                    )
                    val translation = line.translation?.takeIf { it.isNotBlank() }
                    val nextLine = state.lines.getOrNull(index + 1)
                    when {
                        MeloXSettingsRuntime.showLyricTranslation && translation != null -> EvaFooter(
                            eyebrow = stringResource(R.string.settings_eva_translation_eyebrow),
                            text = translation,
                            line = line,
                            onSeek = { playback.seekTo(line.timeMs) },
                        )
                        nextLine != null -> EvaFooter(
                            eyebrow = stringResource(R.string.settings_eva_up_next_eyebrow),
                            text = nextLine.text,
                            line = nextLine,
                            onSeek = { playback.seekTo(nextLine.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaCompositionCanvas(
    text: String,
    sessionSeed: Long,
    sequence: Int,
    fontScale: Float,
    modifier: Modifier = Modifier,
    onSeek: () -> Unit,
) {
    val composition = remember(text, sessionSeed, sequence) {
        EvaLayoutEngine.composition(text, sessionSeed, sequence)
    }
    BoxWithConstraints(
        modifier.clipToBounds().clickable(onClick = onSeek),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        composition.blocks.forEach { block ->
            val blockWidth = widthPx * block.width
            val blockHeight = heightPx * block.height
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = widthPx * block.x
                        translationY = heightPx * block.y
                    }
                    .size(maxWidth * block.width, maxHeight * block.height)
                    .clipToBounds(),
                contentAlignment = when (block.alignment) {
                    EvaAlignment.Leading -> Alignment.CenterStart
                    EvaAlignment.Center -> Alignment.Center
                    EvaAlignment.Trailing -> Alignment.CenterEnd
                },
            ) {
                if (block.vertical) {
                    EvaVerticalText(block, blockWidth, blockHeight, fontScale)
                } else {
                    EvaHorizontalText(block, blockWidth, blockHeight, fontScale)
                }
            }
        }
    }
}

@Composable
private fun EvaHorizontalText(
    block: EvaTextBlock,
    blockWidth: Float,
    blockHeight: Float,
    fontScale: Float,
) {
    val isLatin = EvaLayoutEngine.isPredominantlyLatin(block.text)
    val fontSizePx = blockHeight * .94f * block.sizeScale * fontScale.coerceIn(.78f, 1.35f)
    val glyphWidth = if (isLatin) .63f else .94f
    val naturalWidth = (fontSizePx * glyphWidth * block.text.codePointCount(0, block.text.length)).coerceAtLeast(1f)
    val fitted = min(block.widthScale, blockWidth / naturalWidth).coerceAtLeast(.18f)
    val density = LocalDensity.current
    Text(
        text = block.text,
        color = evaWarmWhite,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = if (isLatin) FontFamily.Serif else FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { fontSizePx.toSp() },
            letterSpacing = with(density) { (-fontSizePx * if (isLatin) .035f else .065f).toSp() },
            shadow = Shadow(evaGlow.copy(alpha = .82f), blurRadius = 9f),
        ),
        modifier = Modifier.graphicsLayer {
            scaleX = fitted
            transformOrigin = when (block.alignment) {
                EvaAlignment.Leading -> TransformOrigin(0f, .5f)
                EvaAlignment.Center -> TransformOrigin.Center
                EvaAlignment.Trailing -> TransformOrigin(1f, .5f)
            }
        },
    )
}

@Composable
private fun EvaVerticalText(
    block: EvaTextBlock,
    blockWidth: Float,
    blockHeight: Float,
    fontScale: Float,
) {
    val characters = block.text.codePoints().toArray().map { String(Character.toChars(it)) }
    if (characters.isEmpty()) return
    val fontSizePx = min(
        blockWidth * 1.05f,
        blockHeight / characters.size * 1.14f,
    ) * block.sizeScale * fontScale.coerceIn(.78f, 1.35f)
    val density = LocalDensity.current
    Column(
        modifier = Modifier.graphicsLayer {
            scaleX = block.widthScale
            transformOrigin = when (block.alignment) {
                EvaAlignment.Leading -> TransformOrigin(0f, .5f)
                EvaAlignment.Center -> TransformOrigin.Center
                EvaAlignment.Trailing -> TransformOrigin(1f, .5f)
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        characters.forEach { character ->
            Text(
                text = character,
                color = evaWarmWhite,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { fontSizePx.toSp() },
                    lineHeight = with(density) { (fontSizePx * .87f).toSp() },
                    shadow = Shadow(evaGlow.copy(alpha = .82f), blurRadius = 9f),
                ),
            )
        }
    }
}

@Composable
private fun EvaFooter(
    eyebrow: String,
    text: String,
    line: LyricLine,
    onSeek: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onSeek),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            eyebrow,
            color = evaWarmWhite.copy(alpha = .45f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text,
            color = evaWarmWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-.8).sp,
                shadow = Shadow(evaGlow.copy(alpha = .72f), blurRadius = 7f),
            ),
        )
    }
}
