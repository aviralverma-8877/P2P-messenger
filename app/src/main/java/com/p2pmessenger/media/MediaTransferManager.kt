package com.p2pmessenger.media

import android.net.Uri
import com.p2pmessenger.network.P2pSocketManager
import com.p2pmessenger.network.WireMessage
import com.p2pmessenger.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingFile(
    val contactSignalName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/**
 * Scaffolding for photo/video sharing, deliberately left unfinished this pass (see the plan:
 * text messaging is the fully-working path; media transfer/calls are wired-but-stubbed).
 *
 * The transport groundwork is already in place and should be reused rather than rebuilt:
 * - [WireMessage.FileMeta] is the "here's a file coming" announcement, sent the same way
 *   [com.p2pmessenger.repository.MessageRepository] sends [WireMessage.Text].
 * - The actual bytes should follow as a new `FrameKind.FILE_CHUNK` (add it next to
 *   [com.p2pmessenger.network.FrameKind.MESSAGE]) framed by
 *   [com.p2pmessenger.network.MessageFraming], each chunk encrypted the same way
 *   [com.p2pmessenger.network.P2pSocketManager.send] encrypts a [WireMessage] -- don't invent a
 *   second crypto path.
 * - CameraX (already a dependency) feeds captured photos/video into [sendFile]; received files
 *   should land in `MediaStore` via `MediaStore.Images/Video.Media` insert, not app-private
 *   storage, so they show up in the user's gallery like a normal messaging app.
 */
interface MediaTransferManager {
    suspend fun sendFile(contactSignalName: String, uri: Uri, mimeType: String, fileName: String): Boolean
    fun observeIncomingFiles(): SharedFlow<IncomingFile>
}

@Singleton
class MediaTransferManagerImpl @Inject constructor(
    private val socketManager: P2pSocketManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : MediaTransferManager {

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 8)
    override fun observeIncomingFiles(): SharedFlow<IncomingFile> = _incomingFiles.asSharedFlow()

    init {
        appScope.launch {
            socketManager.incomingMessages.collect { (signalName, message) ->
                if (message is WireMessage.FileMeta) {
                    // TODO: once FILE_CHUNK framing exists, start accumulating chunks for this
                    // (signalName, message.id) pair here instead of just announcing metadata.
                    _incomingFiles.emit(
                        IncomingFile(signalName, message.fileName, message.mimeType, message.sizeBytes),
                    )
                }
            }
        }
    }

    override suspend fun sendFile(
        contactSignalName: String,
        uri: Uri,
        mimeType: String,
        fileName: String,
    ): Boolean {
        // TODO: read `uri` in fixed-size chunks, encrypt+frame each one (see class doc above),
        // and send a WireMessage.FileMeta first. Returning false until that's implemented so
        // callers (the chat UI) can show a clear "not implemented yet" state instead of
        // silently pretending to succeed.
        return false
    }
}
