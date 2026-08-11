package com.lladlam.melox.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MeloXNetworkAvailability {
    fun isOnline(context: Context): Boolean {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Android does not guarantee that applications can issue a reliable ICMP ping.
     * For MeloX's online/offline quality decision, use a short TCP reachability
     * probe to the NetEase Music website instead. If music.163.com:443 is reachable,
     * online quality choices stay unlocked even when a local download exists.
     */
    suspend fun canReachNetease(
        context: Context,
        timeoutMs: Int = 1_800,
    ): Boolean {
        if (!isOnline(context)) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(NETEASE_HOST, NETEASE_HTTPS_PORT), timeoutMs)
                }
                true
            }.getOrDefault(false)
        }
    }

    private const val NETEASE_HOST = "music.163.com"
    private const val NETEASE_HTTPS_PORT = 443
}
