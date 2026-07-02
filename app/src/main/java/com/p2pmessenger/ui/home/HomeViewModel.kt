package com.p2pmessenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pmessenger.crypto.SignalSessionManager
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.network.Ipv6Status
import com.p2pmessenger.network.Ipv6Utils
import com.p2pmessenger.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    ipv6Utils: Ipv6Utils,
    private val signalSessionManager: SignalSessionManager,
) : ViewModel() {

    val ipv6Status: StateFlow<Ipv6Status> = ipv6Utils.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ipv6Status.Checking)

    val contacts: StateFlow<List<ContactEntity>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionState: StateFlow<Map<String, Boolean>> = contactRepository.connectionState()

    init {
        viewModelScope.launch { signalSessionManager.ensureIdentity() }
    }
}
