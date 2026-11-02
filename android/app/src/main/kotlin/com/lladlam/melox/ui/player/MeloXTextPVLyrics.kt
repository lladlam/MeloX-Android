// PV Tool — Copyright (c) 2026 DanteAlighieri13210914
// Canvas renderer ported to Kotlin under the PV Tool Non-Commercial License.
package com.lladlam.melox.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.settings.MeloXSettingsRuntime
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private data class TextPVFrame(
    val template: TextPVTemplate,
    val text: String,
    val previousText: String,
    val characters: List<String>,
    val time: Float,
    val segmentTime: Float,
    val animationSpeed: Float,
    val motionIntensity: Float,
    val fontScale: Float,
    val seed: Long,
)

@Composable
internal fun MeloXTextPVLyricsPanel(
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
    val style = MeloXSettingsRuntime.textPVStyle
    val template = remember(style) { TextPVTemplateCatalog.template(style) }
    val phase = remember { Animatable(0f) }
    val speed = MeloXSettingsRuntime.textPVAnimationSpeed.coerceIn(0f, 4f)
    val motion = if (!active || MeloXSettingsRuntime.lyricReduceMotion) 0f else {
        MeloXSettingsRuntime.textPVMotionIntensity.coerceIn(0f, 2f)
    }
    LaunchedEffect(active, speed, motion, style) {
        if (!active || speed <= 0f) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            phase.snapTo(0f)
            phase.animateTo(
                1f,
                tween(
                    durationMillis = (14_000f / speed).toInt().coerceIn(3_500, 140_000),
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    val index = if (state.lines.isEmpty()) 0 else state.index.coerceIn(state.lines.indices)
    val current = state.lines.getOrNull(index)
    val previous = state.lines.getOrNull(index - 1)
    val text = current?.text.orEmpty()
    val fontScale = MeloXSettingsRuntime.lyricFontScale.coerceIn(.8f, 1.25f)
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val segmentTime = phase.value * 14f % 3.5f
    Box(
        modifier.fillMaxSize().background(template.palette.backgroundColor)
            .clickable(onClick = onInteraction),
    ) {
        TextPVCanvas(
            template = template,
            frame = TextPVFrame(
                template = template,
                text = text,
                previousText = previous?.text.orEmpty(),
                characters = text.codePoints().toArray().map { String(Character.toChars(it)) },
                time = phase.value * 14f,
                segmentTime = segmentTime,
                animationSpeed = speed.coerceAtLeast(.001f),
                motionIntensity = motion,
                fontScale = fontScale,
                seed = (playback.mediaId.orEmpty() + text).hashCode().toLong(),
            ),
            measurer = measurer,
            modifier = Modifier.fillMaxSize(),
        )
        TextPVStateOverlay(state)
    }
}

@Composable
private fun TextPVStateOverlay(state: AlternativeLyricsState) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        !state.loading && state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color.White.copy(alpha = .56f))
        }
    }
}

@Composable
private fun TextPVCanvas(
    template: TextPVTemplate,
    frame: TextPVFrame,
    measurer: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val postFX = template.postFX
    val shakeX = if (postFX.shake == 0f || frame.motionIntensity == 0f) 0f else {
        pvSigned(frame.seed, (frame.time * 60).toInt()) * postFX.shake * 15f * frame.motionIntensity
    }
    val shakeY = if (postFX.shake == 0f || frame.motionIntensity == 0f) 0f else {
        pvSigned(frame.seed, (frame.time * 60).toInt() + 1) * postFX.shake * 10f * frame.motionIntensity
    }
    Canvas(
        modifier.graphicsLayer {
            translationX = shakeX
            translationY = shakeY
            val zoom = 1f + postFX.zoom * .5f
            scaleX = zoom
            scaleY = zoom
            rotationZ = postFX.tilt * .3f * 180f / PI.toFloat()
        },
    ) {
        drawRect(template.palette.backgroundColor.copy(alpha = template.backgroundOpacity))
        for (layer in TextPVLayer.entries) {
            template.effects.forEachIndexed { index, effect ->
                if (effect.layer == layer) {
                    drawTextPVEffect(effect, frame, measurer, index)
                }
            }
        }
        if (template.effects.none { it.layer == TextPVLayer.Text }) {
            drawHeroText(
                frame,
                measurer,
                mapOf("fontSize" to 72f, "x" to .5f, "y" to .5f, "color" to "\$text"),
            )
        }
    }
}

