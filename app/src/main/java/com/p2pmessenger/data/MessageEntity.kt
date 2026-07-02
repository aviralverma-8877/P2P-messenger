package com.p2pmessenger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val direction: MessageDirection,
    val type: MessageType,
    val body: String?,
    val mediaUri: String?,
    val mediaMimeType: String?,
    val status: MessageStatus,
    val timestampEpochMs: Long,
)
