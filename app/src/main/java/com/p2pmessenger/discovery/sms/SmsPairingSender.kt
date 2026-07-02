package com.p2pmessenger.discovery.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.p2pmessenger.discovery.PairingCodec
import com.p2pmessenger.discovery.PairingPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends our pairing bundle to a peer who's out of BLE range via a plain SMS. The bundle only
 * contains public key material + our IPv6 address/port, so there's no additional encryption
 * layer needed on top of the carrier's own SMS transport -- this mirrors how Signal's own
 * X3DH prekey bundles are public-by-design.
 */
@Singleton
class SmsPairingSender @Inject constructor(@ApplicationContext private val context: Context) {

    fun send(phoneNumber: String, payload: PairingPayload) {
        val smsManager = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val body = PairingCodec.encodeForSms(payload)
        val parts = smsManager.divideMessage(body)
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
    }
}
