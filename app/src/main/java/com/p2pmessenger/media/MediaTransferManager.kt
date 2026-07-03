package com.p2pmessenger.media

import android.content.Context
import android.net.Uri
import com.p2pmessenger.data.ContactDao
import com.p2pmessenger.data.MessageDao
import com.p2pmessenger.data.MessageDirection
import com.p2pmessenger.data.MessageEntity
import com.p2pmessenger.data.MessageStatus
import com.p2pmessenger.data.messageTypeForMimeType
import com.p2pmessenger.di.ApplicationScope
import com.p2pmessenger.network.FileChunkFraming
import com.p2pmessenger.network.P2pSocketManager
import com.p2pmessenger.network.WireMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingFile(
    val contactSignalName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/**
 * Handles chunked file/photo/video transfer over the same encrypted P2P socket messages use.
 * [WireMessage.FileMeta] announces an incoming file (and creates its placeholder chat row);
 * the bytes themselves follow as a sequence of [com.p2pmessenger.network.FrameKind.FILE_CHUNK]
 * frames (see [P2pSocketManager.sendFileChunk]/[P2pSocketManager.incomingFileChunks]), each
 * chunk independently Signal-encrypted like any other message.
 *
 * Received files are written to the platform's MediaStore (via [MediaStoreSaver]), not
 * app-private storage, so they show up in the recipient's gallery/downloads like a normal
 * messaging app.
 */
interface MediaTransferManager {
    suspend fun sendFile(contactSignalName: String, uri: Uri, mimeType: String, fileName: String): Boolean
    fun observeIncomingFiles(): SharedFlow<IncomingFile>
}

@Singleton
class MediaTransferManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val socketManager: P2pSocketManager,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    @ApplicationScope private val appScope: CoroutineScope,
) : MediaTransferManager {

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 8)
    override fun observeIncomingFiles(): SharedFlow<IncomingFile> = _incomingFiles.asSharedFlow()

    /** fileId -> in-progress reassembly state. Only ever touched from the single chunk-collector below. */
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()

    private class IncomingTransfer(
        val signalName: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val tempFile: File,
    ) {
        var chunksReceived: Int = 0
    }

    init {
        appScope.launch {
            socketManager.incomingMessages.collect { (signalName, message) ->
                if (message is WireMessage.FileMeta) {
                    val contact = contactDao.getBySignalName(signalName) ?: return@collect
                    withContext(Dispatchers.IO) {
                        val tempFile = File(context.cacheDir, "incoming_${message.id}")
                        tempFile.outputStream().close() // truncate/create empty
                        incomingTransfers[message.id] = IncomingTransfer(
                            signalName = signalName,
                            fileName = message.fileName,
                            mimeType = message.mimeType,
                            sizeBytes = message.sizeBytes,
                            tempFile = tempFile,
                        )
                        messageDao.upsert(
                            MessageEntity(
                                id = message.id,
                                contactId = contact.id,
                                direction = MessageDirection.INCOMING,
                                type = messageTypeForMimeType(message.mimeType),
                                body = message.fileName,
                                mediaUri = null,
                                mediaMimeType = message.mimeType,
                                status = MessageStatus.SENDING,
                                timestampEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
        appScope.launch {
            socketManager.incomingFileChunks.collect { (_, chunk) ->
                val transfer = incomingTransfers[chunk.fileId] ?: return@collect
                withContext(Dispatchers.IO) {
                    transfer.tempFile.appendBytes(chunk.data)
                    transfer.chunksReceived++
                    if (transfer.chunksReceived >= chunk.totalChunks) {
                        incomingTransfers.remove(chunk.fileId)
                        val mediaUri = MediaStoreSaver.save(context, transfer.tempFile, transfer.fileName, transfer.mimeType)
                        transfer.tempFile.delete()
                        messageDao.updateMedia(chunk.fileId, mediaUri.toString(), MessageStatus.RECEIVED)
                        _incomingFiles.emit(IncomingFile(transfer.signalName, transfer.fileName, transfer.mimeType, transfer.sizeBytes))
                    }
                }
            }
        }
    }

    override suspend fun sendFile(
        contactSignalName: String,
        uri: Uri,
        mimeType: String,
        fileName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "outgoing_${UUID.randomUUID()}")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) return@withContext false

            val sizeBytes = tempFile.length()
            val fileId = UUID.randomUUID().toString()
            val metaSent = socketManager.send(contactSignalName, WireMessage.FileMeta(fileId, fileName, mimeType, sizeBytes))
            if (!metaSent) return@withContext false

            val chunkSize = FileChunkFraming.CHUNK_SIZE
            val totalChunks = ((sizeBytes + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
            tempFile.inputStream().use { input ->
                val buffer = ByteArray(chunkSize)
                var index = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val sent = socketManager.sendFileChunk(contactSignalName, fileId, index, totalChunks, buffer.copyOf(read))
                    if (!sent) return@withContext false
                    index++
                }
            }
            true
        } finally {
            tempFile.delete()
        }
    }
}
