package com.lladlam.melox.core.plugin

import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginRcSecurityTest {
    private val manifest = """{"id":"org.example.signed","name":"Signed","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":[]}"""
    private fun archive(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun signed(): MutableMap<String, ByteArray> {
        val files = linkedMapOf("manifest.json" to manifest.toByteArray(), "index.js" to "globalThis.meloxPlugin={};".toByteArray())
        val hashes = JSONObject(); files.forEach { (name, bytes) -> hashes.put(name, PluginPackageTrust.sha256(bytes)) }
        val integrity = JSONObject().put("version", 1).put("files", hashes).toString().toByteArray()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update("MeloX-package-v1\n".toByteArray()); update(integrity) }
        files["integrity.json"] = integrity
        files["publisher.json"] = JSONObject().put("algorithm", "RSA-SHA256")
            .put("publicKey", Base64.getEncoder().encodeToString(pair.public.encoded))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().toByteArray()
        return files
    }

    @Test fun signatureBindsManifestEntryAndCompletePayloadSet() {
        val files = signed()
        val accepted = PluginArchiveReader.read(archive(files))
        assertTrue(accepted.integrityChecked); assertNotNull(accepted.publisher)
        for ((name, bytes) in listOf("index.js" to "tampered".toByteArray(), "manifest.json" to manifest.replace("1.0.0", "2.0.0").toByteArray(), "extra.txt" to byteArrayOf(1))) {
            val changed = files.toMutableMap().apply { put(name, bytes) }
            assertThrows(IllegalArgumentException::class.java) { PluginArchiveReader.read(archive(changed)) }
        }
        val missing = files.toMutableMap().apply { remove("integrity.json") }
        assertThrows(IllegalArgumentException::class.java) { PluginArchiveReader.read(archive(missing)) }
        val signature = JSONObject(files.getValue("publisher.json").toString(Charsets.UTF_8))
        signature.put("signature", Base64.getEncoder().encodeToString(ByteArray(256)))
        assertThrows(IllegalArgumentException::class.java) { PluginArchiveReader.read(archive(files.toMutableMap().apply { put("publisher.json", signature.toString().toByteArray()) })) }
    }

    @Test fun publisherRotationAndSignatureRemovalRequireExactArchiveApproval() {
        val first = PluginArchiveReader.read(archive(signed()))
        val second = PluginArchiveReader.read(archive(signed()))
        val unsigned = PluginArchiveReader.read(archive(mapOf("manifest.json" to manifest.toByteArray(), "index.js" to "x".toByteArray())))
        PluginPackageTrust.requireUpdate(first, first, null)
        assertThrows(IllegalArgumentException::class.java) { PluginPackageTrust.requireUpdate(first, second, null) }
        assertThrows(IllegalArgumentException::class.java) { PluginPackageTrust.requireUpdate(first, unsigned, null) }
        assertThrows(IllegalArgumentException::class.java) { PluginPackageTrust.requireUpdate(first, second, first.archiveSha256) }
        PluginPackageTrust.requireUpdate(first, second, second.archiveSha256)
    }

    @Test fun boundsApplyBeforeRecursiveJsonAndScriptDecoding() {
        PluginJson.check("""{"quoted":"[[[[{\\\"text\\\"}]"}""")
        PluginJson.check("[".repeat(32) + "0" + "]".repeat(32))
        assertThrows(IllegalArgumentException::class.java) { PluginJson.check("[".repeat(33) + "0" + "]".repeat(33)) }
        assertEquals("PLUGIN_EXECUTION_FAILED", PluginIpcPolicy.errorCode("{\"code\":" + "[".repeat(100) + "0" + "]".repeat(100) + "}"))
        assertThrows(IllegalArgumentException::class.java) { PluginArchiveReader.read(archive(mapOf("manifest.json" to manifest.toByteArray(), "index.js" to ByteArray(128 * 1024 + 1) { 32 }))) }
    }

    @Test fun adaptiveAndDrmMediaAreRejectedExplicitly() {
        for ((url, format) in listOf("https://example.com/a.m3u8" to null, "https://example.com/a.mpd" to null,
            "https://example.com/stream" to "hls", "https://example.com/stream" to "application/dash+xml")) {
            assertThrows(IllegalArgumentException::class.java) { PluginMediaFormats.check(url, format) }
        }
        assertThrows(IllegalArgumentException::class.java) { PluginMediaFormats.check("https://example.com/a.mp4", "mp4", true) }
        PluginMediaFormats.check("https://example.com/a.wav", "wav")
    }

    @Test fun httpBrokerRejectsCoercedModelsAndUnauthorizedOriginsBeforeNetwork() {
        val parsed = PluginManifestParser.parse(manifest)
        PluginHttpBroker(parsed).use { broker ->
            for (request in listOf("""{"url":123}""", """{"url":"https://example.com","method":123}""",
                """{"url":"https://example.com","headers":[]} """, """{"url":"https://example.com","body":{}}""",
                """{"url":"http://127.0.0.1"}""", """{"url":"https://example.com"}""")) {
                assertThrows(IllegalArgumentException::class.java) { broker.request(request) }
            }
        }
    }
}
