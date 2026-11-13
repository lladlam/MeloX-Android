package com.lladlam.melox.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.core.autosync.AutoSyncDictionaryRepository
import com.lladlam.melox.core.autosync.AutoSyncManifest
import com.lladlam.melox.core.autosync.AutoSyncModelRepository
import com.lladlam.melox.core.autosync.AutoSyncModelStore
import com.lladlam.melox.core.network.MeloXGitHubRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Cloud model management for AutoSync, inside「测试功能尝鲜」.
 *
 * Nothing is downloaded until the user confirms a specific language, and every
 * byte goes through the same GitHub mirror routing the app update check uses.
 * A download in flight is cancelled when the page goes away.
 */
@Composable
internal fun AutoSyncModelPanel(context: Context) {
    val scope = rememberCoroutineScope()
    val repository = remember(context) {
        AutoSyncModelRepository(context.applicationContext, MeloXGitHubRouting(context.applicationContext))
    }
    val dictionaryRepository = remember(context) {
        AutoSyncDictionaryRepository(context.applicationContext, MeloXGitHubRouting(context.applicationContext))
    }
    var index by remember { mutableStateOf<AutoSyncModelRepository.Index?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(emptyList<String>()) }
    var busyLanguage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val downloadJobs = remember { mutableStateListOf<Job>() }
    var confirmLanguage by remember { mutableStateOf<String?>(null) }
    var deleteLanguage by remember { mutableStateOf<String?>(null) }
    var precisionLanguage by remember { mutableStateOf<String?>(null) }
    var manifests by remember { mutableStateOf(emptyMap<String, AutoSyncManifest>()) }
    var dictionaryInstalled by remember { mutableStateOf(false) }
    var dictionarySize by remember { mutableStateOf(0L) }
    var dictionaryBusy by remember { mutableStateOf(false) }
    var dictionaryConfirming by remember { mutableStateOf(false) }
    var dictionaryDeleteConfirming by remember { mutableStateOf(false) }
    var dictionaryNotice by remember { mutableStateOf<String?>(null) }

    suspend fun reloadInstalled() {
        val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val languages = AutoSyncModelStore.installedLanguages(context)
            Triple(
                AutoSyncDictionaryRepository.dictionaryDirectory(context) != null,
                AutoSyncDictionaryRepository.installedBytes(context),
                languages to languages.mapNotNull { language ->
                    AutoSyncModelStore.manifest(context, language)?.let { language to it }
                }.toMap(),
            )
        }
        dictionaryInstalled = snapshot.first
        dictionarySize = snapshot.second
        installed = snapshot.third.first
        manifests = snapshot.third.second
    }

    DisposableEffect(Unit) { onDispose { downloadJobs.forEach(Job::cancel) } }

    fun launchDownload(block: suspend CoroutineScope.() -> Unit) {
        downloadJobs.removeAll { it.isCompleted }
        val job = scope.launch { block() }
        downloadJobs += job
    }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            repository.fetchIndex()
                .onSuccess { index = it; loading = false }
                .onFailure { error = it.message ?: it::class.java.simpleName; loading = false }
        }
    }

    LaunchedEffect(repository) {
        reloadInstalled()
        refresh()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.autosync_models_title),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        val packs = index?.packs?.values?.toList().orEmpty()
        when {
            loading -> HintText(stringResource(R.string.autosync_models_loading))
            error != null -> {
                HintText(stringResource(R.string.autosync_model_failed, error.orEmpty()))
                PanelButton(stringResource(R.string.autosync_models_retry)) { refresh() }
            }
            else -> packs.forEach { pack ->
                val manifest = manifests[pack.language]
                ModelRow(
                    pack = pack,
                    manifest = manifest,
                    updateAvailable = manifest != null && !AutoSyncModelRepository.matchesIndex(manifest, pack),
                    busy = busyLanguage == pack.language,
                    confirming = confirmLanguage == pack.language,
                    progress = progress,
                    precisionBusy = busyLanguage == "<prefix>" + pack.language,
                    precisionConfirming = precisionLanguage == pack.language,
                    precisionInstalled = manifest?.modelVariant == "precision",
                    onDownload = { confirmLanguage = pack.language },
                    onCancelConfirm = { confirmLanguage = null },
                    onConfirm = {
                        confirmLanguage = null
                        val snapshot = index ?: return@ModelRow
                        busyLanguage = pack.language
                        progress = 0f
                        launchDownload {
                            try {
                                repository.install(pack, snapshot) { fraction -> progress = fraction }
                                    .onSuccess { reloadInstalled() }
                                    .onFailure { error = it.message }
                            } finally {
                                busyLanguage = null
                            }
                        }
                    },
                    onPrecision = { precisionLanguage = pack.language },
                    onCancelPrecision = { precisionLanguage = null },
                    onPrecisionConfirm = {
                        precisionLanguage = null
                        val snapshot = index ?: return@ModelRow
                        val variant = pack.optionalModel ?: return@ModelRow
                        busyLanguage = "<prefix>" + pack.language
                        progress = 0f
                        launchDownload {
                            try {
                                repository.install(pack, snapshot, precision = variant) { fraction -> progress = fraction }
                                    .onSuccess { reloadInstalled() }
                                    .onFailure { error = it.message }
                            } finally {
                                busyLanguage = null
                            }
                        }
                    },
                    onPrecisionDelete = {
                        precisionLanguage = null
                        val snapshot = index ?: return@ModelRow
                        busyLanguage = "<prefix>" + pack.language
                        progress = 0f
                        launchDownload {
                            try {
                                repository.install(pack, snapshot) { fraction -> progress = fraction }
                                    .onSuccess { reloadInstalled() }
                                    .onFailure { error = it.message }
                            } finally {
                                busyLanguage = null
                            }
                        }
                    },
                    onDelete = {
                        deleteLanguage = null
                        precisionLanguage = null
                        launchDownload { repository.delete(pack.language) }
                    },
                    onDeleteRequest = { deleteLanguage = pack.language },
                    onCancelDelete = { deleteLanguage = null },
                    deleteConfirming = deleteLanguage == pack.language,
                    dictionaryInstalled = dictionaryInstalled,
                    dictionarySize = dictionarySize,
                    dictionaryBusy = dictionaryBusy,
                    dictionaryConfirming = dictionaryConfirming,
                    dictionaryNotice = dictionaryNotice,
                    onDictionaryDownload = { dictionaryConfirming = true },
                    onDictionaryCancel = { dictionaryConfirming = false },
                    onDictionaryConfirm = {
                        dictionaryConfirming = false
                        dictionaryBusy = true
                        progress = 0f
                        error = null
                        launchDownload {
                            try {
                                dictionaryRepository.install { fraction -> progress = fraction }
                                    .onSuccess {
                                        dictionaryNotice = dictionaryRepository.licenseNotice()
                                        reloadInstalled()
                                    }
                                    .onFailure { error = it.message }
                            } finally {
                                dictionaryBusy = false
                            }
                        }
                    },
                    onDictionaryDelete = {
                        dictionaryDeleteConfirming = true
                    },
                    onDictionaryDeleteConfirm = {
                        dictionaryDeleteConfirming = false
                        dictionaryBusy = true
                        launchDownload {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                dictionaryRepository.delete()
                            }
                            dictionaryNotice = null
                            dictionaryBusy = false
                            reloadInstalled()
                        }
                    },
                    onDictionaryDeleteCancel = { dictionaryDeleteConfirming = false },
                    dictionaryDeleteConfirming = dictionaryDeleteConfirming,
                )
            }
        }
    }
}

