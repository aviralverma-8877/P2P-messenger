package com.p2pmessenger.crypto

import com.p2pmessenger.data.signalstore.KyberPreKeyDao
import com.p2pmessenger.data.signalstore.KyberPreKeyEntity
import com.p2pmessenger.data.signalstore.OwnIdentityDao
import com.p2pmessenger.data.signalstore.OwnIdentityEntity
import com.p2pmessenger.data.signalstore.PreKeyDao
import com.p2pmessenger.data.signalstore.PreKeyEntity
import com.p2pmessenger.data.signalstore.RemoteIdentityDao
import com.p2pmessenger.data.signalstore.RemoteIdentityEntity
import com.p2pmessenger.data.signalstore.SessionDao
import com.p2pmessenger.data.signalstore.SessionEntity
import com.p2pmessenger.data.signalstore.SignedPreKeyDao
import com.p2pmessenger.data.signalstore.SignedPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verified against the real `org.signal:libsignal-client:0.96.4` jar (via `javap` on the
 * resolved artifact in the Gradle cache) rather than guessed -- see git history on this file
 * for the earlier guessed version if the API shifts again in a future `libsignal-android` bump.
 *
 * Every method here does a blocking Room call via [runBlocking] because [SignalProtocolStore]
 * is a synchronous Java interface invoked from inside libsignal's session/cipher calls.
 * Callers (see [SignalSessionManager]) must run those calls on a background dispatcher.
 */
