package com.p2pmessenger.pcclient

import kotlinx.serialization.json.Json
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * A minimal, real-protocol-compatible stand-in for the Android app, used to test the app's
 * IPv6 P2P socket + Signal Protocol messaging from a PC. Not a UI -- driven by line commands on
 * stdin so `tools/ble_bridge.py` (or a human) can control it. See `tools/README.md`.
 *
 * Commands (stdin, one per line):
 *   GET_BUNDLE                 -> prints "BUNDLE <json pairing payload>"
 *   PAIR <json pairing payload> -> establishes the Signal session + connects to the peer's
 *                                  IPv6 socket; prints "PAIRED <signalName>" or "PAIR_FAILED <reason>"
 *   SEND <text>                -> encrypts and sends a text message to the paired peer; prints "SENT"
 *   QUIT                       -> exits
 *
 * Output lines you should watch for:
 *   READY <port> <ipv6>        -> listening socket is up
 *   RECV <text>                -> a decrypted message arrived from the peer
 *   LOG <message>               -> diagnostics
 */
private const val LISTEN_PORT = 47321

private val json = Json { ignoreUnknownKeys = true }
private val session = SignalSession()
private var peerSignalName: String? = null
private var peerSocket: Socket? = null
private val writeLock = Any()

fun main() {
    val ownIpv6 = findOwnGlobalIpv6()
    if (ownIpv6 == null) {
        println("LOG No global IPv6 address found on this machine -- messaging will not work.")
    }

    val serverSocket = ServerSocket()
    serverSocket.reuseAddress = true
    serverSocket.bind(InetSocketAddress(Inet6Address.getByName("::"), LISTEN_PORT))
    println("READY $LISTEN_PORT ${ownIpv6 ?: "none"}")
    println("LOG own signalName=${session.ownSignalName}")

    val executor = Executors.newCachedThreadPool()
    executor.submit {
        while (true) {
            val socket = try {
                serverSocket.accept()
            } catch (e: Exception) {
                break
            }
            executor.submit { handleIncomingConnection(socket) }
        }
    }

    generateSequence(::readLine).forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed == "GET_BUNDLE" -> handleGetBundle(ownIpv6)
            trimmed.startsWith("PAIR ") -> handlePair(trimmed.removePrefix("PAIR ").trim())
            trimmed.startsWith("SEND ") -> handleSend(trimmed.removePrefix("SEND "))
            trimmed == "QUIT" -> exitProcess(0)
            trimmed.isEmpty() -> Unit
            else -> println("LOG unrecognized command: $trimmed")
        }
    }
}

private fun handleGetBundle(ownIpv6: String?) {
    val bundle = session.generateLocalKeyBundle()
    val payload = PairingCodec.toPayload(
        displayName = "pc-client",
        signalName = session.ownSignalName,
        ipv6Address = ownIpv6 ?: "::1",
        port = LISTEN_PORT,
        bundle = bundle,
    )
    println("BUNDLE ${PairingCodec.encodeJson(payload)}")
}

private fun handlePair(payloadJson: String) {
    try {
        val payload = PairingCodec.decodeJson(payloadJson)
        session.processRemoteKeyBundle(payload.signalName, PairingCodec.toKeyBundle(payload))
        peerSignalName = payload.signalName

        val socket = Socket()
        socket.connect(InetSocketAddress(InetAddress.getByName(payload.ipv6Address), payload.port), 10_000)
        peerSocket = socket
        synchronized(writeLock) {
            MessageFraming.writeFrame(socket.getOutputStream(), FrameKind.HELLO, session.ownSignalName.toByteArray())
        }
        Executors.newSingleThreadExecutor().submit { readLoop(payload.signalName, socket) }

        println("PAIRED ${payload.signalName}")
    } catch (e: Exception) {
        println("PAIR_FAILED ${e.message}")
    }
}

private fun handleSend(text: String) {
    val name = peerSignalName
    val socket = peerSocket
    if (name == null || socket == null) {
        println("SEND_FAILED not paired yet")
        return
    }
    try {
        val message = WireMessage.Text(
            id = java.util.UUID.randomUUID().toString(),
            body = text,
            timestampEpochMs = System.currentTimeMillis(),
        )
        val plaintext = json.encodeToString(WireMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        val envelope = session.encrypt(name, SIGNAL_DEVICE_ID, plaintext)
        val framePayload = byteArrayOf(envelope.type.toByte()) + envelope.ciphertext
        synchronized(writeLock) {
            MessageFraming.writeFrame(socket.getOutputStream(), FrameKind.MESSAGE, framePayload)
        }
        println("SENT")
    } catch (e: Exception) {
        println("SEND_FAILED ${e.message}")
    }
}

private fun handleIncomingConnection(socket: Socket) {
    try {
        val first = MessageFraming.readFrame(socket.getInputStream()) ?: return
        if (first.kind != FrameKind.HELLO) return
        val remoteName = first.payload.toString(Charsets.UTF_8)
        peerSignalName = remoteName
        peerSocket = socket
        println("LOG inbound connection from $remoteName")
        readLoop(remoteName, socket)
    } catch (e: Exception) {
        println("LOG inbound connection error: ${e.message}")
    }
}

private fun readLoop(remoteName: String, socket: Socket) {
    try {
        val input = socket.getInputStream()
        while (true) {
            val frame = MessageFraming.readFrame(input) ?: break
            if (frame.kind != FrameKind.MESSAGE || frame.payload.isEmpty()) continue
            val envelopeType = frame.payload[0].toInt()
            val ciphertext = frame.payload.copyOfRange(1, frame.payload.size)
            val plaintext = session.decrypt(remoteName, SIGNAL_DEVICE_ID, EncryptedEnvelope(envelopeType, ciphertext))
            val message = json.decodeFromString(WireMessage.serializer(), plaintext.toString(Charsets.UTF_8))
            if (message is WireMessage.Text) {
                println("RECV ${message.body}")
            } else {
                println("LOG received non-text message: $message")
            }
        }
    } catch (e: Exception) {
        println("LOG connection to $remoteName ended: ${e.message}")
    }
}
