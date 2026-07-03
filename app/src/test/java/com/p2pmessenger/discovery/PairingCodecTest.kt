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
    fun `share link encoding round-trips through the deep link prefix`() {
        val payload = PairingCodec.toPayload("Bob", "deadbeef", "2001:db8::2", 47321, sampleBundle())
        val link = PairingCodec.encodeForShare(payload)

        assertTrue(link.startsWith("${PairingCodec.DEEP_LINK_SCHEME}://${PairingCodec.DEEP_LINK_HOST}?d="))
        val decoded = PairingCodec.decodeFromShareLink(link)
        assertEquals(payload, decoded)
    }

    @Test
    fun `share link decoding ignores links that aren't ours`() {
        assertEquals(null, PairingCodec.decodeFromShareLink("https://example.com/not-an-invite"))
    }

    @Test
    fun `ble encoding round-trips as utf-8 json bytes`() {
        val payload = PairingCodec.toPayload("Carol", "feedface", "2001:db8::3", 47321, sampleBundle())
        val bytes = PairingCodec.encodeForBle(payload)
        val decoded = PairingCodec.decodeFromBle(bytes)
        assertEquals(payload, decoded)
    }
}
