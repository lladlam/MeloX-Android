package com.lladlam.melox.core.provider.jellyfin

/** Local credentials required to address one Jellyfin server. */
data class JellyfinSession(
    val serverUrl: String = "",
    val accessToken: String = "",
    val userId: String = "",
    val serverId: String = "",
    val userName: String = "",
) {
    val isLoggedIn: Boolean
        get() = serverUrl.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()

    val baseUrl: String
        get() = serverUrl.trimEnd('/')
}

data class JellyfinServerInfo(
    val id: String,
    val name: String,
    val version: String,
)
