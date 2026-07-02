package com.p2pmessenger.discovery.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The central side of pairing: connects to a device found by [BlePairingScanner], writes our
 * payload in frames, and listens for the peer's own payload coming back as notifications.
 */
@Singleton
class BleGattClientManager @Inject constructor() {

    private var gatt: BluetoothGatt? = null
    private val assembler = BleFrameAssembler()
    private var pendingFrames: List<ByteArray> = emptyList()
    private var frameIndex = 0

    private val _receivedPayload = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val receivedPayload: SharedFlow<ByteArray> = _receivedPayload.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice, ourPayload: ByteArray) {
        frameIndex = 0
        assembler.reset()
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connected.value = false
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                pendingFrames = BleFraming.buildFrames(ourPayload, (mtu - 3).coerceAtLeast(20))
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(BleConstants.SERVICE_UUID)
                    ?.getCharacteristic(BleConstants.TRANSFER_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    g.disconnect()
                    return
                }
                g.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                } else {
                    _connected.value = true
                    writeNextFrame(g)
                }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                _connected.value = true
                writeNextFrame(g)
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                frameIndex++
                writeNextFrame(g)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val complete = assembler.accept(characteristic.value) ?: return
                _receivedPayload.tryEmit(complete)
                assembler.reset()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun writeNextFrame(g: BluetoothGatt) {
        if (frameIndex >= pendingFrames.size) return
        val characteristic = g.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.TRANSFER_CHARACTERISTIC_UUID) ?: return
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = pendingFrames[frameIndex]
        g.writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connected.value = false
    }
}
