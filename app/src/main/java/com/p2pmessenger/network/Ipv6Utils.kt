package com.p2pmessenger.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Ipv6Status {
    data object Checking : Ipv6Status
    data class Available(val address: String) : Ipv6Status
    data object Unavailable : Ipv6Status
}

/**
 * Determines whether the active network gives us a globally-routable IPv6 address we can be
 * reached on. Note: some cellular carriers hand out a global IPv6 address but still firewall
 * unsolicited inbound connections at the network edge -- this check can only confirm we *have*
 * an address, not that peers can actually reach it; [network.P2pSocketManager] surfaces the
 * real answer to that the first time a connection attempt to/from a contact succeeds or fails.
 */
@Singleton
class Ipv6Utils @Inject constructor(@ApplicationContext private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentGlobalIpv6Address(): String? {
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return findGlobalIpv6(linkProperties)
    }

    fun observeStatus(): Flow<Ipv6Status> = callbackFlow {
        trySend(statusNow())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(statusFor(linkProperties))
            }

            override fun onLost(network: Network) {
                trySend(Ipv6Status.Unavailable)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                connectivityManager.getLinkProperties(network)?.let { trySend(statusFor(it)) }
            }
        }
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun statusNow(): Ipv6Status {
        val network = connectivityManager.activeNetwork ?: return Ipv6Status.Unavailable
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return Ipv6Status.Unavailable
        return statusFor(linkProperties)
    }

    private fun statusFor(linkProperties: LinkProperties): Ipv6Status {
        val address = findGlobalIpv6(linkProperties)
        return if (address != null) Ipv6Status.Available(address) else Ipv6Status.Unavailable
    }

    fun findGlobalIpv6(linkProperties: LinkProperties): String? =
        linkProperties.linkAddresses
            .mapNotNull { it.address as? Inet6Address }
            .firstOrNull { isGloballyRoutableIpv6(it) }
            ?.hostAddress
            ?.substringBefore('%') // strip the zone/scope id (e.g. "%wlan0") some platforms append
}

/**
 * Split out as a top-level, dependency-free function (only touches `java.net.Inet6Address`,
 * not any Android framework class) so it's exercisable from a plain JVM unit test -- see
 * `Ipv6RoutabilityTest`.
 */
fun isGloballyRoutableIpv6(address: Inet6Address): Boolean {
    if (address.isLinkLocalAddress || address.isSiteLocalAddress) return false
    if (address.isLoopbackAddress || address.isMulticastAddress) return false
    val firstByte = address.address[0].toInt() and 0xFF
    val isUniqueLocal = (firstByte and 0xFE) == 0xFC // fc00::/7, RFC 4193
    return !isUniqueLocal
}
