package com.p2pmessenger.discovery.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The peripheral side of pairing: hosts a GATT service with one characteristic used for our
 * simple chunked-frame protocol (see [BleFraming]). A connecting peer writes their payload to
 * this characteristic; once we've received it in full we notify them back with ours.
 */
@Singleton
class BleGattServerManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null

    private val assemblers = mutableMapOf<String, BleFrameAssembler>()
    private val connectedDevices = mutableMapOf<String, BluetoothDevice>()
    private val negotiatedMtu = mutableMapOf<String, Int>()

    // Android silently drops a notification if the previous one's radio transmission hasn't
    // finished when notifyCharacteristicChanged() is called again -- these queues plus
    // onNotificationSent() below give proper one-at-a-time flow control instead of firing every
    // frame in a tight loop (which was intermittently corrupting/truncating multi-frame payloads).
    private val pendingFrames = mutableMapOf<String, ArrayDeque<ByteArray>>()
    private val sendInFlight = mutableMapOf<String, Boolean>()

    private val _receivedPayloads = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 4)
    /** Emits (deviceAddress, fullPayloadBytes) whenever a connected central finishes a write. */
    val receivedPayloads: SharedFlow<Pair<String, ByteArray>> = _receivedPayloads.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        val characteristic = BluetoothGattCharacteristic(
            BleConstants.TRANSFER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val cccd = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(cccd)
        transferCharacteristic = characteristic

        val service = BluetoothGattService(BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)

        gattServer = bluetoothManager.openGattServer(context, callback)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        gattServer?.close()
        gattServer = null
        transferCharacteristic = null
        assemblers.clear()
        connectedDevices.clear()
    }

    /**
     * Sends our payload to a connected central, chunked conservatively. The negotiated MTU
     * (esp. cross-stack, e.g. a Windows central connecting to this Android peripheral) isn't a
     * reliable upper bound for how large a single notification can safely be -- large
     * multi-hundred-byte notifications sent back to back have been observed to silently drop a
     * frame, corrupting the reassembled payload. A small, fixed chunk size is far more robust
     * than trusting the reported MTU here.
     */
    fun sendPayload(deviceAddress: String, payload: ByteArray) {
        val chunkSize = ((negotiatedMtu[deviceAddress] ?: 23) - 3).coerceAtLeast(20).coerceAtMost(150)
        val queue = pendingFrames.getOrPut(deviceAddress) { ArrayDeque() }
        queue.addAll(BleFraming.buildFrames(payload, chunkSize))
        if (sendInFlight[deviceAddress] != true) {
            sendNextFrame(deviceAddress)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNextFrame(deviceAddress: String) {
        val queue = pendingFrames[deviceAddress]
        val frame = queue?.removeFirstOrNull()
        if (frame == null) {
            sendInFlight[deviceAddress] = false
            return
        }
        val device = connectedDevices[deviceAddress]
        val characteristic = transferCharacteristic
        if (device == null || characteristic == null) {
            sendInFlight[deviceAddress] = false
            return
        }
        sendInFlight[deviceAddress] = true
        characteristic.value = frame
        gattServer?.notifyCharacteristicChanged(device, characteristic, false)
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[device.address] = device
                    assemblers[device.address] = BleFrameAssembler()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    assemblers.remove(device.address)
                    negotiatedMtu.remove(device.address)
                    pendingFrames.remove(device.address)
                    sendInFlight.remove(device.address)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            negotiatedMtu[device.address] = mtu
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            sendNextFrame(device.address)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleConstants.TRANSFER_CHARACTERISTIC_UUID) {
                val assembler = assemblers.getOrPut(device.address) { BleFrameAssembler() }
                val complete = assembler.accept(value)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                if (complete != null) {
                    _receivedPayloads.tryEmit(device.address to complete)
                    assembler.reset()
                }
            } else if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }
}
