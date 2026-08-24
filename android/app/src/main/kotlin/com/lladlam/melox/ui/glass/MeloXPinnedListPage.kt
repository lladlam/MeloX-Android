package com.lladlam.melox.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

private const val TopFadeShader = """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;
half4 main(float2 coord) {
    float mask = smoothstep(size.y, size.y * 0.40, coord.y);
    return mix(content.eval(coord) * mask, tint * mask, tintIntensity);
}
"""

@Composable
fun MeloXPinnedListPage(
    title: String,
    onNavigateBack: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val collapseDistancePx = with(LocalDensity.current) { 56.dp.toPx() }
    val collapseProgress by remember(listState, collapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val pageBackdrop = rememberLayerBackdrop()
    val background = MaterialTheme.colorScheme.background
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = statusBarHeight + 62.dp

    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = toolbarHeight + 12.dp,
                    end = 20.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "large-title:$title") {
                    Text(
                        title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = 1f - collapseProgress
                                val scale = 1f - .04f * collapseProgress
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, .5f)
                            }
                            .blur(6.dp * collapseProgress)
                            .offset(y = (-8).dp)
                            .padding(vertical = 8.dp),
                        style = MeloXTypography.largeTitle,
                        fontWeight = FontWeight.Bold,
                    )
                }
                content()
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(toolbarHeight + 38.dp)
                .align(Alignment.TopCenter)
                .drawPlainBackdrop(
                    backdrop = pageBackdrop,
                    shape = { RectangleShape },
                    effects = {
                        blur(10.dp.toPx())
                        runtimeShaderEffect("MeloXTopFade", TopFadeShader, "content") {
                            setFloatUniform("size", size.width, size.height)
                            setColorUniform("tint", background)
                            setFloatUniform("tintIntensity", .76f)
                        }
                    },
                ),
        )
        CompositionLocalProvider(LocalMeloXBackdrop provides pageBackdrop) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXGlassIconButton(MeloXSymbol.ChevronLeft, onNavigateBack, contentDescription = "返回")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        title,
                        modifier = Modifier.graphicsLayer {
                            alpha = collapseProgress
                            val scale = .92f + .08f * collapseProgress
                            scaleX = scale
                            scaleY = scale
                        }.blur(8.dp * (1f - collapseProgress)),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(44.dp).padding(horizontal = 22.dp))
            }
        }
    }
}
