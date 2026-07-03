package com.p2pmessenger.network

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayInputStream

/**
 * Encodes one chunk of a file transfer as a single plaintext blob -- this is what gets encrypted
 * (see [P2pSocketManager.sendFileChunk]) and carried inside a [FrameKind.FILE_CHUNK] frame.
 * Layout: `[2-byte fileId length][fileId UTF-8][4-byte chunkIndex][4-byte totalChunks][chunk bytes]`.
 */
object FileChunkFraming {
    /** Kept well under [MessageFraming]'s 32 MiB frame cap, with room for encryption overhead. */
    const val CHUNK_SIZE = 256 * 1024

    data class Chunk(val fileId: String, val chunkIndex: Int, val totalChunks: Int, val data: ByteArray)

    fun encode(fileId: String, chunkIndex: Int, totalChunks: Int, data: ByteArray): ByteArray {
        val fileIdBytes = fileId.toByteArray(Charsets.UTF_8)
        val buffer = ByteArrayOutputStream(fileIdBytes.size + data.size + 10)
        val dos = DataOutputStream(buffer)
        dos.writeShort(fileIdBytes.size)
        dos.write(fileIdBytes)
        dos.writeInt(chunkIndex)
        dos.writeInt(totalChunks)
        dos.write(data)
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): Chunk {
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        val idLength = dis.readUnsignedShort()
        val idBytes = ByteArray(idLength)
        dis.readFully(idBytes)
        val chunkIndex = dis.readInt()
        val totalChunks = dis.readInt()
        val data = dis.readBytes()
        return Chunk(String(idBytes, Charsets.UTF_8), chunkIndex, totalChunks, data)
    }
}
