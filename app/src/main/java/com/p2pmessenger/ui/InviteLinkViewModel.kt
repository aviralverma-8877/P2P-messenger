package com.p2pmessenger.ui

import androidx.lifecycle.ViewModel
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.discovery.IncomingPairing
import com.p2pmessenger.discovery.PairingCodec
import com.p2pmessenger.discovery.PairingSource
import com.p2pmessenger.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Handles a tapped invite link (`p2pmessenger://pair?d=...`), however it was shared. */
@HiltViewModel
class InviteLinkViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    suspend fun acceptInviteLink(link: String): ContactEntity? {
        val payload = PairingCodec.decodeFromShareLink(link) ?: return null
        return pairingRepository.acceptPairing(IncomingPairing(payload, PairingSource.LINK, null))
    }
}
