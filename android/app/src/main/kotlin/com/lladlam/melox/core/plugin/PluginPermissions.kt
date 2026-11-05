package com.lladlam.melox.core.plugin

/** Comparisons use parsed permissions, never publisher descriptions. */
object PluginPermissions {
    fun expands(previous: PluginManifest, next: PluginManifest): Boolean =
        !previous.networkDomains.containsAll(next.networkDomains) ||
            !previous.capabilities.containsAll(next.capabilities) ||
            previous.authentication != next.authentication

    fun describe(manifest: PluginManifest): String = buildString {
        append("API ").append(manifest.apiVersion)
        append("\n能力：").append(manifest.capabilities.joinToString { it.wireName }.ifEmpty { "无" })
        append("\n网络域名：").append(manifest.networkDomains.sorted().joinToString().ifEmpty { "无网络权限" })
        manifest.authentication?.let {
            append("\n认证：").append(it.type)
            append("\n凭据域名：").append(it.domains.sorted().joinToString().ifEmpty { "无" })
            if (it.domains.isNotEmpty()) append("\n凭据请求头：").append(it.header)
            it.oauth?.let { oauth ->
                append("\n授权端点：").append(oauth.authorizationEndpoint)
                append("\nToken 端点：").append(oauth.tokenEndpoint)
                append("\n授权范围：").append(oauth.scopes.joinToString())
            }
        }
    }
}
