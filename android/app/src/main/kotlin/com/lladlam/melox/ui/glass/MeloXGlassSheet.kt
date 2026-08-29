package com.lladlam.melox.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lladlam.melox.ui.theme.isMeloXDarkTheme
import com.lladlam.melox.ui.animation.meloXPanelEnter

/**
 * Floating iOS 26-style Action Sheet / Context Menu surface.
 *
 * The scrim, bottom spacing, grabber and material treatment intentionally live
 * here so every action surface has the same native-component silhouette.
 */
@Composable
fun MeloXGlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isMeloXDarkTheme()
    val hasBackdrop = LocalMeloXBackdrop.current != null
    // Regular pages deliberately do not expose a recursive RenderNode
    // backdrop. In that safe mode the sheet still needs an opaque fallback;
    // otherwise the page underneath remains readable through the modal and
    // makes rows appear to overlap. When a backdrop is available, preserve
    // the translucent sampled-glass treatment.
    val sheetTint = when {
        hasBackdrop && dark -> Color.White.copy(alpha = 0.08f)
        hasBackdrop -> Color.White.copy(alpha = 0.34f)
        dark -> Color.Black
        else -> Color.White
    }
    val sheetSurface = when {
        hasBackdrop && dark -> Color.Black.copy(alpha = 0.12f)
        hasBackdrop -> Color.White.copy(alpha = 0.20f)
        dark -> Color.Black
        else -> Color.White
    }
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            AnimatedVisibility(
                visible = true,
                enter = meloXPanelEnter(initialScale = 0.97f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        )
                        .padding(horizontal = 18.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .meloXGlassSurface(
                                shape = MeloXShapes.sheet,
                                material = MeloXGlassMaterial.Regular,
                                tint = sheetTint,
                                surfaceColor = sheetSurface,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        SheetGrabber()
                        content()
                    }
                }
            }
        }
    }
}

/** Centered iOS 26-style overlay for compact pickers and confirmation panels. */
@Composable
fun MeloXGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isMeloXDarkTheme()
    val hasBackdrop = LocalMeloXBackdrop.current != null
    val dialogTint = when {
        hasBackdrop && dark -> Color.White.copy(alpha = 0.08f)
        hasBackdrop -> Color.White.copy(alpha = 0.34f)
        dark -> Color.Black
        else -> Color.White
    }
    // Confirmation/prompt content must remain fully opaque. Only the empty
    // area around the dialog uses a translucent modal scrim.
    val dialogSurface = if (dark) Color.Black else Color.White
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            AnimatedVisibility(
                visible = true,
                enter = meloXPanelEnter(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = modifier
                            .padding(horizontal = 28.dp)
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .meloXGlassSurface(
                                shape = RoundedCornerShape(28.dp),
                                material = MeloXGlassMaterial.Regular,
                                tint = dialogTint,
                                surfaceColor = dialogSurface,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetGrabber() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = Color.White.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
    }
}
