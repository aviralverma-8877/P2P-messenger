package com.p2pmessenger.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Video calling is scaffolded but not wired up end-to-end yet (see [com.p2pmessenger.call.WebRtcClient]
 * and [com.p2pmessenger.call.CallSignalingChannel] for the pieces already in place: SDP/ICE
 * exchange over the encrypted P2P socket, and a `PeerConnectionFactory` with no ICE servers).
 * This screen is an honest placeholder rather than a fake working call UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(onBack: () -> Unit, viewModel: CallViewModel = hiltViewModel()) {
    val contact by viewModel.contact.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.displayName ?: "Call") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Video calling isn't wired up in this build yet.\n\n" +
                        "The transport is ready (WebRTC with no STUN/TURN, signaling over the " +
                        "same encrypted P2P socket as messages) -- local/remote camera capture " +
                        "and the offer/answer flow are the next things to build.",
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Button(onClick = onBack) { Text("Back to chat") }
        }
    }
}
