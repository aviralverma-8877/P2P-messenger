package com.p2pmessenger.discovery

enum class PairingSource { LINK, BLE }

/** A pairing payload we received, tagged with how it arrived. */
data class IncomingPairing(
    val payload: PairingPayload,
    val source: PairingSource,
    /** The BLE device address for BLE -- purely informational, null for a shared link. */
    val originHint: String?,
)
