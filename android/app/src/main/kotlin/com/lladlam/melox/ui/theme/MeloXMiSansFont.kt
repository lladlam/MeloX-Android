package com.lladlam.melox.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.lladlam.melox.core.network.MeloXGitHubRouting
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

private const val MiSansFileName = "MiSansVF.ttf"
private const val MiSansRepositoryUrl =
    "https://raw.githubusercontent.com/lladlam/MeloX-Android/main/fonts/MiSans/MiSansVF.ttf"
private const val ExternalMiSansPath = "/storage/emulated/0/misans/MiSans/MiSans/可变字体/MiSansVF.ttf"

@Composable
fun rememberMeloXFontFamily(context: Context): FontFamily {
    var family by remember { mutableStateOf<FontFamily>(FontFamily.SansSerif) }
    LaunchedEffect(context) {
        val file = withContext(Dispatchers.IO) { ensureMiSans(context.applicationContext) }
        if (file != null) {
            runCatching { FontFamily(Typeface.createFromFile(file)) }
                .onSuccess { family = it }
        }
    }
    return family
}

private suspend fun ensureMiSans(context: Context): File? {
    val target = File(context.filesDir, "fonts/$MiSansFileName")
    if (target.isFile && target.length() > 1_000_000L) return target

    val external = File(ExternalMiSansPath)
    if (external.isFile && external.canRead()) {
        target.parentFile?.mkdirs()
        external.copyTo(target, overwrite = true)
        return target
    }

    val routing = MeloXGitHubRouting(context)
    for (source in routing.candidates()) {
        val request = Request.Builder()
            .url(routing.routedUrl(source, MiSansRepositoryUrl))
            .header("User-Agent", "MeloX-Android")
            .build()
        val downloaded = runCatching {
            MeloXHttpClient.shared.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                if (bytes.size < 1_000_000) return@use null
                bytes
            }
        }.getOrNull() ?: continue
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "$MiSansFileName.tmp")
        temporary.writeBytes(downloaded)
        if (temporary.renameTo(target)) return target
        temporary.delete()
    }
    return null
}
