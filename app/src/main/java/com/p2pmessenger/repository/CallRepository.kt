package com.p2pmessenger.repository

import com.p2pmessenger.call.CallSignalingChannel
import com.p2pmessenger.call.IncomingCallSignal
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin façade over [CallSignalingChannel] for the UI layer. Doesn't yet track call state
 * (ringing/connecting/active/ended) -- that lands once [com.p2pmessenger.call.WebRtcClient]'s
 * media attach/offer-answer flow is implemented; [com.p2pmessenger.ui.call.CallScreen] shows a
 * "not implemented yet" placeholder in the meantime.
 */
@Singleton
class CallRepository @Inject constructor(
    private val callSignalingChannel: CallSignalingChannel,
) {
    val incomingCallSignals: SharedFlow<IncomingCallSignal> = callSignalingChannel.incomingSignals
}
