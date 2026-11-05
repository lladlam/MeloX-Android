package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import com.lladlam.melox.core.music.model.SourceId
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Two instrumentation processes, separated by SIGKILL at an actual durable store boundary. */
internal object PluginRcCrashChecks {
    val stages = setOf("decision-durable", "archive-durable", "publisher-durable", "rollback-archive-durable", "rollback-publisher-durable")
    private fun sandbox(test: Instrumentation): Context = object : ContextWrapper(test.targetContext) {
        private val root = File(test.targetContext.cacheDir, "plugin-rc-crash-sandbox").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = root
    }
    private val source = SourceId.parse("plugin:org.melox.rc.crash")!!
    private fun archive(version: String, key: java.security.KeyPair? = null): ByteArray = ByteArrayOutputStream().apply {
        val files = linkedMapOf("manifest.json" to """{"id":"org.melox.rc.crash","name":"Crash fixture","version":"$version","apiVersion":1,"entry":"index.js","capabilities":[]}""".toByteArray(),
            "index.js" to "globalThis.meloxPlugin={};".toByteArray())
        if (key != null) {
            val hashes = org.json.JSONObject()
            files.forEach { (name, bytes) -> hashes.put(name, PluginPackageTrust.sha256(bytes)) }
            val integrity = org.json.JSONObject().put("version", 1).put("files", hashes).toString().toByteArray()
            val signature = java.security.Signature.getInstance("SHA256withRSA").apply {
                initSign(key.private); update("MeloX-package-v1\n".toByteArray()); update(integrity)
            }.sign()
            files["integrity.json"] = integrity
            files["publisher.json"] = org.json.JSONObject().put("algorithm", "RSA-SHA256")
                .put("publicKey", android.util.Base64.encodeToString(key.public.encoded, android.util.Base64.NO_WRAP))
                .put("signature", android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP)).toString().toByteArray()
        }
        ZipOutputStream(this).use { zip ->
            files.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry()
            }
        }
    }.toByteArray()

    fun interrupt(test: Instrumentation, stage: String, kind: String): Nothing {
        require(stage in stages && kind in setOf("unsigned", "rotation", "downgrade"))
        val context = sandbox(test)
        context.filesDir.deleteRecursively(); check(context.filesDir.mkdirs())
        val store = PluginPackageStore(context)
        fun pair() = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val old = archive("1.0.0", if (kind == "unsigned") null else pair())
        val next = archive("2.0.0", if (kind == "rotation") pair() else null)
        val prior = PluginArchiveReader.read(old)
        val candidate = PluginArchiveReader.read(next)
        if (kind != "unsigned") {
            check(runCatching { PluginPackageTrust.requireUpdate(prior, candidate, null) }.isFailure)
            check(runCatching { PluginPackageTrust.requireUpdate(prior, candidate, prior.archiveSha256) }.isFailure)
        }
        PluginPackageTrust.requireUpdate(prior, candidate, candidate.archiveSha256)
        store.install(old)
        store.setEnabledSources(setOf(source))
        val expected = org.json.JSONObject().put("kind", kind).put("stage", stage)
            .put("old", prior.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED)
            .put("next", candidate.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED)
        File(test.targetContext.cacheDir, "plugin-rc-crash-expected").outputStream().use {
            it.write(expected.toString().toByteArray()); it.fd.sync()
        }
        PluginPackageStore(context) { reached ->
            if (stage.startsWith("rollback-") && reached == "publisher-durable") throw java.io.IOException("synthetic commit failure")
            if (reached == stage) {
                File(test.targetContext.cacheDir, "plugin-rc-crash-marker").outputStream().use { out ->
                    out.write(stage.toByteArray()); out.fd.sync()
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                // Do not throw through commitArchive: that would test rollback rather than death.
                while (true) Thread.sleep(1000)
            }
        }.update(source, next) {}
        error("crash checkpoint was not reached")
    }

    fun recover(test: Instrumentation, stage: String, kind: String): String {
        require(stage in stages)
        val marker = File(test.targetContext.cacheDir, "plugin-rc-crash-marker")
        require(marker.readText() == stage)
        val context = sandbox(test)
        val store = PluginPackageStore(context)
        val installed = requireNotNull(store.read(source))
        val expectation = File(test.targetContext.cacheDir, "plugin-rc-crash-expected")
        val decision = org.json.JSONObject(expectation.readText())
        check(decision.getString("stage") == stage && decision.getString("kind") == kind)
        val oldExpected = stage == "decision-durable" || stage.startsWith("rollback-")
        val expected = if (oldExpected) "1.0.0" else "2.0.0"
        check(installed.manifest.version == expected)
        val fingerprint = decision.getString(if (oldExpected) "old" else "next")
        check(store.publisherFingerprint(source) == fingerprint)
        check((installed.publisher?.fingerprint ?: PluginPackageTrust.UNSIGNED) == fingerprint)
        check(source in store.enabledSources())
        check(context.filesDir.walk().none { it.isFile && it.name.endsWith(".transaction") })
        marker.delete(); expectation.delete(); context.filesDir.deleteRecursively()
        return "PASS $kind process death at $stage: package $expected, matching publisher, desired enabled state and journal recovery"
    }
}
