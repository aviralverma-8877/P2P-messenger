package com.p2pmessenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.p2pmessenger.crypto.SignalSessionManager
import com.p2pmessenger.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val signalSessionManager: SignalSessionManager,
) : ViewModel() {

    val displayName: StateFlow<String> = userPreferences.displayName

    private val _ownFingerprint = MutableStateFlow("")
    val ownFingerprint: StateFlow<String> = _ownFingerprint.asStateFlow()

    init {
        viewModelScope.launch {
            signalSessionManager.ensureIdentity()
            _ownFingerprint.value = signalSessionManager.ownSignalName()
        }
    }

    fun setDisplayName(name: String) {
        if (name.isNotBlank()) userPreferences.setDisplayName(name)
    }
}