@Composable
private fun HintText(value: String) {
    Text(
        value,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        fontSize = 13.sp,
    )
}

@Composable
private fun ModelRow(
    pack: AutoSyncModelRepository.Pack,
    manifest: AutoSyncManifest?,
    updateAvailable: Boolean,
    busy: Boolean,
    confirming: Boolean,
    progress: Float,
    precisionBusy: Boolean,
    precisionConfirming: Boolean,
    precisionInstalled: Boolean,
    onDownload: () -> Unit,
    onCancelConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onPrecision: () -> Unit,
    onCancelPrecision: () -> Unit,
    onPrecisionConfirm: () -> Unit,
    onPrecisionDelete: () -> Unit,
    onDelete: () -> Unit,
    onDeleteRequest: () -> Unit,
    onCancelDelete: () -> Unit,
    deleteConfirming: Boolean,
    dictionaryInstalled: Boolean = false,
    dictionarySize: Long = 0L,
    dictionaryBusy: Boolean = false,
    dictionaryConfirming: Boolean = false,
    dictionaryDeleteConfirming: Boolean = false,
    dictionaryNotice: String? = null,
    onDictionaryDownload: () -> Unit = {},
    onDictionaryCancel: () -> Unit = {},
    onDictionaryConfirm: () -> Unit = {},
    onDictionaryDelete: () -> Unit = {},
    onDictionaryDeleteConfirm: () -> Unit = {},
    onDictionaryDeleteCancel: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(languageLabel(pack.language), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                val size = pack.optionalModel.takeIf { precisionInstalled }?.bytes ?: pack.totalBytes
                Text(
                    when {
                        busy -> stringResource(R.string.autosync_model_downloading, (progress * 100).toInt())
                        updateAvailable -> stringResource(R.string.autosync_model_update_available, pack.packVersion)
                        manifest != null -> stringResource(R.string.autosync_model_installed, pack.packVersion)
                        else -> stringResource(R.string.autosync_model_not_installed)
                    } + " · " + formatMegabytes(size) +
                        if (precisionInstalled) " · " + stringResource(R.string.autosync_model_variant_precision) else "",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                    fontSize = 12.sp,
                )
                if (pack.language == "ja") {
                    Text(
                        when {
                            dictionaryInstalled -> stringResource(R.string.autosync_dict_ready)
                            else -> stringResource(R.string.autosync_model_g2p_unavailable)
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        color = if (dictionaryInstalled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = .85f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = .8f)
                        },
                        fontSize = 11.sp,
                    )
                }
            }
            when {
                busy -> Text(
                    stringResource(R.string.autosync_model_downloading, (progress * 100).toInt()),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
                manifest != null && updateAvailable -> PanelButton(stringResource(R.string.autosync_model_update), onClick = onDownload)
                manifest != null -> PanelButton(stringResource(R.string.autosync_model_delete), onClick = onDeleteRequest)
                else -> PanelButton(stringResource(R.string.autosync_model_download), onClick = onDownload)
            }
        }
        if (deleteConfirming && !busy) {
            Text(
                stringResource(R.string.autosync_model_delete_confirm),
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = .8f),
                fontSize = 12.sp,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelButton(stringResource(R.string.action_cancel), onClick = onCancelDelete)
                PanelButton(stringResource(R.string.autosync_model_delete), onClick = onDelete)
            }
        }
        if (confirming && !busy) {
            Text(
                stringResource(R.string.autosync_model_confirm, formatMegabytes(pack.totalBytes)),
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                fontSize = 12.sp,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelButton(stringResource(R.string.action_cancel), onClick = onCancelConfirm)
                PanelButton(stringResource(R.string.autosync_model_download), onClick = onConfirm)
            }
        }
        // 日文 G2P 词典（NAIST）：单独按需下载，不塞进 APK。
        if (pack.language == "ja") {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.autosync_dict_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (dictionaryInstalled) {
                            stringResource(R.string.autosync_dict_installed, formatMegabytes(dictionarySize))
                        } else {
                            stringResource(R.string.autosync_dict_note)
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        fontSize = 11.sp,
                    )
                }
                when {
                    dictionaryBusy -> Text(
                        stringResource(R.string.autosync_model_downloading, (progress * 100).toInt()),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                    dictionaryInstalled -> PanelButton(stringResource(R.string.autosync_model_delete), onClick = onDictionaryDelete)
                    else -> PanelButton(stringResource(R.string.autosync_model_download), onClick = onDictionaryDownload)
                }
            }
            if (dictionaryConfirming && !dictionaryBusy) {
                Text(
                    stringResource(R.string.autosync_dict_confirm),
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    fontSize = 12.sp,
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelButton(stringResource(R.string.action_cancel), onClick = onDictionaryCancel)
                    PanelButton(stringResource(R.string.autosync_model_download), onClick = onDictionaryConfirm)
                }
            }
            if (dictionaryDeleteConfirming && !dictionaryBusy) {
                Text(
                    stringResource(R.string.autosync_dict_delete_confirm),
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = .8f),
                    fontSize = 12.sp,
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelButton(stringResource(R.string.action_cancel), onClick = onDictionaryDeleteCancel)
                    PanelButton(stringResource(R.string.autosync_model_delete), onClick = onDictionaryDeleteConfirm)
                }
            }
            dictionaryNotice?.let { notice ->
                Text(
                    stringResource(R.string.autosync_dict_license, "NAIST"),
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
                    fontSize = 10.sp,
                )
            }
        }
        // 精度优先变体：只在发布方提供了可选模型时出现（目前是英文 292 MB）。
        pack.optionalModel?.takeIf { manifest != null }?.let { variant ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.autosync_model_precision_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.autosync_model_precision_note, formatMegabytes(variant.bytes)),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        fontSize = 11.sp,
                    )
                }
                when {
                    precisionBusy -> Text(
                        stringResource(R.string.autosync_model_downloading, (progress * 100).toInt()),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                    precisionInstalled -> PanelButton(stringResource(R.string.autosync_model_precision_revert), onClick = onPrecisionDelete)
                    else -> PanelButton(stringResource(R.string.autosync_model_download), onClick = onPrecision)
                }
            }
            if (precisionConfirming && !precisionBusy) {
                Text(
                    stringResource(R.string.autosync_model_confirm, formatMegabytes(variant.bytes)),
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    fontSize = 12.sp,
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelButton(stringResource(R.string.action_cancel), onClick = onCancelPrecision)
                    PanelButton(stringResource(R.string.autosync_model_download), onClick = onPrecisionConfirm)
                }
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String = String.format("%.0f MB", bytes / 1024.0 / 1024.0)

private fun languageLabel(language: String): String = when (language) {
    "zh" -> "中文"
    "ja" -> "日本語（G2P 未就绪）"
    "en" -> "English"
    else -> language
}

/** Local action button: the settings-wide helper is private to its own file. */
@Composable
private fun PanelButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        title,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}
