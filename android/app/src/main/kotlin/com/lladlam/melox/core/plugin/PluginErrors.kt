package com.lladlam.melox.core.plugin

/** Only host-controlled codes and text are suitable for persistent diagnostics and UI. */
object PluginErrors {
    private val messages = mapOf(
        "OK" to "调用完成",
        "CANCELLED" to "操作已取消",
        "UNSUPPORTED_OPERATION" to "插件未提供此能力",
        "INVALID_ARGUMENT" to "插件请求或返回数据不符合协议",
        "LOGIN_REQUIRED" to "请先选择或登录插件账号",
        "AUTH_EXPIRED" to "账号授权已失效，请重新授权",
        "PERMISSION_DENIED" to "插件没有访问此资源的权限",
        "RATE_LIMITED" to "请求过于频繁，请稍后重试",
        "NETWORK_REQUEST_FAILED" to "网络请求失败，请检查网络后重试",
        "NOT_FOUND" to "资源不存在或已移除",
        "PLUGIN_ERROR" to "插件执行失败",
        "PLUGIN_EXECUTION_FAILED" to "插件执行中断或返回异常，请重试",
        "UNSUPPORTED_MEDIA_FORMAT" to "此插件音频格式暂不支持",
        "PUBLISHER_RECORD_MISMATCH" to "插件包与记录的发布者不一致，请重新导入可信包",
        "PUBLISHER_CHANGE_APPROVAL_REQUIRED" to "发布者已变化，请核对指纹并确认候选包",
        "PLUGIN_PERMISSION_APPROVAL_REQUIRED" to "新版插件请求更多权限，请核对后确认",
        "DOWNLOAD_INTERRUPTED" to "上次下载因应用重启中断，点击重试可继续任务",
        "DOWNLOAD_FAILED" to "下载失败，可重试或移除任务",
        "DOWNLOAD_STAGING_FULL" to "未完成下载的临时文件已达限额，请先完成或移除其他任务",
        "SOURCE_UNAVAILABLE" to "来源缺失或已禁用，恢复原来源后可重试",
        "DOWNLOAD_ACCOUNT_CHANGED" to "任务属于另一个插件账号，请切回原账号后重试",
        "PERSISTENCE_FAILED" to "无法保存任务或状态，请检查可用存储空间",
    )
    internal fun known(code: String) = code in messages
    fun message(code: String): String = messages[code] ?: messages.getValue("PLUGIN_EXECUTION_FAILED")
    fun code(failure: Throwable): String {
        val candidate = failure.message
        if (candidate != null && candidate in messages && candidate != "OK") return candidate
        return when (failure) {
            is java.util.concurrent.CancellationException, is InterruptedException -> "CANCELLED"
            is java.io.IOException -> "NETWORK_REQUEST_FAILED"
            is IllegalArgumentException -> "INVALID_ARGUMENT"
            is SecurityException -> "PERMISSION_DENIED"
            else -> "PLUGIN_EXECUTION_FAILED"
        }
    }
    fun describe(failure: Throwable) = message(code(failure))
}