@Singleton
class RoomSignalProtocolStore @Inject constructor(
    private val ownIdentityDao: OwnIdentityDao,
    private val remoteIdentityDao: RemoteIdentityDao,
    private val preKeyDao: PreKeyDao,
    private val signedPreKeyDao: SignedPreKeyDao,
    private val kyberPreKeyDao: KyberPreKeyDao,
    private val sessionDao: SessionDao,
    private val crypto: KeystoreCryptoHelper,
) : SignalProtocolStore {

    // Group messaging (Signal's "sender key" mechanism) isn't used by this app -- we only ever
    // do 1:1 sessions -- so this is a plain in-memory stub just to satisfy the interface.
    private val senderKeys = ConcurrentHashMap<String, SenderKeyRecord>()

    private fun SignalProtocolAddress.toKey(): String = "${name}.${deviceId}"

    // ---- Identity ----

    override fun getIdentityKeyPair(): IdentityKeyPair = runBlocking {
        val entity = ownIdentityDao.get()
            ?: error("Own identity not initialized -- call SignalSessionManager.ensureIdentity() first")
        IdentityKeyPair(crypto.decrypt(entity.encryptedIdentityKeyPair))
    }

    override fun getLocalRegistrationId(): Int = runBlocking {
        ownIdentityDao.get()?.registrationId
            ?: error("Own identity not initialized -- call SignalSessionManager.ensureIdentity() first")
    }

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange = runBlocking {
        val key = address.toKey()
        val existing = remoteIdentityDao.get(key)
        val changed = existing != null && !existing.identityKeyPublic.contentEquals(identityKey.serialize())
        remoteIdentityDao.upsert(RemoteIdentityEntity(key, identityKey.serialize()))
        if (changed) IdentityKeyStore.IdentityChange.REPLACED_EXISTING else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = runBlocking {
        // Trust-on-first-use: accept unseen identities automatically. If a peer's identity
        // key ever changes after that, we still accept it here but the UI layer should
        // surface a "safety number changed" warning -- not implemented yet in this pass.
        val existing = remoteIdentityDao.get(address.toKey()) ?: return@runBlocking true
        existing.identityKeyPublic.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = runBlocking {
        remoteIdentityDao.get(address.toKey())?.let { IdentityKey(it.identityKeyPublic, 0) }
    }

    // ---- PreKeys (one-time EC prekeys) ----

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = runBlocking {
        val entity = preKeyDao.get(preKeyId) ?: throw InvalidKeyIdException("No such prekey: $preKeyId")
        PreKeyRecord(crypto.decrypt(entity.encryptedRecord))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = runBlocking {
        preKeyDao.upsert(PreKeyEntity(preKeyId, crypto.encrypt(record.serialize())))
        Unit
    }

    override fun containsPreKey(preKeyId: Int): Boolean = runBlocking { preKeyDao.contains(preKeyId) }

    override fun removePreKey(preKeyId: Int) = runBlocking {
        preKeyDao.delete(preKeyId)
        Unit
    }

    // ---- Signed prekeys ----

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = runBlocking {
        val entity = signedPreKeyDao.get(signedPreKeyId)
            ?: throw InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        SignedPreKeyRecord(crypto.decrypt(entity.encryptedRecord))
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = runBlocking {
        signedPreKeyDao.getAll().map { SignedPreKeyRecord(crypto.decrypt(it.encryptedRecord)) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) = runBlocking {
        signedPreKeyDao.upsert(SignedPreKeyEntity(signedPreKeyId, crypto.encrypt(record.serialize())))
        Unit
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        runBlocking { signedPreKeyDao.contains(signedPreKeyId) }

    override fun removeSignedPreKey(signedPreKeyId: Int) = runBlocking {
        signedPreKeyDao.delete(signedPreKeyId)
        Unit
    }

    // ---- Kyber prekeys (PQXDH) ----

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = runBlocking {
        val entity = kyberPreKeyDao.get(kyberPreKeyId)
            ?: throw InvalidKeyIdException("No such kyber prekey: $kyberPreKeyId")
        KyberPreKeyRecord(crypto.decrypt(entity.encryptedRecord))
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = runBlocking {
        kyberPreKeyDao.getAll().map { KyberPreKeyRecord(crypto.decrypt(it.encryptedRecord)) }
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) = runBlocking {
        kyberPreKeyDao.upsert(KyberPreKeyEntity(kyberPreKeyId, crypto.encrypt(record.serialize())))
        Unit
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
        runBlocking { kyberPreKeyDao.contains(kyberPreKeyId) }

    // We never generate "last-resort" Kyber prekeys (see SignalSessionManager) -- every one we
    // hand out is one-time-use, so per the real contract of this method (verified against
    // libsignal's KyberPreKeyStore.java doc comment) it should simply be removed, the same way
    // removePreKey() retires a one-time EC prekey.
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = runBlocking {
        kyberPreKeyDao.delete(kyberPreKeyId)
        Unit
    }

    // ---- Sessions ----

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? = runBlocking {
        sessionDao.get(address.toKey())?.let { SessionRecord(crypto.decrypt(it.encryptedRecord)) }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> =
        runBlocking {
            addresses.mapNotNull { addr ->
                sessionDao.get(addr.toKey())?.let { SessionRecord(crypto.decrypt(it.encryptedRecord)) }
            }.toMutableList()
        }

    override fun getSubDeviceSessions(name: String): MutableList<Int> = runBlocking {
        sessionDao.getAllForName(name)
            .mapNotNull { it.address.substringAfterLast('.', "").toIntOrNull() }
            .toMutableList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) = runBlocking {
        sessionDao.upsert(SessionEntity(address.toKey(), crypto.encrypt(record.serialize())))
        Unit
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        runBlocking { sessionDao.contains(address.toKey()) }

    override fun deleteSession(address: SignalProtocolAddress) = runBlocking {
        sessionDao.delete(address.toKey())
        Unit
    }

    override fun deleteAllSessions(name: String) = runBlocking {
        sessionDao.deleteAllForName(name)
        Unit
    }

    // ---- Sender keys (group messaging -- unused by this app, see class doc) ----

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys["${sender.toKey()}.$distributionId"] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        senderKeys["${sender.toKey()}.$distributionId"]

    suspend fun initializeOwnIdentityIfNeeded(registrationId: Int, identityKeyPair: IdentityKeyPair) {
        if (ownIdentityDao.get() == null) {
            ownIdentityDao.upsert(
                OwnIdentityEntity(
                    registrationId = registrationId,
                    encryptedIdentityKeyPair = crypto.encrypt(identityKeyPair.serialize()),
                ),
            )
        }
    }

    suspend fun hasOwnIdentity(): Boolean = ownIdentityDao.get() != null
}
