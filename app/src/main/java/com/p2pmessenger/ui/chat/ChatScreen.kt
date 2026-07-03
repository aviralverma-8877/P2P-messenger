package com.p2pmessenger.ui.chat

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.p2pmessenger.data.MessageDirection
import com.p2pmessenger.data.MessageEntity
import com.p2pmessenger.data.MessageStatus
import com.p2pmessenger.data.MessageType

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
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var fileName = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) fileName = cursor.getString(nameIndex) ?: fileName
        }
        viewModel.sendFile(uri, fileName, mimeType)
    }

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
                            if (isConnected) "Connected" else "Connecting...",
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
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear conversation") },
                            onClick = {
                                menuExpanded = false
                                showClearDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete contact") },
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            },
                        )
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
                    IconButton(onClick = { attachmentLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach a photo, video, or file")
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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear conversation?") },
            text = { Text("All messages in this conversation will be deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearConversation()
                    },
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${contact?.displayName ?: "contact"}?") },
            text = { Text("This contact and all messages with them will be deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteContact(onDeleted = onBack)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val context = LocalContext.current
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
                when (message.type) {
                    MessageType.IMAGE -> ImageBubbleContent(message)
                    MessageType.VIDEO, MessageType.FILE -> FileBubbleContent(message, context)
                    else -> Text(message.body ?: "")
                }
                val inProgressLabel = when {
                    message.status == MessageStatus.SENDING && isOutgoing && message.type != MessageType.TEXT -> "Sending..."
                    message.status == MessageStatus.SENDING && !isOutgoing && message.type != MessageType.TEXT -> "Receiving..."
                    else -> null
                }
                inProgressLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

@Composable
private fun ImageBubbleContent(message: MessageEntity) {
    if (message.mediaUri != null) {
        AsyncImage(
            model = Uri.parse(message.mediaUri),
            contentDescription = message.body,
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun FileBubbleContent(message: MessageEntity, context: android.content.Context) {
    val mediaUri = message.mediaUri
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(min = 180.dp)
            .then(
                if (mediaUri != null) {
                    Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(mediaUri), message.mediaMimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            if (message.type == MessageType.VIDEO) Icons.Filled.PlayCircle else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(message.body ?: "File", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
