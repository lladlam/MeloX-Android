/*
 * Small Sonify library UI primitives (GPL-3.0), adapted for MeloX.
 *
 * Upstream sources:
 *   ui/pages/library/OnlineStatus.kt
 *   ui/widgets/basic/YosTextField.kt
 *   ui/pages/library/albums/AlbumInfo.kt (NormalButton)
 *   ui/pages/library/LibraryDivider.kt
 *
 * Sonify drawables are replaced with MeloXSymbol font icons; Coil2
 * ShadowImage is replaced with Coil3 AsyncImage while retaining Sonify's
 * 52dp / 3.5dp library-row artwork geometry.
 */
package com.lladlam.melox.ui.sonify.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.sonify.SonifyHeadline
import com.lladlam.melox.ui.sonify.SonifyHeadlineDark
import com.lladlam.melox.ui.sonify.withNight

/**
 * Library search field, preserving Sonify's 44dp rounded field and text style.
 */
@Composable
internal fun SonifySearchTextField(
    text: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontSize = 17.sp
    Row(
        modifier
            .background(
                (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                RoundedCornerShape(10.dp),
            )
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Search,
            modifier = Modifier.padding(start = 12.dp).size(18.dp).alpha(0.55f),
            color = LocalContentColor.current,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = fontSize,
                    modifier = Modifier.alpha(0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.Black withNight Color.White,
                    fontSize = fontSize,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth(),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Sonify "play / shuffle" action button. */
@Composable
internal fun SonifyNormalButton(
    symbol: MeloXSymbol,
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
    containerColor: Color? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val tint = contentColor ?: MaterialTheme.colorScheme.primary
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(
                containerColor ?: (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape,
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(22.dp),
            color = tint,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = tint,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
    }
}

/** Online status row (loading / empty / error) used by list and detail pages. */
@Composable
internal fun SonifyOnlineStatusItem(
    status: String,
    loadingText: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
) {
    if (status != "loading" && status != "empty" && !status.startsWith("error:")) return
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            status == "loading" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor ?: MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = loadingText,
                    fontSize = 13.sp,
                    color = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            status == "empty" -> Text(
                text = emptyText,
                fontSize = 18.sp,
                modifier = Modifier.alpha(0.6f),
            )

            else -> Text(
                text = status.removePrefix("error:"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SonifyListDivider(
    modifier: Modifier = Modifier,
    startPadding: Dp = 88.dp,
    endPadding: Dp = 16.dp,
) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding)
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

@Composable
internal fun SonifyDetailDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 16.dp)
            .height(0.5.dp)
            .background(LocalContentColor.current.copy(alpha = 0.2f)),
    )
}

/** Artwork shell with Sonify's rounded-corner library geometry. */
@Composable
internal fun SonifyArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 3.5.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(LocalContentColor.current.copy(alpha = 0.09f)),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                modifier = Modifier.align(Alignment.Center).size(28.dp).alpha(0.35f),
                color = LocalContentColor.current,
            )
        }
    }
}

/** Empty-state block shared by normal music and detail pages. */
@Composable
internal fun SonifyEmptyStatus(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 22.dp,
    fontSize: Int = 18,
) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 10.dp).alpha(0.6f),
    )
}

@Composable
internal fun SonifyColumnEmpty(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        SonifyEmptyStatus(text, horizontalPadding = 0.dp)
    }
}

@Composable
internal fun SonifyLibraryPageTitleHeader(content: String, horizontalPadding: Dp = 20.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = 6.dp),
    ) {
        Text(
            text = content,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            color = SonifyHeadline withNight SonifyHeadlineDark,
        )
    }
}

@Composable
internal fun SonifyStatusFooter(
    text: String,
    showSpinner: Boolean,
    isError: Boolean,
    retry: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (isError) MaterialTheme.colorScheme.error else LocalContentColor.current.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        if (retry) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "重试",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 6.dp),
            )
        }
    }
}
