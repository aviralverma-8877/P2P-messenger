package com.p2pmessenger.ui.addcontact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.data.UserPreferences
import com.p2pmessenger.discovery.ble.BleDiscoveredPeer
import com.p2pmessenger.discovery.ble.BlePairingCoordinator
import com.p2pmessenger.discovery.sms.SmsPairingSender
import com.p2pmessenger.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val smsPairingSender: SmsPairingSender,
    private val bleCoordinator: BlePairingCoordinator,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val discoveredPeers: StateFlow<List<BleDiscoveredPeer>> = bleCoordinator.discoveredPeers

    private val _pairedContact = MutableStateFlow<ContactEntity?>(null)
    val pairedContact: StateFlow<ContactEntity?> = _pairedContact.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bleActive = MutableStateFlow(false)
    val bleActive: StateFlow<Boolean> = _bleActive.asStateFlow()

    init {
        viewModelScope.launch {
            pairingRepository.incomingPairings.collect { incoming ->
                _pairedContact.value = pairingRepository.acceptPairing(incoming)
            }
        }
    }

    fun sendViaSms(phoneNumber: String) {
        viewModelScope.launch {
            val payload = pairingRepository.buildOutgoingPayload(userPreferences.displayName.value)
            if (payload == null) {
                _error.value = "No global IPv6 address available -- check your network connection and try again."
                return@launch
            }
            smsPairingSender.send(phoneNumber, payload)
        }
    }

    fun startBlePairing() {
        viewModelScope.launch {
            val payload = pairingRepository.buildOutgoingPayload(userPreferences.displayName.value)
            if (payload == null) {
                _error.value = "No global IPv6 address available -- check your network connection and try again."
                return@launch
            }
            bleCoordinator.start(payload)
            _bleActive.value = true
        }
    }

    fun stopBlePairing() {
        bleCoordinator.stop()
        _bleActive.value = false
    }

    fun pairWithBlePeer(peer: BleDiscoveredPeer) {
        bleCoordinator.pairWith(peer)
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        bleCoordinator.stop()
        super.onCleared()
    }
}
