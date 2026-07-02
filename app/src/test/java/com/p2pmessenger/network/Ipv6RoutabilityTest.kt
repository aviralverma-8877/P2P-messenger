package com.p2pmessenger.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet6Address
import java.net.InetAddress

class Ipv6RoutabilityTest {

    private fun inet6(literal: String): Inet6Address = InetAddress.getByName(literal) as Inet6Address

    @Test
    fun `global unicast address is routable`() {
        assertTrue(isGloballyRoutableIpv6(inet6("2001:db8:85a3::8a2e:370:7334")))
    }

    @Test
    fun `link-local address is not routable`() {
        assertFalse(isGloballyRoutableIpv6(inet6("fe80::1")))
    }

    @Test
    fun `unique local (fc00__7) address is not routable`() {
        assertFalse(isGloballyRoutableIpv6(inet6("fd12:3456:789a:1::1")))
    }

    @Test
    fun `loopback address is not routable`() {
        assertFalse(isGloballyRoutableIpv6(inet6("::1")))
    }

    @Test
    fun `multicast address is not routable`() {
        assertFalse(isGloballyRoutableIpv6(inet6("ff02::1")))
    }
}
