package com.p2pmessenger.call

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around WebRTC's media engine (via `stream-webrtc-android`, a maintained
 * prebuilt AAR that keeps the same `org.webrtc.*` API as Google's original library), run in
 * pure P2P mode: `iceServers` is intentionally empty, since this app's whole premise is that
 * the two peers are already directly reachable over IPv6 -- only WebRTC's "host" ICE candidates
 * are relevant here, no STUN/TURN.
 *
 * This sets up a real factory and an empty [PeerConnection] so [CallScreen] has something to
 * bind to, but local media capture, `createOffer`/`createAnswer`, and remote-track rendering
 * are left as TODOs for the next pass (see the plan: video calling is scaffolded, not wired
 * end-to-end, in this first pass).
 */
@Singleton
class WebRtcClient @Inject constructor(@ApplicationContext private val context: Context) {

    val eglBase: EglBase = EglBase.create()

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null

    fun startCall(observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
        return peerConnection

        // TODO next pass:
        //  - capture local audio/video (CameraX or WebRTC's Camera2Capturer) and addTrack()
        //    them onto `peerConnection`
        //  - createOffer()/createAnswer(), setLocalDescription(), push the SDP through
        //    CallSignalingChannel
        //  - on receiving the remote answer/candidates: setRemoteDescription()/addIceCandidate()
        //  - bind local/remote VideoTracks to SurfaceViewRenderers in CallScreen
    }

    fun endCall() {
        peerConnection?.close()
        peerConnection = null
    }
}
