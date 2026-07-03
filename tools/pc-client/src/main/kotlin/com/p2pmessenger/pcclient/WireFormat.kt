package com.p2pmessenger.pcclient

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Copied from the Android app's `discovery/PairingPayload.kt`, `network/WireMessage.kt`, and
 * `network/MessageFraming.kt` -- kept byte-for-byte wire compatible on purpose, since this PC
 * tool has to genuinely interoperate with the real app, not a reimplementation of it. If those
 * app-side files change shape, update this file to match.
 */
@Serializable
data class PairingPayload(
    val displayName: String,
    val signalName: String,
    val registrationId: Int,
    val deviceId: Int,
    val identityKeyPublic: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val preKeyId: Int,
    val preKeyPublic: String,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: String,
    val kyberPreKeySignature: String,
    val ipv6Address: String,
    val port: Int,
)

data class LocalKeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val identityKeyPublic: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val preKeyId: Int,
    val preKeyPublic: ByteArray,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: ByteArray,
    val kyberPreKeySignature: ByteArray,
)

const val SIGNAL_DEVICE_ID = 1

object PairingCodec {
    const val BLE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val BLE_CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun toPayload(
        displayName: String,
        signalName: String,
        ipv6Address: String,
        port: Int,
        bundle: LocalKeyBundle,
    ): PairingPayload = PairingPayload(
        displayName = displayName,
        signalName = signalName,
        registrationId = bundle.registrationId,
        deviceId = bundle.deviceId,
        identityKeyPublic = bundle.identityKeyPublic.toBase64(),
        signedPreKeyId = bundle.signedPreKeyId,
        signedPreKeyPublic = bundle.signedPreKeyPublic.toBase64(),
        signedPreKeySignature = bundle.signedPreKeySignature.toBase64(),
        preKeyId = bundle.preKeyId,
        preKeyPublic = bundle.preKeyPublic.toBase64(),
        kyberPreKeyId = bundle.kyberPreKeyId,
        kyberPreKeyPublic = bundle.kyberPreKeyPublic.toBase64(),
        kyberPreKeySignature = bundle.kyberPreKeySignature.toBase64(),
        ipv6Address = ipv6Address,
        port = port,
    )

    fun toKeyBundle(payload: PairingPayload): LocalKeyBundle = LocalKeyBundle(
        registrationId = payload.registrationId,
        deviceId = payload.deviceId,
        identityKeyPublic = payload.identityKeyPublic.fromBase64(),
        signedPreKeyId = payload.signedPreKeyId,
        signedPreKeyPublic = payload.signedPreKeyPublic.fromBase64(),
        signedPreKeySignature = payload.signedPreKeySignature.fromBase64(),
        preKeyId = payload.preKeyId,
        preKeyPublic = payload.preKeyPublic.fromBase64(),
        kyberPreKeyId = payload.kyberPreKeyId,
        kyberPreKeyPublic = payload.kyberPreKeyPublic.fromBase64(),
        kyberPreKeySignature = payload.kyberPreKeySignature.fromBase64(),
    )

    fun encodeForBle(payload: PairingPayload): ByteArray = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

    fun decodeFromBle(bytes: ByteArray): PairingPayload = json.decodeFromString(bytes.toString(Charsets.UTF_8))

    fun encodeJson(payload: PairingPayload): String = json.encodeToString(payload)

    fun decodeJson(text: String): PairingPayload = json.decodeFromString(text)

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}

// kotlinx.serialization's default polymorphic discriminator is the fully-qualified class name,
// which would otherwise be "com.p2pmessenger.pcclient.WireMessage.Text" here vs.
// "com.p2pmessenger.network.WireMessage.Text" on the real app (different package) -- the
// @SerialName overrides below force them to match so the two can actually talk to each other.
@Serializable
sealed interface WireMessage {
    @Serializable
    @SerialName("com.p2pmessenger.network.WireMessage.Text")
    data class Text(val id: String, val body: String, val timestampEpochMs: Long) : WireMessage

    @Serializable
    @SerialName("com.p2pmessenger.network.WireMessage.CallSignal")
    data class CallSignal(val callId: String, val kind: String, val data: String) : WireMessage

    @Serializable
    @SerialName("com.p2pmessenger.network.WireMessage.FileMeta")
    data class FileMeta(
        val id: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : WireMessage
}

object FrameKind {
    const val HELLO: Byte = 0x01
    const val MESSAGE: Byte = 0x02
    const val FILE_CHUNK: Byte = 0x03
}

/** Mirrors the app's `network/FileChunkFraming.kt` byte-for-byte. */
object FileChunkFraming {
    const val CHUNK_SIZE = 256 * 1024

    data class Chunk(val fileId: String, val chunkIndex: Int, val totalChunks: Int, val data: ByteArray)

    fun encode(fileId: String, chunkIndex: Int, totalChunks: Int, data: ByteArray): ByteArray {
        val fileIdBytes = fileId.toByteArray(Charsets.UTF_8)
        val buffer = java.io.ByteArrayOutputStream(fileIdBytes.size + data.size + 10)
        val dos = DataOutputStream(buffer)
        dos.writeShort(fileIdBytes.size)
        dos.write(fileIdBytes)
        dos.writeInt(chunkIndex)
        dos.writeInt(totalChunks)
        dos.write(data)
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): Chunk {
        val dis = DataInputStream(java.io.ByteArrayInputStream(bytes))
        val idLength = dis.readUnsignedShort()
        val idBytes = ByteArray(idLength)
        dis.readFully(idBytes)
        val chunkIndex = dis.readInt()
        val totalChunks = dis.readInt()
        val data = dis.readBytes()
        return Chunk(String(idBytes, Charsets.UTF_8), chunkIndex, totalChunks, data)
    }
}

data class Frame(val kind: Byte, val payload: ByteArray)

object MessageFraming {
    private const val MAX_FRAME_BYTES = 32 * 1024 * 1024

    fun writeFrame(output: OutputStream, kind: Byte, payload: ByteArray) {
        val dos = DataOutputStream(output)
        dos.writeInt(payload.size + 1)
        dos.writeByte(kind.toInt())
        dos.write(payload)
        dos.flush()
    }

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
