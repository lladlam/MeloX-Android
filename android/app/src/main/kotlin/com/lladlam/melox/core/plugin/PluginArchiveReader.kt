package com.lladlam.melox.core.plugin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** Reads bounded archives in memory; it never extracts attacker-supplied paths. */
object PluginArchiveReader {
    private const val MaxArchiveBytes = 4 * 1024 * 1024
    private const val MaxExpandedBytes = 8 * 1024 * 1024
    private const val MaxFiles = 64
    private val safeSegment = Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}")

    data class Package(val manifest: PluginManifest, val script: String)

    fun read(bytes: ByteArray): Package {
        require(bytes.isNotEmpty() && bytes.size <= MaxArchiveBytes) { "invalid plugin archive size" }
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && name.length <= 512 && name.split('/').all {
                    it != "." && it != ".." && safeSegment.matches(it)
                }) { "invalid archive path" }
                require(name !in files && files.size < MaxFiles) { "duplicate or excessive archive entries" }
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MaxExpandedBytes) { "expanded plugin archive too large" }
                    out.write(buffer, 0, count)
                }
                files[name] = out.toByteArray()
                zip.closeEntry()
            }
        }
        val manifest = PluginManifestParser.parse(decode(files["manifest.json"] ?: error("missing manifest")))
        val script = decode(files[manifest.entry] ?: error("missing plugin entry"))
        require(script.isNotBlank()) { "empty plugin entry" }
        return Package(manifest, script)
    }

    private fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
