package com.lladlam.melox.core.remoteconfig

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

data class MeloXVerifiedRemoteConfig(
    val keyId: String,
    val config: MeloXRemoteConfig,
)

class MeloXRemoteConfigVerifier(
    publicKeyDer: ByteArray = Base64.getDecoder().decode(PinnedPublicKeyBase64),
    private val allowedKeyId: String = KeyId,
) {
    private val publicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(publicKeyDer))
        .also { key ->
            require(key is ECPublicKey && key.params.curve.field.fieldSize == 256) {
                "Remote config key must be P-256"
            }
        }

    fun verify(envelopeText: String): MeloXVerifiedRemoteConfig {
        require(envelopeText.toByteArray().size <= MaxEnvelopeBytes) { "Remote config is too large" }
        val envelope = JSONObject(envelopeText)
        val keyId = envelope.getString("keyId")
        require(keyId == allowedKeyId) { "Unknown remote config key" }
        require(envelope.getString("algorithm") == Algorithm) { "Unsupported remote config algorithm" }
        val payload = Base64.getUrlDecoder().decode(envelope.getString("payload"))
        val signatureBytes = Base64.getDecoder().decode(envelope.getString("signature"))
        val signature = Signature.getInstance(Algorithm)
        signature.initVerify(publicKey)
        signature.update(payload)
        require(signature.verify(signatureBytes)) { "Remote config signature verification failed" }
        return MeloXVerifiedRemoteConfig(keyId, MeloXRemoteConfig.parse(payload))
    }

    companion object {
        const val KeyId = "melox-remote-config-2026-01"
        const val Algorithm = "SHA256withECDSA"
        const val MaxEnvelopeBytes = 256 * 1024
        const val PinnedPublicKeyBase64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEyGw1cwv89ce4hf4u1P7u3GQ1ANzDw8yiCe5AZ6JpQsML1tcX7adS/L3ztpZMrk0OZ3tOh7QqTJixdTghGjo2YQ=="
    }
}
