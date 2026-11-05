package com.lladlam.melox.core.plugin

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Same fully-populated records and method coverage as the Node Schema test. */
class PluginWireFieldsTest {
    @Test fun everyPresentFieldRejectsWrongTypesBeforeProviderCoercion() {
        val fixtureFile = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "plugin-sdk/test/fixtures/wire-cases.json") }.first { it.isFile }
        val fixture = JSONObject(fixtureFile.readText())
        val models = fixture.getJSONObject("models")
        val values = mutableMapOf<String, Any>()
        models.keys().forEach { values[it] = models.get(it) }
        fun page(model: String) = JSONObject().put("items", JSONArray().put(models.getJSONObject(model)))
            .put("page", 1).put("pageSize", 10).put("total", 1).put("hasMore", false)
        values["searchPage"] = page("track")
        listOf("playlist", "album", "artist", "ranking").forEach { values[it + "Page"] = page(it) }
        listOf("playlist", "album", "artist").forEach {
            values[it + "Detail"] = JSONObject().put("summary", models.getJSONObject(it)).put("tracks", page("track"))
        }
        values["homeFeed"] = JSONObject().put("recommendedPlaylists", JSONArray().put(models.getJSONObject("playlist")))
            .put("newSongs", JSONArray().put(models.getJSONObject("track"))).put("rankings", JSONArray().put(models.getJSONObject("ranking")))
        values["authenticationStatus"] = JSONObject().put("type", "signedIn").put("account", models.getJSONObject("account"))
        values["accountSummary"] = models.getJSONObject("account")
        values["favoriteState"] = true; values["writeAcknowledgement"] = JSONObject.NULL
        val methods = fixture.getJSONObject("methods")
        assertEquals(PluginCapability.entries.flatMap { it.methods }.toSet(), methods.keys().asSequence().toSet())
        methods.keys().forEach { method ->
            val value = values.getValue(methods.getString(method))
            PluginWireContract.check(method, value.toString())
            visit(value) { path, original ->
                listOf(JSONObject.NULL, "wrong", 123, true, JSONObject(), JSONArray()).filter { kind(it) != kind(original) }.forEach { wrong ->
                    if (path.isEmpty()) {
                        assertTrue(method, runCatching { PluginWireContract.check(method, wrong.toString()) }.isFailure)
                    } else {
                        val copy = JSONObject(value.toString())
                        var parent: Any = copy
                        path.dropLast(1).forEach { key -> parent = if (parent is JSONObject) (parent as JSONObject).get(key) else (parent as JSONArray).get(key.toInt()) }
                        val key = path.last()
                        if (parent is JSONObject) (parent as JSONObject).put(key, wrong) else (parent as JSONArray).put(key.toInt(), wrong)
                        assertTrue("$method.${path.joinToString(".")}: ${kind(wrong)}", runCatching { PluginWireContract.check(method, copy.toString()) }.isFailure)
                    }
                }
            }
        }
    }
    private fun kind(v: Any): String = when (v) { JSONObject.NULL -> "null"; is JSONObject -> "object"; is JSONArray -> "array"; is String -> "string"; is Boolean -> "boolean"; else -> "number" }
    private fun visit(value: Any, path: List<String> = emptyList(), action: (List<String>, Any) -> Unit) {
        if (value !is JSONObject && value !is JSONArray) { action(path, value); return }
        val keys = if (value is JSONObject) value.keys().asSequence().toList() else (0 until (value as JSONArray).length()).map(Int::toString)
        keys.forEach { key ->
            val child = if (value is JSONObject) value.get(key) else (value as JSONArray).get(key.toInt())
            action(path + key, child)
            if (child is JSONObject || child is JSONArray) visit(child, path + key, action)
        }
    }
}
