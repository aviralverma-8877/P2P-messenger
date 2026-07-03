package com.p2pmessenger.data

import androidx.room.TypeConverter

enum class MessageDirection { INCOMING, OUTGOING }

enum class MessageType { TEXT, IMAGE, VIDEO, FILE, CALL_EVENT }

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED, RECEIVED }

/** [MessageStatus.SENDING] doubles as "transfer in progress" for both directions -- callers tell
 *  "Sending..." from "Receiving..." using [MessageEntity.direction], not a separate status. */
fun messageTypeForMimeType(mimeType: String): MessageType = when {
    mimeType.startsWith("image/") -> MessageType.IMAGE
    mimeType.startsWith("video/") -> MessageType.VIDEO
    else -> MessageType.FILE
}

class Converters {
    @TypeConverter
    fun directionToString(value: MessageDirection): String = value.name

    @TypeConverter
    fun stringToDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun typeToString(value: MessageType): String = value.name

    @TypeConverter
    fun stringToType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun statusToString(value: MessageStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
