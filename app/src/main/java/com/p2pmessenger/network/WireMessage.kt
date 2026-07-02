package com.p2pmessenger.network

import kotlinx.serialization.Serializable

/**
 * Application-level messages carried inside each Signal-encrypted frame. [CallSignal] is how
 * [com.p2pmessenger.call.CallSignalingChannel] pushes WebRTC SDP offers/answers/ICE candidates
 * over this same encrypted channel instead of a separate signaling server. [FileMeta] marks
 * where chunked media transfer (see [com.p2pmessenger.media.MediaTransferManager]) will hang
 * its handshake in the next pass -- not fully wired up yet.
 */
@Serializable
sealed interface WireMessage {
    @Serializable
    data class Text(val id: String, val body: String, val timestampEpochMs: Long) : WireMessage

    @Serializable
    data class CallSignal(val callId: String, val kind: String, val data: String) : WireMessage

    @Serializable
    data class FileMeta(
        val id: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : WireMessage
}
