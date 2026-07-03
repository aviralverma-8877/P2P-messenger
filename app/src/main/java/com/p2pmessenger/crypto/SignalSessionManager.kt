package com.p2pmessenger.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** We only ever run one logical "device" per identity in this app -- no multi-device support yet. */
const val SIGNAL_DEVICE_ID = 1

/** An encrypted message ready to go on the wire, or as received off the wire. */
data class EncryptedEnvelope(val type: Int, val ciphertext: ByteArray)

/** Everything a peer needs to build a Signal session with us, generated locally (no server). */
data class LocalKeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val identityKeyPublic: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val preKeyId: Int,
    val preKeyPublic: ByteArray,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: ByteArray,
    val kyberPreKeySignature: ByteArray,
)

/**
 * Runs the Signal Protocol (X3DH/PQXDH handshake + Double Ratchet) entirely offline: prekey
 * bundles are generated here and exchanged directly with a peer over BLE or a shared invite link
 * instead of being fetched from Signal's servers. API calls here were verified against the real
 * `org.signal:libsignal-client:0.96.4` jar with `javap`, not guessed.
 *
 * Note that both [SessionBuilder] and [SessionCipher] need *our own* address as well as the
 * remote one (libsignal supports one store serving multiple local identities), so every
 * session operation below resolves [ownSignalName] first.
 */
@Singleton
class SignalSessionManager @Inject constructor(
    private val store: RoomSignalProtocolStore,
) {
    private val secureRandom = SecureRandom()

    /** Generates our identity + an initial prekey pool the first time the app runs. */
    suspend fun ensureIdentity() = withContext(Dispatchers.IO) {
        if (store.hasOwnIdentity()) return@withContext
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        store.initializeOwnIdentityIfNeeded(registrationId, identityKeyPair)
    }

    /** Our stable contact identifier: a short fingerprint of our public identity key. */
    suspend fun ownSignalName(): String = withContext(Dispatchers.IO) {
        ensureIdentity()
        fingerprintOf(store.identityKeyPair.publicKey.serialize())
    }

    fun fingerprintOf(identityKeyPublicBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identityKeyPublicBytes)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    /** Builds a fresh, single-use prekey bundle to hand to a new peer via BLE or a shared link. */
    suspend fun generateLocalKeyBundle(): LocalKeyBundle = withContext(Dispatchers.IO) {
        ensureIdentity()
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

        LocalKeyBundle(
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

    /**
     * Consumes a peer's [LocalKeyBundle] (received via BLE or a shared link) and establishes an outbound
     * Signal session with them -- the offline equivalent of fetching a prekey bundle from
     * Signal's server.
     */
    suspend fun processRemoteKeyBundle(remoteSignalName: String, bundle: LocalKeyBundle) =
        withContext(Dispatchers.IO) {
            val ownAddress = SignalProtocolAddress(ownSignalName(), SIGNAL_DEVICE_ID)
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

    suspend fun encrypt(remoteSignalName: String, deviceId: Int, plaintext: ByteArray): EncryptedEnvelope =
        withContext(Dispatchers.IO) {
            val ownAddress = SignalProtocolAddress(ownSignalName(), SIGNAL_DEVICE_ID)
            val remoteAddress = SignalProtocolAddress(remoteSignalName, deviceId)
            val cipher = SessionCipher(store, ownAddress, remoteAddress)
            val message = cipher.encrypt(plaintext)
            EncryptedEnvelope(message.type, message.serialize())
        }

    suspend fun decrypt(remoteSignalName: String, deviceId: Int, envelope: EncryptedEnvelope): ByteArray =
        withContext(Dispatchers.IO) {
            val ownAddress = SignalProtocolAddress(ownSignalName(), SIGNAL_DEVICE_ID)
            val remoteAddress = SignalProtocolAddress(remoteSignalName, deviceId)
            val cipher = SessionCipher(store, ownAddress, remoteAddress)
            when (envelope.type) {
                CiphertextMessage.PREKEY_TYPE ->
                    cipher.decrypt(PreKeySignalMessage(envelope.ciphertext))
                else ->
                    cipher.decrypt(SignalMessage(envelope.ciphertext))
            }
        }

    private fun randomKeyId(): Int = Random(secureRandom.nextLong()).nextInt(1, Int.MAX_VALUE - 1)
}
