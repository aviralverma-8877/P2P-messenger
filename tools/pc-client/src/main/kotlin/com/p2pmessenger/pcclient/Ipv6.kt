package com.p2pmessenger.pcclient

import java.net.Inet6Address
import java.net.NetworkInterface

/** Copied from the app's `network/Ipv6Utils.kt` -- same classification logic, no Android deps. */
fun isGloballyRoutableIpv6(address: Inet6Address): Boolean {
    if (address.isLinkLocalAddress || address.isSiteLocalAddress) return false
    if (address.isLoopbackAddress || address.isMulticastAddress) return false
    val firstByte = address.address[0].toInt() and 0xFF
    val isUniqueLocal = (firstByte and 0xFE) == 0xFC
    return !isUniqueLocal
}

fun findOwnGlobalIpv6(): String? {
    for (iface in NetworkInterface.getNetworkInterfaces()) {
        if (!iface.isUp || iface.isLoopback) continue
        for (addr in iface.inetAddresses) {
            if (addr is Inet6Address && isGloballyRoutableIpv6(addr)) {
                return addr.hostAddress.substringBefore('%')
            }
        }
    }
    return null
}
