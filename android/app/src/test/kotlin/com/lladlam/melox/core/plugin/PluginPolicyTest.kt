package com.lladlam.melox.core.plugin

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PluginPolicyTest {
    @Test fun excludesPrivateReservedAndTransitionAddresses() {
        val blocked = listOf("0.0.0.0", "10.0.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1", "192.168.0.1",
            "100.64.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "255.255.255.255",
            "::1", "::", "fc00::1", "fe80::1", "2001:db8::1", "2002:7f00:1::", "2001:20::1", "3fff::1")
        blocked.forEach { assertFalse(it, PluginHttpBroker.isPublicAddress(InetAddress.getByName(it))) }
        listOf("1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "2001:4860:4860::8888").forEach {
            assertTrue(it, PluginHttpBroker.isPublicAddress(InetAddress.getByName(it)))
        }
    }

    @Test fun semanticVersionPrecedenceHandlesPrereleasesAndLargeComponents() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.1.0", "999999999999999999999.0.0")
        ordered.zipWithNext().forEach { (old, newer) -> assertTrue(PluginVersion.compare(newer, old) > 0) }
        listOf("01.0.0", "1.0", "1.0.0-01", "1.0.0-", "1.0.0+build").forEach { assertFalse(it, PluginVersion.valid(it)) }
    }
}
