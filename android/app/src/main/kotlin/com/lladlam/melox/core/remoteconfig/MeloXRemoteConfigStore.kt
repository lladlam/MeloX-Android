package com.lladlam.melox.core.remoteconfig

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

internal data class MeloXStoredRemoteConfig(
    val envelope: String?,
    val etag: String?,
    val lastCheckedAtEpochMs: Long,
    val lastUpdatedAtEpochMs: Long,
    val highestConfigVersion: Int,
)

internal class MeloXRemoteConfigStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "remote_config/state.json"))

    fun read(): MeloXStoredRemoteConfig = runCatching {
        if (!file.baseFile.isFile) return@runCatching Empty
        val value = JSONObject(file.readFully().toString(Charsets.UTF_8))
        MeloXStoredRemoteConfig(
            envelope = value.optString("envelope").takeIf(String::isNotBlank),
            etag = value.optString("etag").takeIf(String::isNotBlank),
            lastCheckedAtEpochMs = value.optLong("lastCheckedAtEpochMs"),
            lastUpdatedAtEpochMs = value.optLong("lastUpdatedAtEpochMs"),
            highestConfigVersion = value.optInt("highestConfigVersion"),
        )
    }.getOrDefault(Empty)

    fun write(value: MeloXStoredRemoteConfig) {
        file.baseFile.parentFile?.mkdirs()
        val bytes = JSONObject()
            .put("envelope", value.envelope ?: "")
            .put("etag", value.etag ?: "")
            .put("lastCheckedAtEpochMs", value.lastCheckedAtEpochMs)
            .put("lastUpdatedAtEpochMs", value.lastUpdatedAtEpochMs)
            .put("highestConfigVersion", value.highestConfigVersion)
            .toString()
            .toByteArray()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    fun clearEnvelope() {
        val current = read()
        write(
            current.copy(
                envelope = null,
                etag = null,
                lastCheckedAtEpochMs = 0L,
                lastUpdatedAtEpochMs = 0L,
            ),
        )
    }

    companion object {
        val Empty = MeloXStoredRemoteConfig(null, null, 0L, 0L, 0)
    }
}
