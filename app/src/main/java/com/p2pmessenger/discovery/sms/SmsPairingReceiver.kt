package com.p2pmessenger.discovery.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.p2pmessenger.discovery.IncomingPairing
import com.p2pmessenger.discovery.PairingCodec
import com.p2pmessenger.discovery.PairingSource
import com.p2pmessenger.repository.PairingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for incoming SMS and only acts on ones carrying our [PairingCodec.SMS_MARKER] prefix
 * -- everything else is left alone for the user's normal SMS app to handle.
 */
@AndroidEntryPoint
class SmsPairingReceiver : BroadcastReceiver() {

    @Inject
    lateinit var pairingRepository: PairingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val payload = PairingCodec.decodeFromSms(body) ?: return

        scope.launch {
            pairingRepository.onIncomingPairing(IncomingPairing(payload, PairingSource.SMS, sender))
        }
    }
}
