package com.p2pmessenger.network

import com.p2pmessenger.crypto.EncryptedEnvelope
import com.p2pmessenger.crypto.SIGNAL_DEVICE_ID
import com.p2pmessenger.crypto.SignalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every direct IPv6 socket to a paired contact: one persistent [Socket] each, kept alive
 * for as long as [com.p2pmessenger.network.P2pConnectionForegroundService] is running. Contacts
 * are addressed by their Signal "signalName" (identity key fingerprint) rather than a raw
 * IP, since a contact's address can change between sessions -- callers look the current
 * IPv6/port up via the contact repository and pass it to [connect].
 */
@Singleton
class P2pSocketManager @Inject constructor(
    private val signalSessionManager: SignalSessionManager,
) {
    companion object {
        const val LISTEN_PORT = 47321
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val connections = ConcurrentHashMap<String, PeerConnection>()
    private var serverSocket: ServerSocket? = null

    private val _incomingMessages = MutableSharedFlow<Pair<String, WireMessage>>(extraBufferCapacity = 64)
    /** (signalName of sender, decrypted message). */
    val incomingMessages: SharedFlow<Pair<String, WireMessage>> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connectionState: StateFlow<Map<String, Boolean>> = _connectionState.asStateFlow()

    private class PeerConnection(val socket: Socket) {
        val writeMutex = Mutex()
    }

    /** Starts accepting inbound connections on `[::]:LISTEN_PORT`. Safe to call more than once. */
    fun startListening() {
        if (serverSocket != null) return
        scope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(Inet6Address.getByName("::"), LISTEN_PORT))
                serverSocket = server
                while (isActive) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    scope.launch { handleAcceptedSocket(socket) }
                }
            } catch (e: IOException) {
                // Binding failed (port in use, or no IPv6 stack) -- the UI's Ipv6Status indicator
                // combined with per-contact connectionState is how we surface transport problems.
            }
        }
    }

    fun stopListening() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // ignore
        }
        serverSocket = null
        connections.values.forEach { it.socket.closeQuietly() }
        connections.clear()
        _connectionState.value = emptyMap()
    }

    /** Opens (or reuses) a direct connection to a contact and sends our HELLO. */
    suspend fun connect(signalName: String, ipv6Address: String, port: Int): Boolean {
        connections[signalName]?.takeIf { it.socket.isConnected && !it.socket.isClosed }?.let { return true }
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(InetAddress.getByName(ipv6Address), port), 10_000)
            val connection = registerConnection(signalName, socket)
            val ownName = signalSessionManager.ownSignalName()
            connection.writeMutex.withLock {
                MessageFraming.writeFrame(socket.getOutputStream(), FrameKind.HELLO, ownName.toByteArray(Charsets.UTF_8))
            }
            scope.launch { readLoop(signalName, socket) }
            true
        } catch (e: IOException) {
            false
        }
    }

    fun disconnect(signalName: String) {
        connections.remove(signalName)?.socket?.closeQuietly()
        _connectionState.value = _connectionState.value - signalName
    }

    suspend fun send(signalName: String, message: WireMessage): Boolean {
        val connection = connections[signalName] ?: return false
        return try {
            val plaintext = json.encodeToString(WireMessage.serializer(), message).toByteArray(Charsets.UTF_8)
            val envelope = signalSessionManager.encrypt(signalName, SIGNAL_DEVICE_ID, plaintext)
            val framePayload = byteArrayOf(envelope.type.toByte()) + envelope.ciphertext
            connection.writeMutex.withLock {
                MessageFraming.writeFrame(connection.socket.getOutputStream(), FrameKind.MESSAGE, framePayload)
            }
            true
        } catch (e: IOException) {
            connections.remove(signalName)
            _connectionState.value = _connectionState.value + (signalName to false)
            false
        }
    }

    private suspend fun handleAcceptedSocket(socket: Socket) {
        try {
            val first = MessageFraming.readFrame(socket.getInputStream())
            if (first == null || first.kind != FrameKind.HELLO) {
                socket.closeQuietly()
                return
            }
            val signalName = first.payload.toString(Charsets.UTF_8)
            registerConnection(signalName, socket)
            readLoop(signalName, socket)
        } catch (e: IOException) {
            socket.closeQuietly()
        }
    }

    private fun registerConnection(signalName: String, socket: Socket): PeerConnection {
        connections[signalName]?.socket?.closeQuietly()
        val connection = PeerConnection(socket)
        connections[signalName] = connection
        _connectionState.value = _connectionState.value + (signalName to true)
        return connection
    }

    private suspend fun readLoop(signalName: String, socket: Socket) {
        try {
            val input = socket.getInputStream()
            while (true) {
                val frame = MessageFraming.readFrame(input) ?: break
                if (frame.kind != FrameKind.MESSAGE || frame.payload.isEmpty()) continue
                val envelopeType = frame.payload[0].toInt()
                val ciphertext = frame.payload.copyOfRange(1, frame.payload.size)
                val plaintext = signalSessionManager.decrypt(
                    signalName,
                    SIGNAL_DEVICE_ID,
                    EncryptedEnvelope(envelopeType, ciphertext),
                )
                val message = json.decodeFromString(WireMessage.serializer(), plaintext.toString(Charsets.UTF_8))
                _incomingMessages.emit(signalName to message)
            }
        } catch (e: IOException) {
            // connection dropped -- reconnect is the caller's responsibility (see ContactRepository)
        } finally {
            connections.remove(signalName)
            _connectionState.value = _connectionState.value + (signalName to false)
            socket.closeQuietly()
        }
    }

    private fun Socket.closeQuietly() {
        try {
            close()
        } catch (e: IOException) {
            // ignore
        }
    }
}