private fun DrawScope.drawTextPVEffect(
    effect: TextPVEffect,
    frame: TextPVFrame,
    measurer: TextMeasurer,
    effectIndex: Int,
) {
    val palette = frame.template.palette
    val seed = frame.seed + effectIndex * -3343331850605822205L
    val config = effect.config
    val t = frame.time
    val motion = frame.motionIntensity
    fun color(key: String, fallback: String) = palette.resolve(config.text(key, fallback), fallback)
    fun alpha(key: String, fallback: Float) = config.number(key, fallback)

    when (effect.kind) {
        TextPVEffectKind.BackgroundBlocks -> {
            val count = config.integer("count", 7)
            repeat(count) { index ->
                val x = size.width * (.06f + (index * .137f + t * .01f * motion) % .88f)
                val y = size.height * (.08f + (index * .213f) % .8f)
                drawRect(
                    color = palette.resolve("\$primary").copy(alpha = alpha("alpha", .5f) * .18f),
                    topLeft = Offset(x, y),
                    size = Size(size.width * (.08f + index % 3 * .04f), size.height * .3f),
                )
            }
        }
        TextPVEffectKind.BalancingCircles -> {
            val count = config.integer("count", 5)
            val blue = color("blueColor", "#0028B4")
            val white = color("whiteColor", "#ffffff")
            repeat(count) { index ->
                val center = Offset(
                    size.width * (.2f + (index * .19f + sin(t * .3f + index) * .04f * motion) % .7f),
                    size.height * (.25f + index % 3 * .2f),
                )
                drawCircle(blue.copy(alpha = .16f), size.minDimension * .16f, center)
                drawCircle(white.copy(alpha = .22f), size.minDimension * .1f, center)
            }
        }
        TextPVEffectKind.BigOutlineText -> drawBigOutlineText(frame, measurer, config)
        TextPVEffectKind.BloodSplatter -> {
            val count = config.integer("count", 5)
            val splatterColor = color("color", "#8b0000")
            repeat(count * 9) { index ->
                val x = size.width * pvUnit(seed, index)
                val y = size.height * pvUnit(seed, index + 100)
                val radius = size.minDimension * (.008f + pvUnit(seed, index + 200) * .06f)
                drawCircle(splatterColor.copy(alpha = alpha("alpha", .85f) * .5f), radius, Offset(x, y))
            }
        }
        TextPVEffectKind.BurstLines -> {
            val count = config.integer("rayCount", 20)
            val inner = config.number("innerRadius", .06f) * size.minDimension
            val outer = config.number("outerRadius", .7f) * size.maxDimension
            val center = Offset(
                size.width * config.number("x", .5f),
                size.height * config.number("y", .5f),
            )
            repeat(count) { index ->
                val angle = index * 2f * PI.toFloat() / count + t * config.number("rotSpeed", .03f) * motion
                drawLine(
                    color("color", "#b4b4b4").copy(alpha = alpha("alpha", .25f)),
                    Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                    Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                    config.number("lineWidth", 2f),
                )
            }
        }
        TextPVEffectKind.CenteredSquares -> {
            listOf(
                config.number("outerSize", 320f) to color("borderColor", "#1a1a1a"),
                config.number("midSize", 240f) to color("midColor", "#1a1a1a"),
                config.number("innerSize", 170f) to color("innerColor", "#ffffff"),
            ).forEach { (side, shapeColor) ->
                val half = side / 2f
                drawRect(
                    shapeColor.copy(alpha = if (shapeColor == color("innerColor", "#ffffff")) .9f else .12f),
                    Offset(size.width / 2f - half, size.height / 2f - half),
                    Size(side, side),
                )
            }
        }
        TextPVEffectKind.Checkerboard -> {
            val cell = config.number("cellSize", 64f)
            val first = color("color1", "#f0b8c4")
            val second = color("color2", "#e8a0b0")
            var y = -cell
            var row = 0
            while (y < size.height) {
                var x = -cell
                var column = 0
                while (x < size.width) {
                    if ((row + column) % 2 == 0) {
                        drawRect(first.copy(alpha = alpha("alpha", .08f)), Offset(x, y), Size(cell, cell))
                    } else {
                        drawRect(second.copy(alpha = alpha("alpha", .08f)), Offset(x, y), Size(cell, cell))
                    }
                    x += cell
                    column++
                }
                y += cell
                row++
            }
        }
        TextPVEffectKind.ChromaticAberration -> {
            if (frame.text.isNotEmpty()) {
                val offset = config.number("offset", config.number("amount", 4f))
                val fontSize = size.height * .09f
                val flicker = 1f + sin(t * 8f) * .25f * motion
                listOf(
                    Color.Red.copy(alpha = .28f) to Offset(-offset * flicker, 0f),
                    Color.Cyan.copy(alpha = .28f) to Offset(offset * flicker, 0f),
                ).forEach { (tint, shift) ->
                    drawPvText(
                        measurer, frame.text, Offset(size.width / 2f + shift.x, size.height / 2f + shift.y),
                        fontSize, tint, alpha = .7f,
                    )
                }
            }
        }
        TextPVEffectKind.ColorMask -> drawRect(color("color", "#1a2040").copy(alpha = alpha("alpha", .15f)))
        TextPVEffectKind.CompositionGuides -> {
            val guideColor = color("color", "#ffffff").copy(alpha = alpha("alpha", .35f))
            drawLine(guideColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.5f)
            drawLine(guideColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.5f)
            drawLine(guideColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.5f)
            drawLine(guideColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.5f)
        }
        TextPVEffectKind.ConcentricCircles -> {
            val count = config.integer("count", 5)
            val center = Offset(size.width * config.number("x", .5f), size.height * config.number("y", .5f))
            repeat(count) { index ->
                val radius = size.minDimension * (.12f + index * .1f)
                drawCircle(
                    color("color", "#c8c8c8").copy(alpha = alpha("alpha", .4f)),
                    radius,
                    center,
                    style = Stroke(config.number("strokeWidth", 1f)),
                )
            }
        }
        TextPVEffectKind.CrayonShatter -> drawCrayonShatter(frame, measurer, config)
        TextPVEffectKind.CrimeTape -> drawCrimeTape(frame, measurer, config)
        TextPVEffectKind.CuteOutlineText -> drawCuteOutlineText(frame, measurer, config)
        TextPVEffectKind.DataMonitors -> {
            val count = config.integer("count", 4)
            repeat(count) { index ->
                val width = size.width * .16f
                val height = size.height * .1f
                val x = size.width * (.08f + index % 3 * .24f)
                val y = size.height * (.12f + index * .21f)
                drawRect(color("fillColor", "#000000").copy(alpha = .45f), Offset(x, y), Size(width, height))
                drawRect(color("borderColor", "#ffffff").copy(alpha = .7f), Offset(x, y), Size(width, height), style = Stroke(1.5f))
                repeat(4) { line ->
                    drawLine(
                        color("dataColor", "#ffffff").copy(alpha = .5f),
                        Offset(x + 6f, y + 8f + line * 9f),
                        Offset(x + width - 6f - (index + line) % 3 * 10f, y + 8f + line * 9f),
                        1.5f,
                    )
                }
            }
        }
        TextPVEffectKind.DesktopIcon -> {
            val iconSize = config.number("size", 64f)
            val x = if (config.number("x", 0f) <= 1f) size.width * config.number("x", 0f) else config.number("x", 0f)
            val y = if (config.number("y", 0f) <= 1f) size.height * config.number("y", 0f) else config.number("y", 0f)
            drawRoundRect(Color.White.copy(alpha = .82f), Offset(x, y), Size(iconSize, iconSize), CornerRadius(8f))
            drawRect(color("labelColor", "#5a3a5a").copy(alpha = .8f), Offset(x + 12f, y + 14f), Size(iconSize - 24f, iconSize - 28f))
            drawPvText(
                measurer, config.text("label", ""), Offset(x + iconSize / 2f, y + iconSize + 14f),
                11f, color("labelColor", "#5a3a5a"), maxWidth = iconSize * 2f,
            )
        }
        TextPVEffectKind.DiagonalHatch -> {
            val spacing = config.number("spacing", 8f).coerceAtLeast(2f)
            val hatchColor = color("color", "#888888").copy(alpha = alpha("alpha", .14f))
            val step = spacing
            var start = -size.height
            while (start < size.width) {
                drawLine(hatchColor, Offset(start, size.height), Offset(start + size.height, 0f), config.number("lineWidth", 1f))
                start += step
            }
        }
        TextPVEffectKind.DiagonalSplit -> {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rotation = config.number("initRotation", 0f) + t * config.number("rotSpeed", .25f) * motion
            rotate(rotation * 180f / PI.toFloat(), center) {
                val path = Path().apply {
                    moveTo(-size.width * .2f, center.y - size.height * .05f)
                    lineTo(size.width * 1.2f, center.y - size.height * .2f)
                    lineTo(size.width * 1.2f, center.y + size.height * .05f)
                    lineTo(-size.width * .2f, center.y + size.height * .25f)
                    close()
                }
                drawPath(path, color("color", "#8b1a1a").copy(alpha = alpha("alpha", 1f)))
                repeat(8) { index ->
                    drawLine(
                        Color.White.copy(alpha = .12f),
                        Offset(0f, size.height * index / 7f),
                        Offset(size.width, size.height * index / 7f - size.height * .08f),
                        1f,
                    )
                }
            }
        }
        TextPVEffectKind.DiagonalStructure -> {
            val step = config.number("step", 100f)
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color("color", "#f0f0f0").copy(alpha = alpha("alpha", .3f)),
                    Offset(x, size.height),
                    Offset(x + size.height, 0f),
                    3f,
                )
                x += step
            }
        }
        TextPVEffectKind.DotScreen -> {
            val spacing = config.number("spacing", 8f).coerceAtLeast(3f)
            val radius = config.number("dotRadius", config.number("dotSize", 2f))
            val angle = config.number("angle", 0f)
            val center = Offset(size.width / 2f, size.height / 2f)
            rotate(angle, center) {
                var y = 0f
                while (y < size.height + spacing) {
                    var x = 0f
                    while (x < size.width + spacing) {
                        drawCircle(
                            color("color", "#ffffff").copy(alpha = alpha("alpha", .18f)),
                            radius,
                            Offset(x, y),
                        )
                        x += spacing
                    }
                    y += spacing
                }
            }
        }
        TextPVEffectKind.EdgeClouds -> {
            val count = config.integer("cloudCount", 5)
            repeat(count) { index ->
                val cloudColor = color("color", "#ffffff").copy(alpha = .55f)
                val centerX = if (index % 2 == 0) size.width * .08f else size.width * .92f
                val centerY = size.height * (.18f + index * .16f)
                val radius = config.number("baseRadius", 100f) * (.7f + index % 3 * .12f)
                drawCircle(cloudColor, radius, Offset(centerX, centerY))
                drawCircle(cloudColor, radius * .72f, Offset(centerX + radius * .5f, centerY + radius * .1f))
                drawCircle(cloudColor, radius * .58f, Offset(centerX - radius * .4f, centerY + radius * .16f))
            }
        }
        TextPVEffectKind.FallingText -> {
            val count = config.integer("count", 24)
            val fallingColor = color("color", "#00ff41")
            repeat(count) { index ->
                val x = size.width * pvUnit(seed, index)
                val speed = .04f + pvUnit(seed, index + 50) * .08f
                val y = size.height * ((pvUnit(seed, index + 100) + t * speed * motion) % 1.1f - .05f)
                val fontSize = (config.number("minSize", 24f) +
                    pvUnit(seed, index + 200) * (config.number("maxSize", 62f) - config.number("minSize", 24f)))
                drawPvText(
                    measurer,
                    frame.characters.getOrNull(index % frame.characters.size.coerceAtLeast(1)).orEmpty(),
                    Offset(x, y), fontSize, fallingColor.copy(alpha = .55f),
                )
            }
        }
        TextPVEffectKind.FilmGrain -> {
            val grainAlpha = alpha("alpha", .14f)
            repeat(160) { index ->
                val x = size.width * pvUnit(seed, index)
                val y = size.height * pvUnit(seed, index + 300)
                val shade = if (index % 2 == 0) Color.White else Color.Black
                drawCircle(shade.copy(alpha = grainAlpha * .5f), 1.2f, Offset(x, y))
            }
        }
        TextPVEffectKind.FlowingLines -> {
            val count = config.integer("count", 4)
            repeat(count) { lineIndex ->
                val path = Path()
                val amplitude = config.number("amplitude", 80f)
                val yBase = size.height * (.2f + lineIndex * .17f)
                var x = 0f
                while (x <= size.width) {
                    val y = yBase + sin(x / size.width * 2f * PI.toFloat() + t * config.number("speed", .08f) * motion + lineIndex) * amplitude
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += size.width / 48f
                }
                drawPath(
                    path,
                    color("color", "#4455aa").copy(alpha = alpha("alpha", .15f)),
                    style = Stroke(config.number("strokeWidth", 1f)),
                )
            }
        }
        TextPVEffectKind.FormulaText -> drawFormulaText(frame, measurer, config)
        TextPVEffectKind.GlitchBars -> {
            val barColor = color("color", "#ffffff").copy(alpha = alpha("alpha", .4f))
            repeat(7) { index ->
                val y = size.height * pvUnit(seed, index + (t * 3).toInt() * 7)
                val height = 2f + pvUnit(seed, index + 70) * 16f
                drawRect(barColor, Offset(0f, y), Size(size.width, height))
            }
        }
        TextPVEffectKind.GlowTextCards -> drawGlowTextCards(frame, measurer, config)
        TextPVEffectKind.GradientOverlay -> {
            val top = color("colorTop", "#141828")
            val bottom = color("colorBottom", "#080c14")
            val brush = if (config.text("mode", "linear") == "radial") {
                Brush.radialGradient(
                    listOf(top.copy(alpha = alpha("alpha", .7f)), bottom.copy(alpha = alpha("alpha", .7f))),
                    center = Offset(size.width / 2f, size.height * .4f),
                    radius = size.maxDimension * .7f,
                )
            } else {
                Brush.verticalGradient(
                    listOf(top.copy(alpha = alpha("alpha", .7f)), bottom.copy(alpha = alpha("alpha", .7f))),
                )
            }
            drawRect(brush)
        }
        TextPVEffectKind.HalftoneBlocks -> {
            val spacing = config.number("spacing", 6f)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(
                        color("color", "#5a3a5a").copy(alpha = alpha("alpha", .12f)),
                        config.number("size", 3f),
                        Offset(x, y),
                    )
                    x += spacing
                }
                y += spacing
            }
        }
        TextPVEffectKind.HeroText -> drawHeroText(frame, measurer, config)
        TextPVEffectKind.HudCorners -> {
            val margin = config.number("margin", 20f)
            val arm = config.number("armLength", 40f)
            val lineWidth = config.number("lineWidth", 2f)
            val cornerColor = color("color", "#FFFF00").copy(alpha = alpha("alpha", .9f))
            listOf(
                Offset(margin, margin) to Offset(1f, 1f),
                Offset(size.width - margin, margin) to Offset(-1f, 1f),
                Offset(margin, size.height - margin) to Offset(1f, -1f),
                Offset(size.width - margin, size.height - margin) to Offset(-1f, -1f),
            ).forEach { (point, direction) ->
                drawLine(cornerColor, point, Offset(point.x + arm * direction.x, point.y), lineWidth)
                drawLine(cornerColor, point, Offset(point.x, point.y + arm * direction.y), lineWidth)
            }
        }
        TextPVEffectKind.HudInfoPanel -> {
            val width = config.number("panelWidth", 240f)
            val height = config.number("panelHeight", 420f)
            val x = size.width - width - size.width * .06f
            val y = (size.height - height) / 2f
            drawRect(Color.Black.copy(alpha = .32f), Offset(x, y), Size(width, height))
            drawRect(color("primaryColor", "#FFFF00").copy(alpha = .6f), Offset(x, y), Size(width, height), style = Stroke(1.5f))
            repeat(9) { index ->
                drawLine(
                    color("primaryColor", "#FFFF00").copy(alpha = .35f),
                    Offset(x + 14f, y + 24f + index * 36f),
                    Offset(x + width - 14f, y + 24f + index * 36f),
                    2f,
                )
            }
            repeat(5) { index ->
                drawCircle(
                    color("alertColor", "#FF0000").copy(alpha = .7f),
                    3f,
                    Offset(x + width - 26f, y + 24f + index * 68f),
                )
            }
        }
        TextPVEffectKind.HudStatusText -> drawHudStatusText(frame, measurer, config)
        TextPVEffectKind.LayeredText -> drawLayeredText(frame, measurer, config)
        TextPVEffectKind.LightSpot -> {
            drawRect(
                Brush.radialGradient(
                    listOf(
                        color("color", "#ffffff").copy(alpha = alpha("alpha", .5f)),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * config.number("x", .5f), size.height * config.number("y", .08f)),
                    radius = size.maxDimension * config.number("radius", .4f),
                ),
            )
        }
        TextPVEffectKind.MotionBrackets -> {
            val bracketColor = color("color", "#ffffff").copy(alpha = alpha("alpha", .7f))
            val lineWidth = config.number("lineWidth", 1f)
            val margin = size.minDimension * .12f
            listOf(
                Offset(margin, margin) to Offset(1f, 1f),
                Offset(size.width - margin, margin) to Offset(-1f, 1f),
                Offset(margin, size.height - margin) to Offset(1f, -1f),
                Offset(size.width - margin, size.height - margin) to Offset(-1f, -1f),
            ).forEach { (point, direction) ->
                drawLine(bracketColor, point, Offset(point.x + 28f * direction.x, point.y), lineWidth)
                drawLine(bracketColor, point, Offset(point.x, point.y + 28f * direction.y), lineWidth)
            }
            if (config.flag("showNodes", false)) {
                repeat(7) { index ->
                    drawCircle(
                        color("nodeColor", "#3264E6").copy(alpha = .7f),
                        2.5f,
                        Offset(size.width * pvUnit(seed, index), size.height * pvUnit(seed, index + 50)),
                    )
                }
            }
        }
        TextPVEffectKind.NoiseText -> drawNoiseText(frame, measurer, config)
        TextPVEffectKind.PerspectiveGrid -> {
            val lineColor = color("color", "#999999").copy(alpha = alpha("alpha", .5f))
            val horizon = size.height * .42f
            repeat(13) { index ->
                val bottomX = size.width * index / 12f
                drawLine(lineColor, Offset(size.width / 2f, horizon), Offset(bottomX, size.height), config.number("lineWidth", 1f))
            }
            repeat(8) { index ->
                val y = horizon + (size.height - horizon) * (index * index / 49f)
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), config.number("lineWidth", 1f))
            }
        }
        TextPVEffectKind.PinkGrid -> {
            val cell = config.number("cellSize", 50f)
            val lineColor = color("lineColor", "#ffffff").copy(alpha = .55f)
            val lineWidth = config.number("lineWidth", 2f)
            var x = (t * config.number("speed", 30f) * motion) % cell
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), lineWidth)
                x += cell
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidth)
                y += cell
            }
        }
        TextPVEffectKind.PinkStripes -> {
            val stripeWidth = config.number("stripeWidth", 150f)
            val pink = color("pinkColor", "#fbbdbe").copy(alpha = .45f)
            val angle = config.number("angle", -45f) + sin(t * config.number("speed", .3f) * motion) * 4f
            rotate(angle, Offset(size.width / 2f, size.height / 2f)) {
                var x = -size.maxDimension
                while (x < size.maxDimension * 2f) {
                    drawRect(pink, Offset(x, -size.maxDimension), Size(stripeWidth / 2f, size.maxDimension * 3f))
                    x += stripeWidth
                }
            }
        }
        TextPVEffectKind.PixelTypewriter -> drawPixelTypewriter(frame, measurer, config)
        TextPVEffectKind.PixelWindow -> drawPixelWindow(frame, measurer, config)
        TextPVEffectKind.Planet -> {
            val center = Offset(size.width * .74f, size.height * .25f)
            val radius = config.number("radius", 120f)
            drawCircle(color("color", "#ffffff").copy(alpha = .82f), radius, center)
            drawCircle(Color.Black.copy(alpha = .18f), radius * .86f, Offset(center.x + radius * .2f, center.y))
            rotate(-22f, center) {
                drawOval(
                    Color.White.copy(alpha = .38f),
                    topLeft = Offset(center.x - radius * 1.5f, center.y - radius * .22f),
                    size = Size(radius * 3f, radius * .44f),
                    style = Stroke(3f),
                )
            }
        }
        TextPVEffectKind.PulsingCircle -> {
            val center = Offset(
                size.width * config.number("x", .5f),
                size.height * config.number("y", .5f),
            )
            val pulse = 1f + sin(t * config.number("animSpeed", .2f) * 2f * PI.toFloat()) * .06f * motion
            drawCircle(
                color("strokeColor", "#ffffff").copy(alpha = .55f),
                config.number("radius", 250f) * pulse,
                center,
                style = Stroke(config.number("strokeWidth", 8f)),
            )
            drawCircle(
                color("outerStrokeColor", "#ecbfc0").copy(alpha = .5f),
                config.number("radius", 250f) * pulse * 1.12f,
                center,
                style = Stroke(config.number("outerStrokeWidth", 3f)),
            )
        }
        TextPVEffectKind.RadialRectangles -> {
            val count = config.integer("count", 14)
            val center = Offset(
                size.width * config.number("x", .47f),
                size.height * config.number("y", .48f),
            )
            repeat(count) { index ->
                val angle = index * 360f / count + t * config.number("rotSpeed", .08f) * 30f * motion
                rotate(angle, center) {
                    drawRect(
                        color("baseColor", "#1133aa").copy(alpha = .12f),
                        Offset(center.x - size.width * .08f, center.y - size.height * .32f),
                        Size(size.width * .16f, size.height * .64f),
                    )
                }
            }
        }
        TextPVEffectKind.ScalloppedBorder -> {
            val radius = config.number("circleRadius", 80f) * .22f
            val scallopColor = color("color", "#ffffff").copy(alpha = .85f)
            var x = 0f
            while (x < size.width) {
                drawCircle(scallopColor, radius, Offset(x, 0f))
                drawCircle(scallopColor, radius, Offset(x, size.height))
                x += radius * 2f
            }
            var y = 0f
            while (y < size.height) {
                drawCircle(scallopColor, radius, Offset(0f, y))
                drawCircle(scallopColor, radius, Offset(size.width, y))
                y += radius * 2f
            }
        }
        TextPVEffectKind.Scanlines -> {
            val spacing = config.number("spacing", 3f).coerceAtLeast(1f)
            val scanColor = color("color", "#000000").copy(alpha = alpha("alpha", .18f))
            var y = 0f
            while (y < size.height) {
                drawRect(scanColor, Offset(0f, y), Size(size.width, spacing / 2f))
                y += spacing
            }
        }
        TextPVEffectKind.ScatteredShapes -> {
            val count = config.integer("count", 18)
            val shapeColor = color("color", "#ffffff").copy(alpha = alpha("alpha", .3f))
            repeat(count) { index ->
                val x = size.width * pvUnit(seed, index)
                val y = size.height * pvUnit(seed, index + 40)
                val shapeSize = config.number("minSize", 2f) +
                    pvUnit(seed, index + 80) * (config.number("maxSize", 7f) - config.number("minSize", 2f))
                if (config.text("shape", "circle") == "square") {
                    drawRect(shapeColor, Offset(x, y), Size(shapeSize, shapeSize))
                } else {
                    drawCircle(shapeColor, shapeSize, Offset(x, y))
                }
            }
        }
        TextPVEffectKind.ScatteredText -> drawScatteredText(frame, measurer, config)
        TextPVEffectKind.ScreenBorder -> {
            val margin = config.number("margin", 18f)
            val borderColor = color("color", "#1a1a1a").copy(alpha = alpha("alpha", .5f))
            drawRect(
                borderColor,
                Offset(margin, margin),
                Size(size.width - margin * 2f, size.height - margin * 2f),
                style = Stroke(config.number("lineWidth", 1.5f)),
            )
            val starSize = config.number("starSize", 4f)
            listOf(
                Offset(size.width * .25f, margin), Offset(size.width * .75f, margin),
                Offset(size.width * .25f, size.height - margin), Offset(size.width * .75f, size.height - margin),
                Offset(margin, size.height * .35f), Offset(size.width - margin, size.height * .65f),
            ).forEach { drawCircle(borderColor, starSize, it) }
        }
        TextPVEffectKind.ShadowShapes -> drawShadowShapes(frame, config)
        TextPVEffectKind.StaggeredText -> drawStaggeredText(frame, measurer, config)
        TextPVEffectKind.TextureBackground -> {
            repeat(260) { index ->
                val x = size.width * pvUnit(seed, index)
                val y = size.height * pvUnit(seed, index + 400)
                drawCircle(
                    color("color", "#5a3a5a").copy(alpha = alpha("alpha", .12f) * (0.4f + pvUnit(seed, index + 700))),
                    1.2f + pvUnit(seed, index + 900) * 1.8f,
                    Offset(x, y),
                )
            }
        }
        TextPVEffectKind.TriangleGrid -> {
            val cols = config.integer("cols", 9)
            val step = size.width / cols
            val gridColor = color("color", "#323200").copy(alpha = alpha("alpha", .3f))
            repeat(cols) { column ->
                repeat((size.height / step).toInt() + 1) { row ->
                    val x = column * step
                    val y = row * step
                    drawLine(gridColor, Offset(x, y), Offset(x + step, y + step), 1f)
                    drawLine(gridColor, Offset(x + step, y), Offset(x, y + step), 1f)
                }
            }
        }
        TextPVEffectKind.VerticalSubText -> drawVerticalSubText(frame, measurer, config)
        TextPVEffectKind.VictimOutline -> {
            val center = Offset(size.width * .5f, size.height * .58f)
            val bodyWidth = size.minDimension * .18f * config.number("scale", 1.5f)
            val bodyHeight = size.minDimension * .42f * config.number("scale", 1.5f)
            val outline = color("color", "#ffffff").copy(alpha = alpha("alpha", .88f))
            drawCircle(outline, bodyWidth * .34f, Offset(center.x, center.y - bodyHeight * .6f), style = Stroke(config.number("lineWidth", 5f)))
            drawLine(outline, Offset(center.x - bodyWidth * .5f, center.y - bodyHeight * .3f), Offset(center.x - bodyWidth * .8f, center.y + bodyHeight * .2f), config.number("lineWidth", 5f))
            drawLine(outline, Offset(center.x + bodyWidth * .5f, center.y - bodyHeight * .3f), Offset(center.x + bodyWidth * .8f, center.y + bodyHeight * .2f), config.number("lineWidth", 5f))
            drawLine(outline, Offset(center.x - bodyWidth * .2f, center.y + bodyHeight * .1f), Offset(center.x - bodyWidth * .35f, center.y + bodyHeight * .55f), config.number("lineWidth", 5f))
            drawLine(outline, Offset(center.x + bodyWidth * .2f, center.y + bodyHeight * .1f), Offset(center.x + bodyWidth * .35f, center.y + bodyHeight * .55f), config.number("lineWidth", 5f))
        }
        TextPVEffectKind.Vignette -> {
            val radius = config.number("radius", .62f)
            drawRect(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    radius to Color.Transparent,
                    1f to color("color", "#000000").copy(alpha = alpha("alpha", .5f)),
                ),
            )
        }
        TextPVEffectKind.WaveText -> drawWaveText(frame, measurer, config)
        TextPVEffectKind.WebLines -> {
            val count = config.integer("count", 24)
            val center = Offset(
                size.width * config.number("focalX", .5f),
                size.height * config.number("focalY", .45f),
            )
            val webColor = color("color", "#ff2222").copy(alpha = .35f)
            repeat(count) { index ->
                val angle = index * 2f * PI.toFloat() / count + t * .02f * motion
                drawLine(webColor, center, Offset(center.x + cos(angle) * size.maxDimension, center.y + sin(angle) * size.maxDimension), 1.5f)
            }
            repeat(6) { index ->
                drawCircle(webColor.copy(alpha = .22f), size.minDimension * (.1f + index * .09f), center, style = Stroke(1.2f))
            }
        }
    }
}

