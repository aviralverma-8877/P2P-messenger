package com.p2pmessenger.data

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Our own profile display name -- shown to peers during pairing. Nothing sensitive here. */
@Singleton
class UserPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(
        prefs.getString(KEY_DISPLAY_NAME, null) ?: Build.MODEL ?: "Me",
    )
    val displayName: StateFlow<String> = _displayName

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
        _displayName.value = name
    }

    companion object {
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
