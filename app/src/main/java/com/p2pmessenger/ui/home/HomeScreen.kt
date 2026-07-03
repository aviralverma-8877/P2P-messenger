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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.network.Ipv6Status
import com.p2pmessenger.ui.theme.ColorScheme_Danger
import com.p2pmessenger.ui.theme.ColorScheme_Success

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
                title = { Text("Messages", fontWeight = FontWeight.SemiBold) },
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
            ConnectivityBanner(ipv6Status)
            if (contacts.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isConnected = connectionState[contact.signalName] == true,
                            onClick = { onOpenChat(contact.id) },
                        )
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectivityBanner(status: Ipv6Status) {
    val (icon, color, text) = when (status) {
        is Ipv6Status.Checking -> Triple(
            Icons.Default.Wifi,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Checking your connection...",
        )
        is Ipv6Status.Available -> Triple(
            Icons.Default.Wifi,
            ColorScheme_Success,
            "You're ready to connect",
        )
        is Ipv6Status.Unavailable -> Triple(
            Icons.Default.SyncProblem,
            ColorScheme_Danger,
            "Direct connections aren't available on this network",
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                "No conversations yet",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tap + to add someone nearby or invite a friend by text message.",
                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, isConnected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(contact.displayName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(if (isConnected) "Connected" else "Not connected") },
        leadingContent = { ContactAvatar(contact.displayName, isConnected) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ContactAvatar(displayName: String, isConnected: Boolean) {
    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(1.5.dp)
                .background(if (isConnected) ColorScheme_Success else Color.Gray, CircleShape),
        )
    }
}
