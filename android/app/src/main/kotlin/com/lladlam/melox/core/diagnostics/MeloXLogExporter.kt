package com.lladlam.melox.core.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import com.lladlam.melox.core.music.provider.ProviderAccountManager
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MeloXLogExportResult(
    val lineCount: Int,
)

data class MeloXLogDeviceInfo(
    val androidVersion: String,
    val phoneModel: String,
    val systemVersion: String,
    val loggedMusicSources: List<String>,
)

object MeloXLogExporter {
    private const val CommandTimeoutSeconds = 12L

    fun collectDeviceInfo(context: Context): MeloXLogDeviceInfo {
        val loggedSources = ProviderAccountManager(context).allStates()
            .filter { it.loggedIn }
            .map { it.source.displayName }
        return MeloXLogDeviceInfo(
            androidVersion = "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            systemVersion = detectSystemVersion(),
            loggedMusicSources = loggedSources,
        )
    }

    fun exportRecentLogs(
        context: Context,
        uri: Uri,
        deviceInfo: MeloXLogDeviceInfo = collectDeviceInfo(context),
    ): MeloXLogExportResult {
        val logcat = readProcessLogs()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val content = buildString {
            appendLine("MeloX Android 日志")
            appendLine("日志范围：当前 MeloX 进程可读取的全部日志")
            appendLine("导出时间：${System.currentTimeMillis()}")
            appendLine("进程：${android.os.Process.myPid()}")
            appendLine("应用包名：${context.packageName}")
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            }
            appendLine("应用版本：${packageInfo.versionName}（$versionCode）")
            appendLine("构建类型：${if (com.lladlam.melox.BuildConfig.DEBUG) "Debug" else "Release"}")
            appendLine("Android 版本：${deviceInfo.androidVersion}")
            appendLine("手机型号：${deviceInfo.phoneModel}")
            appendLine("系统版本：${deviceInfo.systemVersion}")
            appendLine("已登录音乐源：${deviceInfo.loggedMusicSources.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无"}")
            appendLine("应用日志条数：${logcat.lineCount}")
            appendLine()
            appendLine("以下为当前 MeloX 进程日志，不包含其他应用进程日志：")
            appendLine()
            append(logcat.text.ifBlank { "（当前没有可读取的应用日志）\n" })
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法打开导出文件")

        return MeloXLogExportResult(logcat.lineCount)
    }

    private fun readProcessLogs(): ProcessLogResult {
        val process = runCatching {
            ProcessBuilder(
                "logcat",
                "-d",
                "-v",
                "epoch",
                "--pid=${android.os.Process.myPid()}",
            )
                .redirectErrorStream(true)
                .start()
        }.getOrElse { throw IOException("无法读取应用日志", it) }

        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        if (!process.waitFor(CommandTimeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("读取应用日志超时")
        }
        if (process.exitValue() != 0) {
            throw IOException("读取应用日志失败（退出码 ${process.exitValue()}）")
        }

        val lines = output.lineSequence().filter(String::isNotBlank).toList()
        return ProcessLogResult(
            text = lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n"),
            lineCount = lines.size,
        )
    }

    private fun detectSystemVersion(): String {
        val hyperOsName = systemProperty("ro.mi.os.version.name")
        val hyperOsIncremental = systemProperty("ro.odm.build.version.incremental")
            .ifBlank { systemProperty("ro.mi.os.version.incremental") }
            .ifBlank { systemProperty("ro.build.version.incremental") }
        if (hyperOsName.isNotBlank() || hyperOsIncremental.startsWith("OS")) {
            val version = Regex("OS(\\d+(?:\\.\\d+){1,2})")
                .find(hyperOsIncremental)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf(String::isNotBlank)
                ?: hyperOsName
            return "HyperOS ${version.ifBlank { "未知" }}"
        }

        val miui = systemProperty("ro.miui.ui.version.name")
        if (miui.isNotBlank()) return "MIUI $miui"

        val colorOs = systemProperty("ro.build.version.oplusrom")
        if (colorOs.isNotBlank()) return "ColorOS $colorOs"

        val originOs = systemProperty("ro.vivo.os.version")
        if (originOs.isNotBlank()) return "OriginOS $originOs"

        val harmonyOs = systemProperty("hw_sc.build.platform.version")
        if (harmonyOs.isNotBlank()) return "HarmonyOS $harmonyOs"

        val magicOs = systemProperty("ro.build.version.magic")
        if (magicOs.isNotBlank()) return "MagicOS $magicOs"

        val oneUi = systemProperty("ro.build.version.oneui")
        if (oneUi.isNotBlank()) return "One UI $oneUi"

        return Build.DISPLAY.takeIf(String::isNotBlank) ?: "未知"
    }

    private fun systemProperty(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull().orEmpty().trim()

    private data class ProcessLogResult(
        val text: String,
        val lineCount: Int,
    )
}
