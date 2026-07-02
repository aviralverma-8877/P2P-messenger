package com.p2pmessenger.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object FrameKind {
    /** payload = UTF-8 signalName, sent once right after connecting so the accepting side knows who we are. */
    const val HELLO: Byte = 0x01

    /** payload = [1 byte Signal ciphertext type][remaining = ciphertext bytes]. */
    const val MESSAGE: Byte = 0x02
}

data class Frame(val kind: Byte, val payload: ByteArray)

/**
 * Simple length-prefixed framing over the raw TCP stream: `[4-byte big-endian length][1-byte
 * kind][length-1 bytes payload]`. Callers are responsible for serializing concurrent writes to
 * the same socket (see the per-connection mutex in [P2pSocketManager]) -- reads happen on a
 * single dedicated reader coroutine per connection so no locking is needed there.
 */
object MessageFraming {
    private const val MAX_FRAME_BYTES = 32 * 1024 * 1024 // guard against a misbehaving peer forcing huge allocations

    fun writeFrame(output: OutputStream, kind: Byte, payload: ByteArray) {
        val dos = DataOutputStream(output)
        dos.writeInt(payload.size + 1)
        dos.writeByte(kind.toInt())
        dos.write(payload)
        dos.flush()
    }

    /** Returns null once the stream is exhausted (peer closed the connection). */
    fun readFrame(input: InputStream): Frame? {
        val dis = DataInputStream(input)
        val length = try {
            dis.readInt()
        } catch (e: EOFException) {
            return null
        }
        if (length <= 0 || length > MAX_FRAME_BYTES) return null
        val kind = dis.readByte()
        val payload = ByteArray(length - 1)
        dis.readFully(payload)
        return Frame(kind, payload)
    }
}
