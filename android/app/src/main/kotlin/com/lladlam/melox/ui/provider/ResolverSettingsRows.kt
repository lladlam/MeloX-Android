package com.lladlam.melox.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lladlam.melox.R
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.provider.thirdparty.ResolverApiClient
import com.lladlam.melox.core.provider.thirdparty.ResolverKind
import com.lladlam.melox.core.provider.thirdparty.ResolverSettingsStore
import com.lladlam.melox.core.provider.thirdparty.ResolverTestSongId
import com.lladlam.melox.core.provider.thirdparty.validResolverEndpoint
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXIosListRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-configured fallback settings; official account/source controls are unchanged. */
@Composable
internal fun ResolverSettingsRows() {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<ResolverKind?>(null) }
    ResolverKind.entries.forEach { kind ->
        val settings = remember(kind, revision) { ResolverSettingsStore.read(context, kind) }
        MeloXIosListRow(
            title = kind.label,
            subtitle = stringResource(if (settings.configured) R.string.provider_resolver_configured else R.string.provider_resolver_unconfigured),
            onClick = { editing = kind },
        )
    }
    editing?.let { kind ->
        ResolverSettingsDialog(kind = kind, onDismiss = { editing = null }, onSaved = {
            revision++
            editing = null
        })
    }
}

@Composable
private fun ResolverSettingsDialog(kind: ResolverKind, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember(kind) { ResolverSettingsStore.read(context, kind) }
    var endpoint by remember(kind) { mutableStateOf(original.endpoint) }
    var credential by remember(kind) { mutableStateOf(original.credential) }
    var servers by remember(kind) { mutableStateOf(original.servers) }
    var enabled by remember(kind) { mutableStateOf(original.enabled) }
    var songId by remember(kind) { mutableStateOf(ResolverTestSongId.toString()) }
    var status by remember(kind) { mutableStateOf<String?>(null) }
    var responseDetails by remember(kind) { mutableStateOf<String?>(null) }
    var busy by remember(kind) { mutableStateOf(false) }
    fun draft() = original.copy(endpoint = endpoint.trim(), credential = credential.trim(), servers = servers.trim(), enabled = enabled)

    MeloXGlassDialog(visible = true, onDismiss = { if (!busy) onDismiss() }, maxHeightFraction = .92f) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(kind.label, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(when (kind) {
                    ResolverKind.Meting -> R.string.provider_resolver_meting_help
                    ResolverKind.Chksz -> R.string.provider_resolver_chksz_help
                    ResolverKind.Unm -> R.string.provider_resolver_unm_help
                }),
                Modifier.padding(top = 10.dp),
            )
            Text(stringResource(R.string.provider_resolver_order), Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.provider_resolver_enabled))
                MeloXGlassToggle(checked = enabled, onCheckedChange = { if (!busy) enabled = it })
            }
            Text(stringResource(R.string.provider_resolver_endpoint), Modifier.padding(top = 12.dp))
            MeloXGlassTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), enabled = !busy,
                placeholder = { Text(if (kind == ResolverKind.Meting) "https://example.com/api.php" else "https://example.com") })
            if (kind != ResolverKind.Unm) {
                OutlinedTextField(
                    value = credential, onValueChange = { credential = it }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                    label = { Text(if (kind == ResolverKind.Chksz) "API Key" else "Auth") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            } else {
                Text(stringResource(R.string.provider_resolver_servers), Modifier.padding(top = 12.dp))
                MeloXGlassTextField(servers, { servers = it }, Modifier.fillMaxWidth(), enabled = !busy)
            }
            Text(stringResource(R.string.provider_resolver_test_help, ResolverTestSongId.toString()), Modifier.padding(top = 14.dp))
            MeloXGlassTextField(songId, { songId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), enabled = !busy,
                placeholder = { Text(stringResource(R.string.provider_resolver_song_id)) })
            MeloXGlassButton(
                onClick = {
                    val id = songId.toLongOrNull()
                    if (!validResolverEndpoint(endpoint) || id == null || id <= 0 ||
                        (kind == ResolverKind.Chksz && credential.isBlank())) {
                        status = context.getString(R.string.provider_resolver_invalid)
                        responseDetails = null
                    } else {
                        busy = true
                        status = null
                        responseDetails = null
                        val settings = draft().copy(enabled = true)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ResolverApiClient().test(settings, MusicTrack(
                                    MusicResourceId(MusicSource.Netease, id.toString()), "", emptyList(),
                                    providerMetadata = ProviderTrackMetadata.Netease(id),
                                ), AudioQualityTier.High)
                            }
                            status = context.getString(if (result.url == null) R.string.provider_resolver_test_failed else R.string.provider_resolver_test_ok)
                            responseDetails = if (result.url == null) {
                                val body = result.requestError ?: when {
                                    result.responseBody == null -> context.getString(R.string.provider_resolver_response_non_text, result.contentType.orEmpty())
                                    result.responseBody.isBlank() -> context.getString(R.string.provider_resolver_response_empty)
                                    else -> result.responseBody
                                }
                                listOfNotNull(result.httpStatus?.let { "HTTP $it${result.contentType?.let { mime -> " · $mime" }.orEmpty()}" },
                                    body, context.getString(R.string.provider_resolver_response_truncated).takeIf { result.responseTruncated })
                                    .joinToString("\n")
                            } else null
                            busy = false
                        }
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MeloXGlassButtonStyle.Plain,
            ) { Text(stringResource(if (busy) R.string.provider_resolver_testing else R.string.provider_resolver_test)) }
            status?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
            responseDetails?.let { details ->
                Text(stringResource(R.string.provider_resolver_response_title), Modifier.padding(top = 8.dp))
                SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    Text(details, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeloXGlassButton(onClick = { ResolverSettingsStore.clear(context, kind); onSaved() }, enabled = !busy,
                    modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.Plain) { Text(stringResource(R.string.provider_clear)) }
                MeloXGlassButton(onClick = {
                    val settings = draft()
                    if ((settings.endpoint.isNotBlank() && !validResolverEndpoint(settings.endpoint)) ||
                        (enabled && !settings.configured)) {
                        status = context.getString(R.string.provider_resolver_invalid)
                    } else {
                        ResolverSettingsStore.write(context, settings)
                        onSaved()
                    }
                }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.provider_save)) }
            }
        }
    }
}
