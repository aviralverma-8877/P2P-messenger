package com.p2pmessenger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.network.Ipv6Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddContact: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ipv6Status by viewModel.ipv6Status.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P2P Messenger") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Ipv6StatusBanner(ipv6Status)
            Divider()
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts yet -- tap + to pair with someone nearby (BLE) or far away (SMS).")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isConnected = connectionState[contact.signalName] == true,
                            onClick = { onOpenChat(contact.id) },
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Ipv6StatusBanner(status: Ipv6Status) {
    val (color, text) = when (status) {
        is Ipv6Status.Checking -> MaterialTheme.colorScheme.surfaceVariant to "Checking IPv6 support..."
        is Ipv6Status.Available -> Color(0xFF1E8E3E) to "IPv6 ready: ${status.address}"
        is Ipv6Status.Unavailable -> Color(0xFFD93025) to
            "No global IPv6 address on this network -- direct connections won't work until you're on a network that supports it."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, isConnected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        supportingContent = { Text(if (isConnected) "Connected" else "Offline") },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) Color(0xFF1E8E3E) else Color.Gray),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