private fun DrawScope.drawBigOutlineText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val characters = frame.characters
    if (characters.isEmpty()) return
    val fontSize = min(size.height * .35f, size.width / characters.size * .7f)
    val spacing = fontSize * config.number("spacingFrac", 1.3f)
    val totalWidth = (characters.size - 1) * spacing
    val startX = (size.width - totalWidth) / 2f
    val staggerY = config.number("staggerY", fontSize * .25f)
    val staggerDelay = config.number("staggerDelay", .15f)
    val fill = frame.template.palette.resolve(config.text("color", "#e0d8e0"), "#e0d8e0")
    val stroke = frame.template.palette.resolve(config.text("strokeColor", "#ffffff"), "#ffffff")
    characters.forEachIndexed { index, character ->
        val elapsed = max(0f, frame.segmentTime * frame.animationSpeed * 2.5f - index * staggerDelay * frame.animationSpeed)
        if (elapsed <= 0f) return@forEachIndexed
        val progress = min(1f, elapsed / .6f)
        val elastic = elasticOut(progress)
        val y = size.height / 2f + (if (index % 2 == 0) -staggerY else staggerY) + 80f * (1f - elastic)
        drawPvText(
            measurer, character, Offset(startX + index * spacing, y),
            fontSize * (elastic * 1.05f + beatIntensity(frame) * .06f), fill,
            alpha = min(1f, elapsed * 3f), rotation = (1f - elastic) * .4f * if (index % 2 == 0) 1f else -1f,
            strokeColor = stroke, strokeWidth = max(3f, fontSize * .03f),
        )
    }
}

