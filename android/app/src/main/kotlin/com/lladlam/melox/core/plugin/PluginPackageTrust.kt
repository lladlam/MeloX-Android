package com.lladlam.melox.core.plugin

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/** Package envelope v1, independent of the music API protocol. No platform/global publisher CA implied. */
object PluginPackageTrust {
    const val UNSIGNED = "unsigned"
    data class Publisher(val fingerprint: String, val publicKey: String)
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    internal fun verify(files: Map<String, ByteArray>): Publisher? {
        val integrity = files["integrity.json"]
        require(integrity != null || "publisher.json" !in files) { "signed package requires integrity" }
        if (integrity == null) return null // Legacy community packages remain explicitly unverified.
        val raw = integrity.toString(Charsets.UTF_8)
        PluginJson.check(raw, 32 * 1024)
        val descriptor = JSONObject(raw)
        require(descriptor.opt("version") == 1 && descriptor.keys().asSequence().toSet() == setOf("version", "files"))
        val hashes = descriptor.getJSONObject("files")
        val payload = files.keys - setOf("integrity.json", "publisher.json")
        require(hashes.keys().asSequence().toSet() == payload) { "integrity file set mismatch" }
        payload.forEach { name -> require(hashes.opt(name) is String && hashes.getString(name) == sha256(files.getValue(name))) { "package digest mismatch" } }
        val signatureBytes = files["publisher.json"] ?: return null
        val publisherRaw = signatureBytes.toString(Charsets.UTF_8)
        PluginJson.check(publisherRaw, 8192)
        val value = JSONObject(publisherRaw)
        require(value.keys().asSequence().toSet() == setOf("algorithm", "publicKey", "signature") && value.getString("algorithm") == "RSA-SHA256")
        val encoded = requireNotNull(value.getString("publicKey").decodeBase64()).toByteArray()
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(encoded)) as RSAPublicKey
        require(key.modulus.bitLength() in 2048..4096 && key.publicExponent.bitLength() <= 32) { "unsupported publisher key" }
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update("MeloX-package-v1\n".toByteArray(Charsets.US_ASCII))
        verifier.update(integrity)
        require(verifier.verify(requireNotNull(value.getString("signature").decodeBase64()).toByteArray())) { "invalid publisher signature" }
        return Publisher(sha256(key.encoded), key.encoded.toByteString().base64())
    }

    fun requireUpdate(previous: PluginArchiveReader.Package, next: PluginArchiveReader.Package, approvedArchive: String?) {
        val before = previous.publisher?.fingerprint ?: UNSIGNED
        val after = next.publisher?.fingerprint ?: UNSIGNED
        require(before == after || approvedArchive == next.archiveSha256) { "PUBLISHER_CHANGE_APPROVAL_REQUIRED" }
    }
}
