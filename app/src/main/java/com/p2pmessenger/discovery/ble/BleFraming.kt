package com.p2pmessenger.discovery.ble

/**
 * A pairing payload (a few hundred bytes of JSON) is usually bigger than one GATT write/notify
 * can carry, so we split it into `FRAME_DATA` chunks followed by a `FRAME_END` marker, sized to
 * fit whatever MTU was negotiated for the connection.
 */
object BleFraming {
    fun buildFrames(payload: ByteArray, chunkSize: Int): List<ByteArray> {
        require(chunkSize > 0)
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            frames += byteArrayOf(BleConstants.FRAME_DATA) + payload.copyOfRange(offset, end)
            offset = end
        }
        frames += byteArrayOf(BleConstants.FRAME_END)
        return frames
    }
}

/** Accumulates incoming frames from either the GATT server or client side until FRAME_END. */
class BleFrameAssembler {
    private val buffer = mutableListOf<Byte>()

    /** Returns the fully-reassembled payload once a FRAME_END arrives, else null. */
    fun accept(frame: ByteArray): ByteArray? {
        if (frame.isEmpty()) return null
        return when (frame[0]) {
            BleConstants.FRAME_DATA -> {
                for (i in 1 until frame.size) buffer.add(frame[i])
                null
            }
            BleConstants.FRAME_END -> buffer.toByteArray()
            else -> null
        }
    }

    fun reset() = buffer.clear()
}
