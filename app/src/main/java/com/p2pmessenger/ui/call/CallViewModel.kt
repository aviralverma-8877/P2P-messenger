package com.p2pmessenger.ui.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _contact = MutableStateFlow<ContactEntity?>(null)
    val contact: StateFlow<ContactEntity?> = _contact.asStateFlow()

    init {
        viewModelScope.launch { _contact.value = contactRepository.getById(contactId) }
    }
}
