package com.p2pmessenger.discovery

import com.p2pmessenger.crypto.LocalKeyBundle
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Converts between our internal [LocalKeyBundle]/[PairingPayload] types and the wire formats
 * used for SMS (marker-prefixed JSON text, chunked into multipart SMS by Android itself) and
 * BLE GATT (raw UTF-8 JSON bytes, chunked to the negotiated MTU by [ble.BleGattClient]).
 */
object PairingCodec {
    /** Any inbound SMS not starting with this is ignored by [sms.SmsPairingReceiver]. */
    const val SMS_MARKER = "P2PMSG1:"

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

    fun encodeForSms(payload: PairingPayload): String = SMS_MARKER + json.encodeToString(payload)

    fun decodeFromSms(body: String): PairingPayload? {
        if (!body.startsWith(SMS_MARKER)) return null
        return runCatching { json.decodeFromString<PairingPayload>(body.removePrefix(SMS_MARKER)) }.getOrNull()
    }

    fun encodeForBle(payload: PairingPayload): ByteArray = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

    fun decodeFromBle(bytes: ByteArray): PairingPayload? =
        runCatching { json.decodeFromString<PairingPayload>(bytes.toString(Charsets.UTF_8)) }.getOrNull()

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}
