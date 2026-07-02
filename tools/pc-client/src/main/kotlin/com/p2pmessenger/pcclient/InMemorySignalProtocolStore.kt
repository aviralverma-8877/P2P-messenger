package com.p2pmessenger.pcclient

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

/**
 * Throwaway, in-memory twin of the Android app's `crypto/RoomSignalProtocolStore.kt` -- same
 * verified libsignal API calls, but backed by plain maps instead of Room + Android Keystore
 * since this test client only needs to live for one run.
 */
class InMemorySignalProtocolStore(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
) : SignalProtocolStore {

    private val remoteIdentities = ConcurrentHashMap<String, ByteArray>()
    private val preKeys = ConcurrentHashMap<Int, PreKeyRecord>()
    private val signedPreKeys = ConcurrentHashMap<Int, SignedPreKeyRecord>()
    private val kyberPreKeys = ConcurrentHashMap<Int, KyberPreKeyRecord>()
    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val senderKeys = ConcurrentHashMap<String, SenderKeyRecord>()

    private fun SignalProtocolAddress.toKey(): String = "$name.$deviceId"

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val key = address.toKey()
        val existing = remoteIdentities[key]
        val changed = existing != null && !existing.contentEquals(identityKey.serialize())
        remoteIdentities[key] = identityKey.serialize()
        return if (changed) IdentityKeyStore.IdentityChange.REPLACED_EXISTING else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = remoteIdentities[address.toKey()] ?: return true
        return existing.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        remoteIdentities[address.toKey()]?.let { IdentityKey(it, 0) }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        preKeys[preKeyId] ?: throw InvalidKeyIdException("No such prekey: $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record
    }

    override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        signedPreKeys[signedPreKeyId] ?: throw InvalidKeyIdException("No such signed prekey: $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = signedPreKeys.values.toList()

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeys.containsKey(signedPreKeyId)

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        kyberPreKeys[kyberPreKeyId] ?: throw InvalidKeyIdException("No such kyber prekey: $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = kyberPreKeys.values.toList()

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeys[kyberPreKeyId] = record
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = kyberPreKeys.containsKey(kyberPreKeyId)

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        kyberPreKeys.remove(kyberPreKeyId)
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? = sessions[address.toKey()]

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> =
        addresses.mapNotNull { sessions[it.toKey()] }.toMutableList()

    override fun getSubDeviceSessions(name: String): MutableList<Int> =
        sessions.keys.filter { it.startsWith("$name.") }
            .mapNotNull { it.substringAfterLast('.', "").toIntOrNull() }
            .toMutableList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessions[address.toKey()] = record
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = sessions.containsKey(address.toKey())

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address.toKey())
    }

    override fun deleteAllSessions(name: String) {
        sessions.keys.filter { it.startsWith("$name.") }.forEach { sessions.remove(it) }
    }

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys["${sender.toKey()}.$distributionId"] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        senderKeys["${sender.toKey()}.$distributionId"]
}
