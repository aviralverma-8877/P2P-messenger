package com.p2pmessenger.discovery

import kotlinx.serialization.Serializable

/**
 * Everything needed to start talking to a new peer directly: their Signal prekey bundle
 * (public material only) plus where to reach them over IPv6. This is what gets sent as an
 * SMS body or written to a BLE GATT characteristic during pairing.
 *
 * All key material is Base64 rather than raw bytes so this serializes cleanly to/from the
 * text-based SMS transport; the BLE path uses the same JSON encoded as UTF-8.
 */
@Serializable
data class PairingPayload(
    val displayName: String,
    val signalName: String,
    val registrationId: Int,
    val deviceId: Int,
    val identityKeyPublic: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val preKeyId: Int,
    val preKeyPublic: String,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: String,
    val kyberPreKeySignature: String,
    val ipv6Address: String,
    val port: Int,
)
