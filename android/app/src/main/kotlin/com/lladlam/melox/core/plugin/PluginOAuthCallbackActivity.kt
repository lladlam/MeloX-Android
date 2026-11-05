package com.lladlam.melox.core.plugin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only the state-bound code is accepted; intent extras and callback error descriptions are ignored. */
class PluginOAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent?.dataString
        if (callback == null) { finish(); return }
        lifecycleScope.launch {
            val success = try { withContext(Dispatchers.IO) { PluginOAuth.complete(applicationContext, callback) }; true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            Toast.makeText(this@PluginOAuthCallbackActivity, if (success) "插件登录成功" else "登录未完成，请返回插件设置重试", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
