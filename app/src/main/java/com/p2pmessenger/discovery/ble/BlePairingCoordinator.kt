package com.p2pmessenger.discovery.ble

import android.content.Context
import com.p2pmessenger.discovery.IncomingPairing
import com.p2pmessenger.discovery.PairingCodec
import com.p2pmessenger.discovery.PairingPayload
import com.p2pmessenger.discovery.PairingSource
import com.p2pmessenger.repository.PairingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the "Add contact via Bluetooth" screen: while it's open we simultaneously
 * advertise + run a GATT server (so a nearby peer can find and pair with *us*) and scan (so we
 * can find and pair with *them*). Whichever side initiates the GATT connection acts as client;
 * the other replies via the GATT server -- either direction ends with both sides holding the
 * other's [PairingPayload].
 *
 * Both paths hand their result straight to [PairingRepository.onIncomingPairing] -- that's the
 * single flow [com.p2pmessenger.ui.addcontact.AddContactViewModel] actually listens to (the same
 * one [com.p2pmessenger.discovery.sms.SmsPairingReceiver] feeds for SMS pairing). This class used
 * to keep its own separate `incomingPairings` flow that nothing was ever collecting, which meant
 * a device that discovered a peer over BLE decoded and replied to their bundle correctly but
 * never actually created the contact on its own side -- BLE pairing only completed for the
 * device that received the reply, not the device that replied.
 */
@Singleton
class BlePairingCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advertiser: BlePairingAdvertiser,
    private val scanner: BlePairingScanner,
    private val gattServer: BleGattServerManager,
    private val gattClient: BleGattClientManager,
    private val pairingRepository: PairingRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val discoveredPeers: StateFlow<List<BleDiscoveredPeer>> get() = scanner.discoveredPeers

    private var ourPayload: PairingPayload? = null

    fun start(ourPayload: PairingPayload) {
        this.ourPayload = ourPayload
        gattServer.start()
        advertiser.start { }
        scanner.start()

        scope.launch {
            gattServer.receivedPayloads.collect { (deviceAddress, bytes) ->
                val payload = PairingCodec.decodeFromBle(bytes) ?: return@collect
                this@BlePairingCoordinator.ourPayload?.let {
                    gattServer.sendPayload(deviceAddress, PairingCodec.encodeForBle(it))
                }
                pairingRepository.onIncomingPairing(IncomingPairing(payload, PairingSource.BLE, deviceAddress))
            }
        }
        scope.launch {
            gattClient.receivedPayload.collect { bytes ->
                val payload = PairingCodec.decodeFromBle(bytes) ?: return@collect
                pairingRepository.onIncomingPairing(IncomingPairing(payload, PairingSource.BLE, null))
            }
        }
    }

    /** Initiate pairing with a peer found by the scanner. */
    fun pairWith(peer: BleDiscoveredPeer) {
        val payload = ourPayload ?: return
        gattClient.connect(context, peer.device, PairingCodec.encodeForBle(payload))
    }

    fun stop() {
        advertiser.stop()
        scanner.stop()
        gattClient.disconnect()
        gattServer.stop()
        ourPayload = null
    }
}
