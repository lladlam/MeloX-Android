package com.lladlam.melox.platform.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Explicit, shell-only broadcast endpoint. Replies are base64 UTF-8 JSON. */
class MeloXCliReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val reply = try {
                check(MeloXSettingsPreferences.boolean(app, "experimental_cli_enabled", false)) {
                    "请先在设置 → 测试功能尝鲜中开启 CLI 模式"
                }
                val args = JSONObject()
                intent.extras?.keySet()?.forEach { key ->
                    intent.extras?.get(key)?.let { value -> args.put(key, value) }
                }
                withTimeout(8_000) {
                    MeloCliCommands.execute(app, intent.getStringExtra("command") ?: "status", args)
                }.put("ok", true)
            } catch (error: Exception) {
                JSONObject().put("ok", false)
                    .put("error", error.message ?: error.javaClass.simpleName)
            }
            try {
                pending.resultCode = if (reply.optBoolean("ok")) 0 else 1
                pending.resultData = "melox:" + Base64.encodeToString(
                    reply.toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
            } finally {
                pending.finish()
            }
        }
    }
}
