package com.p2pmessenger.discovery.ble

import java.util.UUID

/**
 * Custom 128-bit UUIDs for our pairing protocol (arbitrary, chosen from the "locally
 * assigned" UUID range -- not registered with the Bluetooth SIG, which is fine for a
 * proprietary app-to-app service like this one).
 */
object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val TRANSFER_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Frame types for our simple chunked-transfer-over-GATT protocol. */
    const val FRAME_DATA: Byte = 0x01
    const val FRAME_END: Byte = 0x02
}
