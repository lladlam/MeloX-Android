package com.lladlam.melox.ui.provider

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.prepareMeloXPagePredictiveBack
import com.lladlam.melox.ui.account.NeteaseLoginScreen
import com.lladlam.melox.ui.theme.MeloXTheme

class MeloXProviderServicesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareMeloXPagePredictiveBack()
        setContent {
            MeloXTheme {
                ProviderServicesPage()
            }
        }
    }

    @Composable
    private fun ProviderServicesPage() {
        val context = this@MeloXProviderServicesActivity
        val session = rememberNeteaseSessionStore()
        var currentSource by remember { mutableStateOf(MusicProviderSelectionStore.selectedSource(context)) }
        var showNeteaseLogin by remember { mutableStateOf(false) }
        if (showNeteaseLogin) {
            NeteaseLoginScreen(
                session = session,
                onDismiss = { showNeteaseLogin = false },
                onLoggedIn = { showNeteaseLogin = false },
            )
            return
        }
        ProviderServicesScreen(
            currentSource = currentSource,
            onSourceSelected = { source ->
                currentSource = source
                MusicProviderSelectionStore.setSelectedSource(context, source)
            },
            neteaseSession = session,
            onNeteaseLogin = { showNeteaseLogin = true },
            onBack = ::finish,
        )
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, MeloXProviderServicesActivity::class.java)
                    .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}
