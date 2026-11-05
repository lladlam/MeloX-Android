package com.lladlam.melox.core.plugin

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginProtocolContractTest {
    @Test fun androidUsesTheSameManifestAcceptanceFixturesAsSdk() {
        val contract = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "plugin-sdk/test/fixtures/manifest-cases.json") }.first { it.isFile }
        val fixture = JSONObject(contract.readText())
        val base = fixture.getJSONObject("base")
        val cases = fixture.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val input = JSONObject(base.toString())
            val patch = case.getJSONObject("patch")
            patch.keys().forEach { input.put(it, patch.get(it)) }
            val accepted = runCatching { PluginManifestParser.parse(input.toString()) }.isSuccess
            assertEquals(case.getString("name"), case.getBoolean("valid"), accepted)
        }
    }

    @Test fun everyHostCapabilityRequiresExactlyTheMethodsInThePublishedContract() {
        val schema = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "plugin-sdk/schema/manifest.schema.json") }.first { it.isFile }
        val methods = JSONObject(schema.readText()).getJSONObject("x-capability-methods")
        assertEquals(methods.keys().asSequence().toSet(), PluginCapability.entries.map { it.wireName }.toSet())
        PluginCapability.entries.forEach { capability ->
            val list = methods.getJSONArray(capability.wireName)
            assertEquals(capability.wireName, (0 until list.length()).map { list.getString(it) }.toSet(), capability.methods)
        }
    }
}
