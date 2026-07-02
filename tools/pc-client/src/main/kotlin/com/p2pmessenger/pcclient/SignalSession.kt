package com.p2pmessenger.pcclient

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random

data class EncryptedEnvelope(val type: Int, val ciphertext: ByteArray)

/**
 * Same verified API calls as the app's `crypto/SignalSessionManager.kt`, adapted to run
 * synchronously against [InMemorySignalProtocolStore] instead of Room/coroutines.
 */
class SignalSession {
    val store: InMemorySignalProtocolStore
    private val secureRandom = SecureRandom()

    init {
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        store = InMemorySignalProtocolStore(identityKeyPair, registrationId)
    }

    val ownSignalName: String by lazy {
        fingerprintOf(store.identityKeyPair.publicKey.serialize())
    }

    private fun fingerprintOf(identityKeyPublicBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identityKeyPublicBytes)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun generateLocalKeyBundle(): LocalKeyBundle {
        val identityKeyPair = store.identityKeyPair

        val preKeyId = randomKeyId()
        val preKeyRecord = PreKeyRecord(preKeyId, ECKeyPair.generate())
        store.storePreKey(preKeyId, preKeyRecord)

        val signedPreKeyId = randomKeyId()
        val signedPreKeyKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identityKeyPair.privateKey.calculateSignature(
            signedPreKeyKeyPair.publicKey.serialize(),
        )
        val signedPreKeyRecord = SignedPreKeyRecord(
            signedPreKeyId,
            System.currentTimeMillis(),
            signedPreKeyKeyPair,
            signedPreKeySignature,
        )
        store.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord)

        val kyberPreKeyId = randomKeyId()
        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identityKeyPair.privateKey.calculateSignature(
            kyberKeyPair.publicKey.serialize(),
        )
        val kyberPreKeyRecord = KyberPreKeyRecord(
            kyberPreKeyId,
            System.currentTimeMillis(),
            kyberKeyPair,
            kyberSignature,
        )
        store.storeKyberPreKey(kyberPreKeyId, kyberPreKeyRecord)

        return LocalKeyBundle(
            registrationId = store.localRegistrationId,
            deviceId = SIGNAL_DEVICE_ID,
            identityKeyPublic = identityKeyPair.publicKey.serialize(),
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = signedPreKeyKeyPair.publicKey.serialize(),
            signedPreKeySignature = signedPreKeySignature,
            preKeyId = preKeyId,
            preKeyPublic = preKeyRecord.keyPair.publicKey.serialize(),
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKeyPublic = kyberKeyPair.publicKey.serialize(),
            kyberPreKeySignature = kyberSignature,
        )
    }

    fun processRemoteKeyBundle(remoteSignalName: String, bundle: LocalKeyBundle) {
        val ownAddress = SignalProtocolAddress(ownSignalName, SIGNAL_DEVICE_ID)
        val remoteAddress = SignalProtocolAddress(remoteSignalName, bundle.deviceId)
        val preKeyBundle = PreKeyBundle(
            bundle.registrationId,
            bundle.deviceId,
            bundle.preKeyId,
            ECPublicKey(bundle.preKeyPublic),
            bundle.signedPreKeyId,
            ECPublicKey(bundle.signedPreKeyPublic),
            bundle.signedPreKeySignature,
            IdentityKey(bundle.identityKeyPublic, 0),
            bundle.kyberPreKeyId,
            KEMPublicKey(bundle.kyberPreKeyPublic, 0),
            bundle.kyberPreKeySignature,
        )
        SessionBuilder(store, remoteAddress, ownAddress).process(preKeyBundle)
    }

    fun encrypt(remoteSignalName: String, deviceId: Int, plaintext: ByteArray): EncryptedEnvelope {
        val ownAddress = SignalProtocolAddress(ownSignalName, SIGNAL_DEVICE_ID)
        val remoteAddress = SignalProtocolAddress(remoteSignalName, deviceId)
        val cipher = SessionCipher(store, ownAddress, remoteAddress)
        val message = cipher.encrypt(plaintext)
        return EncryptedEnvelope(message.type, message.serialize())
    }

    fun decrypt(remoteSignalName: String, deviceId: Int, envelope: EncryptedEnvelope): ByteArray {
        val ownAddress = SignalProtocolAddress(ownSignalName, SIGNAL_DEVICE_ID)
        val remoteAddress = SignalProtocolAddress(remoteSignalName, deviceId)
        val cipher = SessionCipher(store, ownAddress, remoteAddress)
        return when (envelope.type) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(envelope.ciphertext))
            else -> cipher.decrypt(SignalMessage(envelope.ciphertext))
        }
    }

    private fun randomKeyId(): Int = Random(secureRandom.nextLong()).nextInt(1, Int.MAX_VALUE - 1)
}
