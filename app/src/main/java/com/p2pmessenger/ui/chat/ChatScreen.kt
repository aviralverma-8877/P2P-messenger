package com.p2pmessenger.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.p2pmessenger.data.MessageDirection
import com.p2pmessenger.data.MessageEntity
import com.p2pmessenger.data.MessageStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onStartCall: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val contact by viewModel.contact.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = contact?.let { connectionState[it.signalName] == true } ?: false
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(contact?.displayName ?: "Chat")
                        Text(
                            if (isConnected) "Connected" else "Offline -- will retry",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { contact?.let { onStartCall(it.id) } }) {
                        Icon(Icons.Filled.Call, contentDescription = "Video call")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { /* Media sharing isn't wired up yet -- see MediaTransferManager TODOs */ }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach media (coming soon)")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendText(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(message.body ?: "")
                if (isOutgoing && message.status == MessageStatus.FAILED) {
                    Text(
                        "Failed to send",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
