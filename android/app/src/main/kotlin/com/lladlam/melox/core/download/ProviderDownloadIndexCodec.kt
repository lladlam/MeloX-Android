package com.lladlam.melox.core.download

import org.json.JSONArray
import org.json.JSONObject

/** Unknown rows and per-record fields survive writes. Only explicit removal drops an identity. */
internal object ProviderDownloadIndexCodec {
    fun identity(value: JSONObject): String? = if (value.opt("source") is String && value.opt("id") is String)
        value.getString("source") + ":" + value.getString("id") else null

    fun merge(previous: JSONArray, updates: JSONArray, removed: Set<String>): JSONArray {
        val replacements = linkedMapOf<String, JSONObject>()
        for (i in 0 until updates.length()) {
            val value = updates.getJSONObject(i)
            replacements[requireNotNull(identity(value))] = value
        }
        val output = JSONArray()
        for (i in 0 until previous.length()) {
            val original = previous.opt(i)
            val key = (original as? JSONObject)?.let(::identity)
            if (key != null && key in removed) continue
            val replacement = key?.let(replacements::remove)
            if (replacement == null) output.put(original)
            else {
                val merged = JSONObject(original.toString())
                replacement.keys().forEach { merged.put(it, replacement.get(it)) }
                output.put(merged)
            }
        }
        replacements.filterKeys { it !in removed }.values.forEach { output.put(it) }
        return output
    }
}
