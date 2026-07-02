package com.p2pmessenger.call

import com.p2pmessenger.di.ApplicationScope
import com.p2pmessenger.network.P2pSocketManager
import com.p2pmessenger.network.WireMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class CallSignalKind { OFFER, ANSWER, ICE_CANDIDATE, HANGUP }

@Serializable
data class IceCandidateData(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)

data class IncomingCallSignal(
    val fromSignalName: String,
    val callId: String,
    val kind: CallSignalKind,
    val raw: String,
)

/**
 * Carries WebRTC SDP offers/answers and ICE candidates over the same Signal-encrypted P2P
 * socket used for messages -- there's no separate signaling server, since the two peers are
 * already directly connected by the time a call is placed.
 */
@Singleton
class CallSignalingChannel @Inject constructor(
    private val socketManager: P2pSocketManager,
    @ApplicationScope appScope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _incomingSignals = MutableSharedFlow<IncomingCallSignal>(extraBufferCapacity = 16)
    val incomingSignals: SharedFlow<IncomingCallSignal> = _incomingSignals.asSharedFlow()

    init {
        appScope.launch {
            socketManager.incomingMessages.collect { (signalName, message) ->
                if (message is WireMessage.CallSignal) {
                    val kind = runCatching { CallSignalKind.valueOf(message.kind) }.getOrNull() ?: return@collect
                    _incomingSignals.emit(IncomingCallSignal(signalName, message.callId, kind, message.data))
                }
            }
        }
    }

    suspend fun sendOffer(contactSignalName: String, callId: String, sdp: String) =
        send(contactSignalName, callId, CallSignalKind.OFFER, sdp)

    suspend fun sendAnswer(contactSignalName: String, callId: String, sdp: String) =
        send(contactSignalName, callId, CallSignalKind.ANSWER, sdp)

    suspend fun sendIceCandidate(contactSignalName: String, callId: String, candidate: IceCandidateData) =
        send(
            contactSignalName,
            callId,
            CallSignalKind.ICE_CANDIDATE,
            json.encodeToString(IceCandidateData.serializer(), candidate),
        )

    suspend fun sendHangup(contactSignalName: String, callId: String) =
        send(contactSignalName, callId, CallSignalKind.HANGUP, "")

    private suspend fun send(contactSignalName: String, callId: String, kind: CallSignalKind, data: String): Boolean =
        socketManager.send(contactSignalName, WireMessage.CallSignal(callId, kind.name, data))
}
