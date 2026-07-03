package com.p2pmessenger.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Writes a fully-received file into the platform's shared media collections (gallery/downloads)
 * instead of app-private storage, so a received photo/video shows up in the recipient's own
 * gallery app like it would from any normal messenger.
 */
object MediaStoreSaver {

    fun save(context: Context, sourceFile: File, fileName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val (collection, relativeDir) = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            else -> {
                val downloadsCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                downloadsCollection to Environment.DIRECTORY_DOWNLOADS
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/P2P Messenger")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(relativeDir)
                if (!dir.exists()) dir.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(dir, fileName).absolutePath)
            }
        }

        val itemUri = resolver.insert(collection, values) ?: error("MediaStore insert failed for $fileName")
        resolver.openOutputStream(itemUri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pendingDone = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, pendingDone, null, null)
        }
        return itemUri
    }
}
