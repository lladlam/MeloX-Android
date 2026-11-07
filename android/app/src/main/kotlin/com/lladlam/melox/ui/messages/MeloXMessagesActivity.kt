package com.lladlam.melox.ui.messages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.lladlam.melox.ui.MeloXPageHost
import com.lladlam.melox.ui.finishMeloXPage
import com.lladlam.melox.ui.glass.LocalMeloXBackdrop
import com.lladlam.melox.ui.prepareMeloXPageWindow
import com.lladlam.melox.ui.startMeloXPage
import com.lladlam.melox.ui.theme.MeloXTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * **私信（消息）页 —— 一个真实 Activity。**
 *
 * ### 为什么从 App 内覆盖层改成 Activity（2026-10-18）
 * 用户报障：「设置消息页动画不完整，不跟其他设置页采用同款动画」。
 * 此前私信页是 `MeloXApp` 里的一个 `AnimatedVisibility` 覆盖层
 * （`meloXPageEnter` 的 slide + veil），而其他设置页（关于 / 法律 / 通用 / 播放 …）
 * 早已在 2026-10-02 全部迁到 [com.lladlam.melox.ui.settings.MeloXSettingsDetailActivity]
 * 走**平台原生 Activity 转场**。两套推入机制并存的结果：
 * 1. 观感不同款 —— Compose 内 slide 挂在组合/绘制帧上，与 WindowManager 驱动的窗口
 *    转场在速度曲线、打断连续性上都能看出差别；
 * 2. 覆盖层版还有结构性缺陷： veil（`unveilIn`）让页面「先变色再出现」，
 *    且下层页面不退让，没有平台转场那种纵深。
 *
 * 改为 Activity 后，推入 / 退回 / 连续打断 / 预测式返回全部交给平台，
 * 与其他设置详情页逐字同款。
 *
 * ### 约定（与 [com.lladlam.melox.ui.settings.MeloXSettingsDetailActivity] 一致）
 * - 主题走 `Theme.MeloX.Page`（**不透明**窗口）—— 半透明窗口拿不到系统原生转场。
 * - **不要**加 `overridePendingTransition` / `ActivityOptions.makeCustomAnimation`，
 *   也不要在主题里加 `windowAnimationStyle`。
 * - `prepareMeloXPageWindow()` 必须在 `setContent` 之前（按 App 真实配色设窗口底色，
 *   避免转场第一帧闪错色）。
 * - 玻璃背板必须 provide `LocalMeloXBackdrop`（私信页里有 `meloXLiquidButton` /
 *   `MeloXSymbolIcon` 玻璃控件，拿不到 backdrop 会全部退化成无玻璃 else 分支）。
 *
 * @see com.lladlam.melox.ui.MeloXPageHost
 * @see com.lladlam.melox.ui.startMeloXPage
 */
class MeloXMessagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareMeloXPageWindow()
        setContent {
            MeloXPageHost {
                MeloXTheme {
                    // ⚠ 玻璃背板：与 MeloXSettingsDetailActivity / MeloXNeteaseLoginActivity
                    //   同构 —— 稳定源层（纯背景色）+ provider 包裹内容。
                    val pageBackdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .layerBackdrop(pageBackdrop),
                        )
                        CompositionLocalProvider(LocalMeloXBackdrop provides pageBackdrop) {
                            MessagesScreen(onBack = ::finishMeloXPage)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 由设置主页「消息」项、首页工具格「消息」调用。 */
        fun launch(context: Context) {
            context.startMeloXPage(
                Intent(context, MeloXMessagesActivity::class.java)
                    .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}
