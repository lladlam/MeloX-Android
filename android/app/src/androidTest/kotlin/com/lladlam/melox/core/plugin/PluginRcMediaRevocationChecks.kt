package com.lladlam.melox.core.plugin

import android.app.Instrumentation
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Delayed real response: runtime shutdown must cancel its broker, not await socket timeout. */
internal object PluginRcMediaRevocationChecks {
    fun run(test: Instrumentation, originText: String, evidence: MutableList<String>) {
        val origin = originText.toHttpUrl()
        val base = origin.toString().removeSuffix("/")
        val manifest = PluginManifestParser.parse("""{"id":"org.melox.rc.revocation","name":"Revocation","version":"1.0.0","apiVersion":1,"entry":"index.js","capabilities":[],"networkDomains":["${origin.host}"]}""")
        val runtime = PluginRuntime(test.targetContext, PluginArchiveReader.Package(manifest, "globalThis.meloxPlugin={};"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            runtime.mediaBroker().use { broker ->
                broker.mediaClient().newCall(Request.Builder().url("$base/fault/stall").build()).execute().use { response ->
                    check(response.code == 200)
                    val body = requireNotNull(response.body)
                    check(body.source().readByte() == 'x'.code.toByte())
                    val entered = CountDownLatch(1)
                    val pending = executor.submit<Boolean> {
                        entered.countDown()
                        runCatching { body.source().readByte() }.exceptionOrNull() is java.io.IOException
                    }
                    check(entered.await(2, TimeUnit.SECONDS))
                    runtime.close()
                    check(pending.get(2, TimeUnit.SECONDS)) { "runtime shutdown did not revoke streaming read" }
                }
            }
            check(runCatching { runtime.mediaBroker() }.isFailure)
            evidence += "PASS real stalled media response is cancelled on runtime shutdown before 15s socket timeout; closed runtime cannot create a broker"
        } finally { runtime.close(); executor.shutdownNow() }
    }
}
