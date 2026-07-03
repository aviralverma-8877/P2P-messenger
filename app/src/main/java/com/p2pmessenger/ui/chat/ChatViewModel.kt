package com.p2pmessenger.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.data.MessageEntity
import com.p2pmessenger.repository.ContactRepository
import com.p2pmessenger.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _contact = MutableStateFlow<ContactEntity?>(null)
    val contact: StateFlow<ContactEntity?> = _contact.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = messageRepository.observeMessages(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val connectionState: StateFlow<Map<String, Boolean>> = contactRepository.connectionState()

    init {
        viewModelScope.launch {
            val loaded = contactRepository.getById(contactId)
            _contact.value = loaded
            if (loaded != null) contactRepository.connectTo(loaded)
        }
    }

    fun sendText(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _contact.value?.let { messageRepository.sendText(it, body) }
        }
    }

    fun sendFile(uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _contact.value?.let { messageRepository.sendFile(it, uri, fileName, mimeType) }
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            _contact.value?.let { contactRepository.connectTo(it) }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            messageRepository.clearConversation(contactId)
        }
    }

    fun deleteContact(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val contact = _contact.value ?: return@launch
            messageRepository.clearConversation(contactId)
            contactRepository.delete(contact)
            onDeleted()
        }
    }
}
