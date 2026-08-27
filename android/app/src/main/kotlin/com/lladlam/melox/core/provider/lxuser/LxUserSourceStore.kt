package com.lladlam.melox.core.provider.lxuser

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Stores LX user-source metadata separately from the executable script body. */
object LxUserSourceStore {
    private const val PreferencesName = "melox_lx_user_sources"
    private const val KeyList = "list"
    private const val MaxSources = 20
    private const val MaxScriptBytes = 9_000_000L

    fun list(context: Context): List<LxUserSourceRecord> {
        val raw = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(KeyList, null) ?: return emptyList()
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    LxUserSourceRecord(
                        id = id,
                        metadata = LxUserScriptMetadata(
                            name = item.optString("name").takeIf(String::isNotBlank),
                            version = item.optString("version").takeIf(String::isNotBlank),
                            author = item.optString("author").takeIf(String::isNotBlank),
                            description = item.optString("description").takeIf(String::isNotBlank),
                            homepage = item.optString("homepage").takeIf(String::isNotBlank),
                        ),
                    ),
                )
            }
        }
    }

    fun import(context: Context, script: String): LxUserSourceRecord {
        require(script.toByteArray(Charsets.UTF_8).size <= MaxScriptBytes) { "音乐源脚本过大（最大 9 MB）" }
        require(script.trimStart().startsWith("/*")) { "不是有效的 LX Music 音乐源脚本" }
        val metadata = LxUserScriptMetadata.parse(script)
        val id = "user_api_${System.currentTimeMillis()}_${(100..999).random()}"
        val record = LxUserSourceRecord(id, metadata)
        val app = context.applicationContext
        val dir = File(app.filesDir, "lx-user-sources").apply { mkdirs() }
        File(dir, "$id.js").writeText(script, Charsets.UTF_8)
        val records = (list(app) + record).takeLast(MaxSources)
        saveList(app, records)
        return record
    }

    fun script(context: Context, id: String): String? =
        File(File(context.applicationContext.filesDir, "lx-user-sources"), "$id.js")
            .takeIf(File::isFile)?.readText(Charsets.UTF_8)

    fun remove(context: Context, id: String) {
        val app = context.applicationContext
        File(File(app.filesDir, "lx-user-sources"), "$id.js").delete()
        saveList(app, list(app).filterNot { it.id == id })
    }

    private fun saveList(context: Context, records: List<LxUserSourceRecord>) {
        val json = JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().apply {
                    put("id", record.id)
                    put("name", record.metadata.name.orEmpty())
                    put("version", record.metadata.version.orEmpty())
                    put("author", record.metadata.author.orEmpty())
                    put("description", record.metadata.description.orEmpty())
                    put("homepage", record.metadata.homepage.orEmpty())
                })
            }
        }
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putString(KeyList, json.toString()).apply()
    }
}

data class LxUserSourceRecord(
    val id: String,
    val metadata: LxUserScriptMetadata,
)
