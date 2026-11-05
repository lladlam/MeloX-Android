package com.lladlam.melox.core.plugin

/** Enforced again in the privileged host, independently of the worker's JS bootstrap. */
internal object PluginIpcPolicy {
    const val MAX_HOST_CALLS = 32
    fun validToken(expected: String, actual: String?): Boolean = actual != null && actual.length <= 64 &&
        java.security.MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))
    fun validHostCall(id: Int, count: Int, bytes: Int): Boolean = id in 1..MAX_HOST_CALLS && count in 1..MAX_HOST_CALLS && bytes in 0..48 * 1024
    fun methodAllowed(manifest: PluginManifest, method: String): Boolean = method == "__validate" || manifest.capabilities.any { method in it.methods }
    fun errorCode(raw: String): String {
        if (raw.toByteArray(Charsets.UTF_8).size > 1024) return "PLUGIN_EXECUTION_FAILED"
        val code = runCatching { PluginJson.check(raw, 1024); org.json.JSONObject(raw).opt("code") as? String }.getOrNull()
        return code?.takeIf { it in setOf("UNSUPPORTED_OPERATION", "INVALID_ARGUMENT", "LOGIN_REQUIRED", "AUTH_EXPIRED",
            "PERMISSION_DENIED", "RATE_LIMITED", "NETWORK_REQUEST_FAILED", "NOT_FOUND", "PLUGIN_ERROR") } ?: "PLUGIN_EXECUTION_FAILED"
    }
}
