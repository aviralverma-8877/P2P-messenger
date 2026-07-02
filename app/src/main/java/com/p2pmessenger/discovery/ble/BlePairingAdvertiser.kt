package com.p2pmessenger.discovery.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Broadcasts our custom service UUID so nearby devices running this app can find us. */
@Singleton
class BlePairingAdvertiser @Inject constructor(@ApplicationContext private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var callback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onResult: (Boolean) -> Unit) {
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            onResult(false)
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                onResult(true)
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w("BlePairingAdvertiser", "Advertise failed: $errorCode")
                onResult(false)
            }
        }
        callback = cb
        advertiser.startAdvertising(settings, data, cb)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
        callback?.let { advertiser?.stopAdvertising(it) }
        callback = null
    }
}