private fun DrawScope.drawShadowShapes(frame: TextPVFrame, config: Map<String, Any>) {
    val shapes = config.objects("shapes")
    val fill = frame.template.palette.resolve(config.text("color", "#ffffff"), "#ffffff")
    val shadow = frame.template.palette.resolve(config.text("shadowColor", "#000055"), "#000055")
    val shadowAlpha = config.number("shadowAlpha", .45f)
    val shadowX = config.number("shadowOffX", 12f)
    val shadowY = config.number("shadowOffY", 14f)
    shapes.forEachIndexed { index, shape ->
        val rawX = shape.number("x", .5f)
        val rawY = shape.number("y", .5f)
        val rawSize = shape.number("size", .12f)
        val center = Offset(
            if (rawX <= 1f) rawX * size.width else rawX,
            if (rawY <= 1f) rawY * size.height else rawY,
        )
        val side = if (rawSize <= 1f) rawSize * size.minDimension else rawSize
        val type = shape.text("type", "square")
        val baseRotation = shape.number("rotation", 0f)
        val breath = 1f + sin(frame.time * (.6f + pvUnit(frame.seed, index) * .8f) + pvUnit(frame.seed, index + 1) * 2f * PI.toFloat()) * .08f * frame.motionIntensity
        val rotation = baseRotation + sin(frame.time * .5f) * .05f * frame.motionIntensity
        val width = if (type == "rect") side * 1.6f else side
        val height = if (type == "rect") side * .4f else side
        if (type == "diamond") {
            rotate(rotation * 180f / PI.toFloat() + 45f, center) {
                drawRect(shadow.copy(alpha = shadowAlpha), Offset(center.x + shadowX - width * breath / 2f, center.y + shadowY - height * breath / 2f), Size(width * breath, height * breath))
                drawRect(fill, Offset(center.x - width * breath / 2f, center.y - height * breath / 2f), Size(width * breath, height * breath))
            }
        } else {
            rotate(rotation * 180f / PI.toFloat(), center) {
                drawRect(shadow.copy(alpha = shadowAlpha), Offset(center.x + shadowX - width * breath / 2f, center.y + shadowY - height * breath / 2f), Size(width * breath, height * breath))
                drawRect(fill, Offset(center.x - width * breath / 2f, center.y - height * breath / 2f), Size(width * breath, height * breath))
            }
        }
    }
}

