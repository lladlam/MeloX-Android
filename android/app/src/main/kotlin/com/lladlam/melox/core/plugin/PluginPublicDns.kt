package com.lladlam.melox.core.plugin

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/** The exact validated address list is returned to the socket layer; there is no second lookup.
 * Validate every answer on every lookup, including mixed A/AAAA and public-to-private changes.
 */
internal class PluginPublicDns(
    private val domains: Set<String>,
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        require(hostname in domains) { "PERMISSION_DENIED" }
        val addresses = resolve(hostname).toList()
        if (addresses.isEmpty() || addresses.any { !PluginHttpBroker.isPublicAddress(it) })
            throw UnknownHostException("PERMISSION_DENIED")
        return addresses
    }
}
