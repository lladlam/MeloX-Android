package com.lladlam.melox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.ui.MeloXBottomContentClearance
import com.lladlam.melox.ui.glass.meloXLiquidButton

@Composable
fun SettingsScreen(
    session: NeteaseSessionStore,
    onLogin: () -> Unit,
) {
    LaunchedEffect(session.cookie) {
        if (session.isLoggedIn) session.refreshProfile()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 52.dp, bottom = MeloXBottomContentClearance),
    ) {
        Text(
            text = "设置",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "网易云音乐账号",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .meloXLiquidButton(
                    shape = RoundedCornerShape(28.dp),
                    enabled = !session.isLoggedIn,
                    surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                    lensRadius = 10.dp,
                    refractionHeight = 18.dp,
                )
                .clickable(enabled = !session.isLoggedIn, onClick = onLogin),
            shape = RoundedCornerShape(28.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            when {
                session.isLoggedIn && session.profile != null -> {
                    val profile = session.profile!!
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.nickname,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "用户 ID ${profile.userId} · 账号信息与同步",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                                maxLines = 2,
                            )
                        }
                        Text(
                            text = "›",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        )
                    }
                }

                session.isLoggedIn && session.isRefreshing -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(38.dp))
                        Column {
                            Text("网易云音乐账号", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "正在读取账号信息",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("＋", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "登录网易云音乐",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "同步收藏、云盘与播放记录",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                            )
                        }
                        Text(
                            text = "›",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        )
                    }
                }
            }
        }

        if (!session.isLoggedIn) {
            Text(
                text = "登录 Cookie 仅保存在本机，用于同步收藏、云盘和账号内容。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
            )
        }

        session.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (session.isLoggedIn) {
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(26.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                        lensRadius = 9.dp,
                        refractionHeight = 16.dp,
                    )
                    .clickable { session.clear() },
                shape = RoundedCornerShape(26.dp),
                color = androidx.compose.ui.graphics.Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = "退出登录",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