private fun DrawScope.drawLayeredText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    if (frame.text.isEmpty()) return
    val fontSize = config.number("fontSize", 80f)
    val layers = config.integer("maxLayers", 4).coerceAtLeast(1)
    val fill = frame.template.palette.resolve(config.text("color", "\$primary"), "#1a1a1a")
    val scales = listOf(1f, 1.35f, .75f, 1.15f)
    val offsets = listOf(Offset.Zero, Offset(-20f, 15f), Offset(15f, -10f), Offset(-10f, -20f))
    for (depth in layers - 1 downTo 1) {
        drawPvText(
            measurer,
            frame.previousText.ifEmpty { frame.text },
            Offset(size.width / 2f + offsets[depth % offsets.size].x, size.height / 2f + offsets[depth % offsets.size].y),
            fontSize * scales[depth % scales.size],
            fill.copy(alpha = max(.08f, .55f - depth * .18f)),
            letterSpacing = 8f,
        )
    }
    val progress = min(1f, frame.segmentTime * 3f * frame.animationSpeed)
    drawPvText(measurer, frame.text, Offset(size.width / 2f, size.height / 2f), fontSize * (1f - (1f - progress) * (1f - progress)), fill.copy(alpha = min(1f, frame.segmentTime * 5f * frame.animationSpeed)), letterSpacing = 8f)
}

