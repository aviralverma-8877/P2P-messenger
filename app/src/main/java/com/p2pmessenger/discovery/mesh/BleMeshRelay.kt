package com.p2pmessenger.discovery.mesh

import com.p2pmessenger.discovery.PairingPayload

/**
 * Extends BLE pairing range beyond one hop by having nearby app instances rebroadcast pairing
 * advertisements they've seen, gossip-style, so two devices slightly outside direct BLE range
 * of each other can still discover one another through an intermediate phone running this app.
 *
 * This is **not** a standards-compliant Bluetooth Mesh implementation (that's a whole separate
 * provisioning/transport spec) -- it's a deliberately simple store-and-forward relay scoped to
 * our own pairing-discovery use case. Out of scope for this first pass; wire up like this next:
 *
 * 1. [BleGattServerManager] gains a second characteristic ("relay inbox") that neighbors can
 *    write third-party [PairingPayload]s into.
 * 2. This class keeps a small time-boxed cache of payloads seen (directly or relayed) and
 *    periodically re-advertises/re-serves them to its own neighbors (with a hop-count cap to
 *    avoid unbounded flooding).
 * 3. [com.p2pmessenger.discovery.ble.BlePairingCoordinator] merges relayed payloads into the
 *    same `incomingPairings` flow as directly-discovered ones, tagged so the UI can show
 *    "discovered via <relay device>" instead of a direct RSSI.
 */
interface BleMeshRelay {
    fun start()
    fun stop()
    fun offer(payload: PairingPayload, hopCount: Int)
}

class NoOpBleMeshRelay : BleMeshRelay {
    override fun start() = Unit
    override fun stop() = Unit
    override fun offer(payload: PairingPayload, hopCount: Int) = Unit
}
