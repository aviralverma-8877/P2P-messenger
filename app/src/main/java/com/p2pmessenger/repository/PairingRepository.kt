package com.p2pmessenger.repository

import com.p2pmessenger.crypto.SignalSessionManager
import com.p2pmessenger.data.ContactDao
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.discovery.IncomingPairing
import com.p2pmessenger.discovery.PairingCodec
import com.p2pmessenger.discovery.PairingPayload
import com.p2pmessenger.discovery.PairingSource
import com.p2pmessenger.network.Ipv6Utils
import com.p2pmessenger.network.P2pSocketManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val signalSessionManager: SignalSessionManager,
    private val ipv6Utils: Ipv6Utils,
    private val socketManager: P2pSocketManager,
) {
    private val _incomingPairings = MutableSharedFlow<IncomingPairing>(extraBufferCapacity = 8)
    val incomingPairings: SharedFlow<IncomingPairing> = _incomingPairings.asSharedFlow()

    /** Called by [com.p2pmessenger.discovery.sms.SmsPairingReceiver] and the BLE coordinator. */
    suspend fun onIncomingPairing(incoming: IncomingPairing) {
        _incomingPairings.emit(incoming)
    }

    /** Builds the bundle we hand to a new contact via SMS or BLE. Null if we have no IPv6 address. */
    suspend fun buildOutgoingPayload(displayName: String): PairingPayload? {
        val ipv6 = ipv6Utils.currentGlobalIpv6Address() ?: return null
        val bundle = signalSessionManager.generateLocalKeyBundle()
        val ownName = signalSessionManager.ownSignalName()
        return PairingCodec.toPayload(displayName, ownName, ipv6, P2pSocketManager.LISTEN_PORT, bundle)
    }

    /** Establishes the Signal session, saves the contact, and opens a direct connection. */
    suspend fun acceptPairing(incoming: IncomingPairing): ContactEntity {
        val payload = incoming.payload
        val bundle = PairingCodec.toKeyBundle(payload)
        signalSessionManager.processRemoteKeyBundle(payload.signalName, bundle)

        val existing = contactDao.getBySignalName(payload.signalName)
        val contact = ContactEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            displayName = payload.displayName,
            signalName = payload.signalName,
            deviceId = payload.deviceId,
            phoneNumber = existing?.phoneNumber
                ?: incoming.originHint.takeIf { incoming.source == PairingSource.SMS },
            identityKeyPublic = bundle.identityKeyPublic,
            lastKnownIpv6 = payload.ipv6Address,
            lastKnownPort = payload.port,
            lastSeenAtEpochMs = System.currentTimeMillis(),
            pairedViaBle = incoming.source == PairingSource.BLE,
            createdAtEpochMs = existing?.createdAtEpochMs ?: System.currentTimeMillis(),
        )
        contactDao.upsert(contact)
        socketManager.connect(contact.signalName, payload.ipv6Address, payload.port)
        return contact
    }
}