private fun DrawScope.drawWaveText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val characters = frame.characters
    if (characters.isEmpty()) return
    val spread = size.width * config.number("charSpreadFrac", .5f)
    val spacing = if (characters.size > 1) spread / (characters.size - 1) else 0f
    val startX = size.width / 2f - spread / 2f
    val fontSize = config.number("fontSize", 52f)
    val staggerY = config.number("staggerY", 18f)
    val fill = frame.template.palette.resolve(config.text("color", "#ffffff"), "#ffffff")
    characters.forEachIndexed { index, character ->
        val elapsed = max(0f, frame.segmentTime - index * .15f)
        if (elapsed <= 0f) return@forEachIndexed
        val raw = min(1f, elapsed * 3f)
        val elastic = if (raw < 1f) raw * (1f + .4f * sin(raw * PI.toFloat() * 3f) * (1f - raw)) else 1f
        val yProgress = min(1f, elapsed * 4f)
        val targetY = size.height / 2f + if (index % 2 == 0) -staggerY else staggerY
        drawPvText(
            measurer, character, Offset(startX + index * spacing, targetY + 60f * (1f - (1f - (1f - yProgress) * (1f - yProgress)))),
            fontSize * elastic * 1.1f, fill,
            alpha = min(1f, elapsed * 5f),
            rotation = (1f - min(1f, elapsed * 2.5f)) * .6f * if (index % 2 == 0) 1f else -1f,
        )
    }
}

private fun DrawScope.drawHeroText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    if (frame.text.isEmpty()) return
    val position = Offset(
        if (config.number("x", .5f) <= 1f) size.width * config.number("x", .5f) else config.number("x", .5f),
        if (config.number("y", .5f) <= 1f) size.height * config.number("y", .5f) else config.number("y", .5f),
    )
    val animation = config.text("animation", "")
    val amount = config.number("animationAmount", .03f)
    val speed = config.number("animationSpeed", .5f)
    val scale = if (animation == "breathe") 1f + sin(frame.time * speed * 2f * PI.toFloat()) * amount * frame.motionIntensity else 1f
    val color = frame.template.palette.resolve(config.text("color", "\$text"), "#ffffff")
    val strokeColor = if (config.containsKey("strokeColor")) {
        frame.template.palette.resolve(config.text("strokeColor", "#000000"), "#000000")
    } else null
    drawPvText(
        measurer, frame.text, position,
        config.number("fontSize", 120f) * frame.fontScale * scale, color,
        alpha = min(1f, frame.segmentTime * max(frame.animationSpeed, .5f) * 4f),
        rotation = config.number("rotation", 0f),
        letterSpacing = config.number("letterSpacing", 8f),
        strokeColor = strokeColor, strokeWidth = config.number("strokeWidth", 0f),
        maxWidth = size.width * .92f,
    )
}

private fun DrawScope.drawCuteOutlineText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    if (frame.text.isEmpty()) return
    drawPvText(
        measurer, frame.text,
        Offset(size.width * config.number("x", .5f), size.height * config.number("y", .5f)),
        config.number("fontSize", 80f) * frame.fontScale,
        frame.template.palette.resolve(config.text("fillColor", "#fab2b5"), "#fab2b5"),
        alpha = min(1f, frame.segmentTime * 4f),
        letterSpacing = config.number("letterSpacing", 4f),
        strokeColor = frame.template.palette.resolve(config.text("strokeColor", "#ffffff"), "#ffffff"),
        strokeWidth = config.number("strokeWidth", 8f),
        maxWidth = size.width * .9f,
    )
}

