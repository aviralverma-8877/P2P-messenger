package com.p2pmessenger.ui.addcontact

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2pmessenger.discovery.ble.BleDiscoveredPeer
import kotlinx.coroutines.launch

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
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Nearby") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Share invite") },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                )
            }
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (tab) {
                0 -> BlePairingTab(viewModel)
                1 -> ShareInviteTab(viewModel)
            }
        }
    }
}

@Composable
private fun ShareInviteTab(viewModel: AddContactViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPreparing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Invite a friend who isn't nearby using any app you like -- messaging, email, " +
                "whatever's easiest. It just carries what's needed to set up a secure, direct " +
                "connection between your two phones, no accounts or servers involved.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                isPreparing = true
                scope.launch {
                    val link = viewModel.buildShareLink()
                    isPreparing = false
                    if (link != null) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Let's chat privately on P2P Messenger -- tap to connect: $link",
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share invite"))
                    }
                }
            },
            enabled = !isPreparing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Share invite", modifier = Modifier.padding(start = 8.dp))
            }
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Looking for nearby devices...",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Turn on Bluetooth to find people nearby.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (peers.isEmpty() && active) {
            Text(
                "Ask the other person to open this screen on their phone too.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        headlineContent = { Text("Nearby device", fontWeight = FontWeight.Medium) },
        supportingContent = { Text("Tap to connect") },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
