package com.lladlam.melox.ui.account

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.lladlam.melox.BuildConfig
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.provider.spotify.SpotifyOAuth
import com.lladlam.melox.core.provider.spotify.SpotifySessionStore
import com.lladlam.melox.ui.theme.MeloXTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SpotifyCallbackActivity : ComponentActivity() {
    private var callbackJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeloXTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在完成 Spotify 登录…", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        callbackJob?.cancel()
        callbackJob = lifecycleScope.launch {
            val callback = intent?.data
            val result = runCatching {
                requireNotNull(callback) { "无效的 Spotify 登录回调" }
                SpotifyOAuth(
                    applicationContext,
                    BuildConfig.SPOTIFY_CLIENT_ID,
                    MeloXHttpClient.shared,
                ).handleCallback(callback)
            }
            result.exceptionOrNull()?.let {
                SpotifySessionStore.setOAuthError(applicationContext, it.message ?: "Spotify 登录失败")
            }
            finish()
        }
    }
}