private fun DrawScope.drawGlowTextCards(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    if (frame.text.isEmpty()) return
    val charsPerRow = config.integer("charsPerRow", 5).coerceAtLeast(1)
    val fontSize = config.number("fontSize", 72f) * frame.fontScale
    val cardPadding = config.number("cardPadding", 14f)
    val staggerX = config.number("staggerX", 6f)
    val staggerY = config.number("staggerY", 4f)
    val cardColor = frame.template.palette.resolve(config.text("cardColor", "#ffffff"), "#ffffff")
    val textColor = frame.template.palette.resolve(config.text("textColor", "\$text"), "#111111")
    val rows = frame.characters.chunked(charsPerRow)
    val cardWidth = fontSize + cardPadding * 2f
    val cardHeight = fontSize + cardPadding * 1.4f
    val totalHeight = rows.size * cardHeight + (rows.size - 1) * staggerY
    val center = Offset(
        size.width * config.number("x", .5f),
        size.height * config.number("y", .5f),
    )
    rows.forEachIndexed { rowIndex, row ->
        val rowWidth = row.size * cardWidth + (row.size - 1) * staggerX
        row.forEachIndexed { columnIndex, character ->
            val elapsed = max(0f, frame.segmentTime - (rowIndex * row.size + columnIndex) * .06f * frame.animationSpeed)
            if (elapsed <= 0f) return@forEachIndexed
            val x = center.x - rowWidth / 2f + columnIndex * (cardWidth + staggerX)
            val y = center.y - totalHeight / 2f + rowIndex * (cardHeight + staggerY)
            drawRoundRect(
                cardColor.copy(alpha = min(1f, elapsed * 4f)),
                Offset(x, y), Size(cardWidth, cardHeight), CornerRadius(8f),
            )
            drawPvText(
                measurer, character, Offset(x + cardWidth / 2f, y + cardHeight / 2f),
                fontSize, textColor.copy(alpha = min(1f, elapsed * 5f)),
            )
        }
    }
}

private fun DrawScope.drawStaggeredText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val characters = frame.characters
    if (characters.isEmpty()) return
    val fontSize = config.number("fontSize", 68f) * frame.fontScale
    val color = frame.template.palette.resolve(config.text("color", "#ffffff"), "#ffffff")
    val frameColor = frame.template.palette.resolve(config.text("frameColor", "#ffffff"), "#ffffff")
    val framePadding = config.number("framePadding", 30f)
    val spacing = fontSize * 1.05f
    val totalWidth = (characters.size - 1) * spacing
    val startX = (size.width - totalWidth) / 2f
    drawRect(
        frameColor.copy(alpha = .12f),
        Offset(startX - framePadding, size.height / 2f - fontSize * .9f),
        Size(totalWidth + framePadding * 2f, fontSize * 1.8f),
        style = Stroke(1.5f),
    )
    characters.forEachIndexed { index, character ->
        val elapsed = max(0f, frame.segmentTime - index * .08f)
        if (elapsed <= 0f) return@forEachIndexed
        val y = size.height / 2f + sin(index * 1.4f + frame.time * 2f) * fontSize * .12f * frame.motionIntensity
        drawPvText(
            measurer, character, Offset(startX + index * spacing, y),
            fontSize, color.copy(alpha = min(1f, elapsed * 4f)),
            rotation = sin(index * .8f) * .1f,
        )
    }
}

private fun DrawScope.drawCrayonShatter(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val characters = frame.characters
    if (characters.isEmpty()) return
    val fontSize = config.number("fontSize", 100f) * frame.fontScale
    val colors = config.strings("colors").ifEmpty { listOf("#d97a7a", "#e0b96a", "#83b07c", "#7faecc") }
        .map { parseColor(it) }
    val baseColor = frame.template.palette.resolve(config.text("baseColor", "#5a3a5a"), "#5a3a5a")
    val columns = min(5, characters.size)
    val rows = (characters.size + columns - 1) / columns
    val cellWidth = size.width * .72f / columns
    val cellHeight = size.height * .62f / rows
    val startX = size.width * .14f
    val startY = size.height * .18f
    characters.forEachIndexed { index, character ->
        val column = index % columns
        val row = index / columns
        val seedIndex = index + (frame.time / .18f).toInt() * 7
        val useAccent = pvUnit(frame.seed, seedIndex) < .55f
        val characterColor = if (useAccent) colors[index % colors.size] else baseColor
        val offsetX = pvSigned(frame.seed, seedIndex + 1) * 5f
        val offsetY = pvSigned(frame.seed, seedIndex + 2) * 5f
        val rotation = if (pvUnit(frame.seed, seedIndex + 3) < .55f) pvSigned(frame.seed, seedIndex + 4) * 14f else 0f
        drawPvText(
            measurer, character,
            Offset(startX + column * cellWidth + cellWidth / 2f + offsetX, startY + row * cellHeight + cellHeight / 2f + offsetY),
            fontSize * .72f, characterColor,
            rotation = rotation,
            strokeColor = Color.White.copy(alpha = .32f), strokeWidth = 1.2f,
        )
    }
}

private fun DrawScope.drawCrimeTape(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val count = config.integer("count", 6)
    val tapeColor = frame.template.palette.resolve(config.text("tapeColor", "#f5c800"), "#f5c800")
    val textColor = frame.template.palette.resolve(config.text("textColor", "#000000"), "#000000")
    repeat(count) { index ->
        val y = size.height * (.16f + index * .15f)
        val offset = sin(index.toFloat() + frame.time * .2f) * size.width * .06f * frame.motionIntensity
        rotate(-13f, Offset(size.width / 2f, y)) {
            drawRect(tapeColor.copy(alpha = .86f), Offset(-size.width * .15f + offset, y - 20f), Size(size.width * 1.3f, 40f))
            drawPvText(
                measurer, config.text("text", "CRIME SCENE // DO NOT CROSS"),
                Offset(size.width / 2f, y), 13f, textColor, letterSpacing = 4f,
                maxWidth = size.width * 1.2f,
            )
        }
    }
}

private fun DrawScope.drawFormulaText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val count = config.integer("count", 14)
    val formulaColor = frame.template.palette.resolve(config.text("color", "#141414"), "#141414")
    val formula = frame.text.ifEmpty { "E = mc²" }
    repeat(count) { index ->
        val x = size.width * pvUnit(frame.seed, index)
        val y = size.height * pvUnit(frame.seed, index + 60)
        val snippet = (formula + " 0123456789 +-*/=()").drop((pvUnit(frame.seed, index + 120) * 7f).toInt()).take(10)
        drawPvText(
            measurer, snippet, Offset(x, y),
            9f + pvUnit(frame.seed, index + 180) * 9f,
            formulaColor.copy(alpha = config.number("alpha", .35f)),
        )
    }
}

private fun DrawScope.drawHudStatusText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val fontSize = config.number("fontSize", 13f)
    val textColor = frame.template.palette.resolve(config.text("textColor", "#ffffff"), "#ffffff")
    drawPvText(measurer, "REC ● 00:${(frame.time * 7).toInt() % 60}".padStart(12, '0'), Offset(size.width * .18f, size.height * .075f), fontSize, textColor.copy(alpha = .8f))
    drawPvText(measurer, config.text("centerText", "SYSTEM ONLINE"), Offset(size.width * .5f, size.height * .075f), fontSize, textColor.copy(alpha = .8f))
    drawPvText(measurer, config.text("rightText", "NETWATCH"), Offset(size.width * .82f, size.height * .075f), fontSize, frame.template.palette.resolve(config.text("alertColor", "#FF0000"), "#FF0000").copy(alpha = .8f))
}

