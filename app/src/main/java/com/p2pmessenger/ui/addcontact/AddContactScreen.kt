package com.p2pmessenger.ui.addcontact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2pmessenger.discovery.ble.BleDiscoveredPeer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onPaired: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddContactViewModel = hiltViewModel(),
) {
    var tab by remember { mutableStateOf(0) }
    val error by viewModel.error.collectAsState()
    val pairedContact by viewModel.pairedContact.collectAsState()

    LaunchedEffect(pairedContact) {
        pairedContact?.let { onPaired(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add contact") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("SMS (far away)") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bluetooth (nearby)") })
            }
            error?.let {
                Text(it, modifier = Modifier.padding(16.dp), color = androidx.compose.ui.graphics.Color(0xFFD93025))
            }
            when (tab) {
                0 -> SmsPairingTab(viewModel)
                1 -> BlePairingTab(viewModel)
            }
        }
    }
}

@Composable
private fun SmsPairingTab(viewModel: AddContactViewModel) {
    var phoneNumber by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("We'll text your public pairing details (no private keys) to this number.")
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Their phone number") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.sendViaSms(phoneNumber) }, enabled = phoneNumber.isNotBlank()) {
            Text("Send my details via SMS")
        }
        Text("Waiting for them to reply with their own details...")
    }
}

@Composable
private fun BlePairingTab(viewModel: AddContactViewModel) {
    val peers by viewModel.discoveredPeers.collectAsState()
    val active by viewModel.bleActive.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startBlePairing()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.stopBlePairing() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (active) "Broadcasting and scanning for nearby devices..." else "Bluetooth pairing inactive")
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(peers, key = { it.device.address }) { peer ->
                BlePeerRow(peer) { viewModel.pairWithBlePeer(peer) }
            }
        }
    }
}

@Composable
private fun BlePeerRow(peer: BleDiscoveredPeer, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(peer.device.address) },
        supportingContent = { Text("Signal strength: ${peer.rssi} dBm -- tap to pair") },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
