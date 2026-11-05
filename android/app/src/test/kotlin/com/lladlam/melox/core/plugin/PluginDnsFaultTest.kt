package com.lladlam.melox.core.plugin

import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

/** Deterministic resolver faults; not a claim of live authoritative-DNS/socket red-team coverage. */
class PluginDnsFaultTest {
    @Test fun publicToPrivateRebindingIsRejectedOnTheNextLookup() {
        val public = InetAddress.getByName("8.8.8.8")
        var answers = listOf(public)
        val dns = PluginPublicDns(setOf("api.example.com")) { answers }
        assertEquals(listOf(public), dns.lookup("api.example.com"))
        for (bad in listOf("127.0.0.1", "10.1.2.3", "169.254.169.254", "::1", "fd00::1", "fe80::1", "2001:db8::1")) {
            answers = listOf(public, InetAddress.getByName(bad))
            assertThrows(UnknownHostException::class.java) { dns.lookup("api.example.com") }
        }
        answers = listOf(InetAddress.getByName("2606:4700:4700::1111"))
        assertEquals(answers, dns.lookup("api.example.com"))
    }
    @Test fun undeclaredHostsNeverReachResolverAndEmptyAnswersFailClosed() {
        var calls = 0
        val dns = PluginPublicDns(setOf("api.example.com")) { calls++; emptyList() }
        assertThrows(IllegalArgumentException::class.java) { dns.lookup("evil.example.com") }
        assertEquals(0, calls)
        assertThrows(UnknownHostException::class.java) { dns.lookup("api.example.com") }
    }
}
