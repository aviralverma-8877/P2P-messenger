package com.p2pmessenger.discovery

import com.p2pmessenger.crypto.LocalKeyBundle
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Converts between our internal [LocalKeyBundle]/[PairingPayload] types and the wire formats
 * used for sharing an invite link (any app the user picks from the Android share sheet) and
 * BLE GATT (raw UTF-8 JSON bytes, chunked to the negotiated MTU by [ble.BleGattClient]).
 */
object PairingCodec {
    const val DEEP_LINK_SCHEME = "p2pmessenger"
    const val DEEP_LINK_HOST = "pair"
    private const val DEEP_LINK_PREFIX = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST?d="

    private val json = Json { ignoreUnknownKeys = true }

    fun toPayload(
        displayName: String,
        signalName: String,
        ipv6Address: String,
        port: Int,
        bundle: LocalKeyBundle,
    ): PairingPayload = PairingPayload(
        displayName = displayName,
        signalName = signalName,
        registrationId = bundle.registrationId,
        deviceId = bundle.deviceId,
        identityKeyPublic = bundle.identityKeyPublic.toBase64(),
        signedPreKeyId = bundle.signedPreKeyId,
        signedPreKeyPublic = bundle.signedPreKeyPublic.toBase64(),
        signedPreKeySignature = bundle.signedPreKeySignature.toBase64(),
        preKeyId = bundle.preKeyId,
        preKeyPublic = bundle.preKeyPublic.toBase64(),
        kyberPreKeyId = bundle.kyberPreKeyId,
        kyberPreKeyPublic = bundle.kyberPreKeyPublic.toBase64(),
        kyberPreKeySignature = bundle.kyberPreKeySignature.toBase64(),
        ipv6Address = ipv6Address,
        port = port,
    )

    fun toKeyBundle(payload: PairingPayload): LocalKeyBundle = LocalKeyBundle(
        registrationId = payload.registrationId,
        deviceId = payload.deviceId,
        identityKeyPublic = payload.identityKeyPublic.fromBase64(),
        signedPreKeyId = payload.signedPreKeyId,
        signedPreKeyPublic = payload.signedPreKeyPublic.fromBase64(),
        signedPreKeySignature = payload.signedPreKeySignature.fromBase64(),
        preKeyId = payload.preKeyId,
        preKeyPublic = payload.preKeyPublic.fromBase64(),
        kyberPreKeyId = payload.kyberPreKeyId,
        kyberPreKeyPublic = payload.kyberPreKeyPublic.fromBase64(),
        kyberPreKeySignature = payload.kyberPreKeySignature.fromBase64(),
    )

    /** Encodes our payload into a link that opens straight to pairing when tapped in any app. */
    fun encodeForShare(payload: PairingPayload): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
        return DEEP_LINK_PREFIX + encoded
    }

    fun decodeFromShareLink(link: String): PairingPayload? {
        if (!link.startsWith(DEEP_LINK_PREFIX)) return null
        val encoded = link.removePrefix(DEEP_LINK_PREFIX).substringBefore('&')
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            json.decodeFromString<PairingPayload>(bytes.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    fun encodeForBle(payload: PairingPayload): ByteArray = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

    fun decodeFromBle(bytes: ByteArray): PairingPayload? =
        runCatching { json.decodeFromString<PairingPayload>(bytes.toString(Charsets.UTF_8)) }.getOrNull()

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}
