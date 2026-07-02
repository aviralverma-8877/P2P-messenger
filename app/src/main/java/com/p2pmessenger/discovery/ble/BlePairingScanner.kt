package com.p2pmessenger.discovery.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BleDiscoveredPeer(val device: BluetoothDevice, val rssi: Int, val lastSeenAtMs: Long)

/** Scans for nearby devices advertising our pairing service UUID. */
@Singleton
class BlePairingScanner @Inject constructor(@ApplicationContext private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanCallback: ScanCallback? = null

    private val _discoveredPeers = MutableStateFlow<List<BleDiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<BleDiscoveredPeer>> = _discoveredPeers.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val current = _discoveredPeers.value.toMutableList()
                val index = current.indexOfFirst { it.device.address == result.device.address }
                val peer = BleDiscoveredPeer(result.device, result.rssi, System.currentTimeMillis())
                if (index >= 0) current[index] = peer else current.add(peer)
                _discoveredPeers.value = current
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w("BlePairingScanner", "Scan failed: $errorCode")
            }
        }
        scanCallback = callback
        scanner.startScan(listOf(filter), settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        _discoveredPeers.value = emptyList()
    }
}