private fun DrawScope.drawVerticalSubText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val text = frame.text.ifEmpty { "MELOX" }
    val fontSize = config.number("fontSize", 13f)
    val color = frame.template.palette.resolve(config.text("color", "#ffffff"), "#ffffff")
    val characters = text.codePoints().toArray().map { String(Character.toChars(it)) }
    val x = size.width * config.number("x", .82f)
    val y = size.height * config.number("y", .24f)
    characters.forEachIndexed { index, character ->
        drawPvText(
            measurer, character, Offset(x, y + index * fontSize * 1.15f),
            fontSize, color.copy(alpha = .6f),
        )
    }
}

private fun DrawScope.drawScatteredText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val count = config.integer("count", 6)
    val words = config.text("chars", "SCENE MEMORY LIGHT").split(" ").filter(String::isNotEmpty)
    val color = frame.template.palette.resolve(config.text("color", "#4455aa"), "#4455aa")
    repeat(count) { index ->
        val word = words[index % words.size.coerceAtLeast(1)]
        val x = size.width * pvUnit(frame.seed, index)
        val y = size.height * pvUnit(frame.seed, index + 30)
        drawPvText(
            measurer, word, Offset(x, y),
            config.number("minSize", 12f) + pvUnit(frame.seed, index + 60) * (config.number("maxSize", 20f) - config.number("minSize", 12f)),
            color.copy(alpha = .5f), letterSpacing = 3f,
        )
    }
}

private fun DrawScope.drawNoiseText(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val count = config.integer("count", 10)
    val color = frame.template.palette.resolve(config.text("color", "#ffffff"), "#ffffff")
    val alphabet = "01ABCDEF#$%&*"
    repeat(count) { index ->
        val text = buildString {
            repeat(8) { charIndex -> append(alphabet[(pvUnit(frame.seed, index * 8 + charIndex) * alphabet.length).toInt().coerceIn(0, alphabet.lastIndex)]) }
        }
        drawPvText(
            measurer, text,
            Offset(size.width * pvUnit(frame.seed, index + 50), size.height * pvUnit(frame.seed, index + 90)),
            9f + pvUnit(frame.seed, index + 130) * 8f,
            color.copy(alpha = .35f),
        )
    }
}

private fun DrawScope.drawPixelTypewriter(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val characters = frame.characters
    if (characters.isEmpty()) return
    val fontSize = config.number("fontSize", 44f) * frame.fontScale
    val fill = frame.template.palette.resolve(config.text("fillColor", "#5a3a5a"), "#5a3a5a")
    val stroke = frame.template.palette.resolve(config.text("strokeColor", "#ffffff"), "#ffffff")
    val split = (characters.size + 1) / 2
    val left = characters.subList(0, split)
    val right = characters.subList(split, characters.size)
    val y = size.height * config.number("y", .5f)
    fun drawSide(chars: List<String>, x: Float) {
        chars.forEachIndexed { index, character ->
            drawPvText(
                measurer, character,
                Offset(x + index * fontSize * 1.08f, y),
                fontSize, fill, strokeColor = stroke, strokeWidth = config.number("strokeWidth", 6f),
            )
        }
        val cursorAlpha = if ((frame.time * 2f).toInt() % 2 == 0) .9f else .15f
        drawRect(
            frame.template.palette.resolve(config.text("cursorColor", "#ffb3d9"), "#ffb3d9").copy(alpha = cursorAlpha),
            Offset(x + chars.size * fontSize * 1.08f, y - fontSize * .5f),
            Size(config.number("cursorWidth", 4f), fontSize),
        )
    }
    drawSide(left, size.width * config.number("leftX", .2f))
    drawSide(right, size.width * config.number("rightX", .6f))
}

private fun DrawScope.drawPixelWindow(frame: TextPVFrame, measurer: TextMeasurer, config: Map<String, Any>) {
    val width = config.number("width", 300f)
    val height = config.number("height", 200f)
    val centerX = if (config.number("x", .5f) <= 1f) size.width * config.number("x", .5f) else config.number("x", .5f)
    val centerY = if (config.number("y", .5f) <= 1f) size.height * config.number("y", .5f) else config.number("y", .5f)
    val x = centerX - width / 2f
    val y = centerY - height / 2f
    val borderWidth = config.number("borderWidth", 4f)
    drawRect(
        frame.template.palette.resolve(config.text("bgColor", "#ffffff"), "#ffffff").copy(alpha = .92f),
        Offset(x, y), Size(width, height),
    )
    drawRect(
        frame.template.palette.resolve(config.text("borderColor", "#ffb3d9"), "#ffb3d9"),
        Offset(x, y), Size(width, height), style = Stroke(borderWidth),
    )
    val titleHeight = config.number("titleBarHeight", 28f)
    drawRect(
        frame.template.palette.resolve(config.text("titleBgColor", "#ffb3d9"), "#ffb3d9"),
        Offset(x, y), Size(width, titleHeight),
    )
    drawPvText(
        measurer, config.text("title", "WINDOW"),
        Offset(x + width / 2f, y + titleHeight / 2f),
        12f, frame.template.palette.resolve(config.text("titleColor", "#ffffff"), "#ffffff"),
        letterSpacing = 2f,
    )
    repeat(3) { index ->
        drawLine(
            Color(0xFF5A3A5A).copy(alpha = .18f),
            Offset(x + 14f, y + titleHeight + 22f + index * 22f),
            Offset(x + width - 14f, y + titleHeight + 22f + index * 22f),
            2f,
        )
    }
}

private fun DrawScope.drawPvText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    fontSize: Float,
    color: Color,
    alpha: Float = 1f,
    weight: FontWeight = FontWeight.Black,
    family: FontFamily = FontFamily.SansSerif,
    letterSpacing: Float = 0f,
    rotation: Float = 0f,
    strokeColor: Color? = null,
    strokeWidth: Float = 0f,
    maxWidth: Float = Float.MAX_VALUE,
) {
    if (text.isEmpty() || fontSize <= 0f) return
    val style = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = fontSize.toSp(),
        letterSpacing = letterSpacing.toSp(),
    )
    val widthConstraint = maxWidth.coerceAtLeast(1f).toInt().coerceAtMost(100_000)
    val layout = measurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Clip,
        maxLines = 1,
        constraints = Constraints(maxWidth = widthConstraint),
    )
    val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    rotate(rotation, center) {
        if (strokeColor != null && strokeWidth > 0f) {
            drawText(layout, color = strokeColor, topLeft = topLeft, alpha = alpha, drawStyle = Stroke(strokeWidth))
        }
        drawText(layout, color = color, topLeft = topLeft, alpha = alpha)
    }
}

private fun beatIntensity(frame: TextPVFrame): Float {
    val interval = 60f / max(frame.template.bpm, 30f)
    val phase = frame.time % interval / interval
    return kotlin.math.exp(-phase * 6f) * .5f
}

private fun elasticOut(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return progress
    return (2.0.pow((-10 * progress).toDouble()) * sin((progress * 10 - .75) * (2 * PI / 3)) + 1).toFloat()
}

private fun pvUnit(seed: Long, index: Int): Float {
    var z = seed + index.toLong() * -7046029254386353131L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    z = z xor (z ushr 31)
    return ((z ushr 40) and 0xFFFFFF).toFloat() / 16_777_215f
}

private fun pvSigned(seed: Long, index: Int): Float = pvUnit(seed, index) * 2f - 1f
