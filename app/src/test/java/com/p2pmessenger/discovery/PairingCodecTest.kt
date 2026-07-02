package com.p2pmessenger.discovery

import com.p2pmessenger.crypto.LocalKeyBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodecTest {

    private fun sampleBundle() = LocalKeyBundle(
        registrationId = 12345,
        deviceId = 1,
        identityKeyPublic = byteArrayOf(1, 2, 3, 4, 5),
        signedPreKeyId = 7,
        signedPreKeyPublic = byteArrayOf(6, 7, 8),
        signedPreKeySignature = byteArrayOf(9, 10, 11, 12),
        preKeyId = 42,
        preKeyPublic = byteArrayOf(13, 14, 15),
        kyberPreKeyId = 99,
        kyberPreKeyPublic = ByteArray(64) { it.toByte() },
        kyberPreKeySignature = byteArrayOf(16, 17, 18),
    )

    @Test
    fun `payload round-trips to and from a key bundle`() {
        val bundle = sampleBundle()
        val payload = PairingCodec.toPayload("Alice", "abcd1234", "2001:db8::1", 47321, bundle)
        val restored = PairingCodec.toKeyBundle(payload)

        assertEquals(bundle.registrationId, restored.registrationId)
        assertEquals(bundle.deviceId, restored.deviceId)
        assertTrue(bundle.identityKeyPublic.contentEquals(restored.identityKeyPublic))
        assertEquals(bundle.signedPreKeyId, restored.signedPreKeyId)
        assertTrue(bundle.signedPreKeyPublic.contentEquals(restored.signedPreKeyPublic))
        assertTrue(bundle.signedPreKeySignature.contentEquals(restored.signedPreKeySignature))
        assertEquals(bundle.preKeyId, restored.preKeyId)
        assertTrue(bundle.preKeyPublic.contentEquals(restored.preKeyPublic))
        assertEquals(bundle.kyberPreKeyId, restored.kyberPreKeyId)
        assertTrue(bundle.kyberPreKeyPublic.contentEquals(restored.kyberPreKeyPublic))
        assertTrue(bundle.kyberPreKeySignature.contentEquals(restored.kyberPreKeySignature))
    }

    @Test
    fun `sms encoding round-trips through the marker prefix`() {
        val payload = PairingCodec.toPayload("Bob", "deadbeef", "2001:db8::2", 47321, sampleBundle())
        val sms = PairingCodec.encodeForSms(payload)

        assertTrue(sms.startsWith(PairingCodec.SMS_MARKER))
        val decoded = PairingCodec.decodeFromSms(sms)
        assertEquals(payload, decoded)
    }

    @Test
    fun `sms decoding ignores messages without our marker`() {
        assertEquals(null, PairingCodec.decodeFromSms("hey, are we still on for lunch?"))
    }

    @Test
    fun `ble encoding round-trips as utf-8 json bytes`() {
        val payload = PairingCodec.toPayload("Carol", "feedface", "2001:db8::3", 47321, sampleBundle())
        val bytes = PairingCodec.encodeForBle(payload)
        val decoded = PairingCodec.decodeFromBle(bytes)
        assertEquals(payload, decoded)
    }
}
