package com.p2pmessenger.repository

import android.net.Uri
import com.p2pmessenger.data.ContactDao
import com.p2pmessenger.data.MessageDao
import com.p2pmessenger.data.MessageDirection
import com.p2pmessenger.data.MessageEntity
import com.p2pmessenger.data.MessageStatus
import com.p2pmessenger.data.MessageType
import com.p2pmessenger.data.messageTypeForMimeType
import com.p2pmessenger.di.ApplicationScope
import com.p2pmessenger.media.MediaTransferManager
import com.p2pmessenger.network.P2pSocketManager
import com.p2pmessenger.network.WireMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val socketManager: P2pSocketManager,
    private val mediaTransferManager: MediaTransferManager,
    @ApplicationScope appScope: CoroutineScope,
) {
    init {
        appScope.launch {
            socketManager.incomingMessages.collect { (signalName, message) ->
                if (message is WireMessage.Text) {
                    val contact = contactDao.getBySignalName(signalName) ?: return@collect
                    messageDao.upsert(
                        MessageEntity(
                            id = message.id,
                            contactId = contact.id,
                            direction = MessageDirection.INCOMING,
                            type = MessageType.TEXT,
                            body = message.body,
                            mediaUri = null,
                            mediaMimeType = null,
                            status = MessageStatus.RECEIVED,
                            timestampEpochMs = message.timestampEpochMs,
                        ),
                    )
                }
                // WireMessage.CallSignal is consumed by CallSignalingChannel's own collector,
                // and WireMessage.FileMeta handling is a TODO for the media-transfer pass.
            }
        }
    }

    fun observeMessages(contactId: String): Flow<List<MessageEntity>> = messageDao.observeForContact(contactId)

    suspend fun clearConversation(contactId: String) = messageDao.deleteForContact(contactId)

    suspend fun sendText(contact: com.p2pmessenger.data.ContactEntity, body: String): Boolean {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        messageDao.upsert(
            MessageEntity(
                id = id,
                contactId = contact.id,
                direction = MessageDirection.OUTGOING,
                type = MessageType.TEXT,
                body = body,
                mediaUri = null,
                mediaMimeType = null,
                status = MessageStatus.SENDING,
                timestampEpochMs = timestamp,
            ),
        )
        val sent = socketManager.send(contact.signalName, WireMessage.Text(id, body, timestamp))
        messageDao.updateStatus(id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
        return sent
    }

    suspend fun sendFile(
        contact: com.p2pmessenger.data.ContactEntity,
        uri: Uri,
        fileName: String,
        mimeType: String,
    ): Boolean {
        val id = UUID.randomUUID().toString()
        messageDao.upsert(
            MessageEntity(
                id = id,
                contactId = contact.id,
                direction = MessageDirection.OUTGOING,
                type = messageTypeForMimeType(mimeType),
                body = fileName,
                mediaUri = uri.toString(),
                mediaMimeType = mimeType,
                status = MessageStatus.SENDING,
                timestampEpochMs = System.currentTimeMillis(),
            ),
        )
        val sent = mediaTransferManager.sendFile(contact.signalName, uri, mimeType, fileName)
        messageDao.updateStatus(id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
        return sent
    }
}
