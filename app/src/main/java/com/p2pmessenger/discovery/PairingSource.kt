package com.p2pmessenger.discovery

enum class PairingSource { SMS, BLE }

/** A pairing payload we received, tagged with how it arrived. */
data class IncomingPairing(
    val payload: PairingPayload,
    val source: PairingSource,
    /** Phone number for SMS, or the BLE device address for BLE -- purely informational. */
    val originHint: String?,
)
