package com.p2pmessenger.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A paired peer. [signalName] is the stable identifier used as the `name` half of a
 * [org.signal.libsignal.protocol.SignalProtocolAddress] (we use the identity key fingerprint,
 * not the phone number, so pairing over BLE without a phone number still works).
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val signalName: String,
    val deviceId: Int,
    val phoneNumber: String?,
    val identityKeyPublic: ByteArray,
    val lastKnownIpv6: String?,
    val lastKnownPort: Int?,
    val lastSeenAtEpochMs: Long?,
    val pairedViaBle: Boolean,
    val createdAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactEntity) return false
        return id == other.id &&
            displayName == other.displayName &&
            signalName == other.signalName &&
            deviceId == other.deviceId &&
            phoneNumber == other.phoneNumber &&
            identityKeyPublic.contentEquals(other.identityKeyPublic) &&
            lastKnownIpv6 == other.lastKnownIpv6 &&
            lastKnownPort == other.lastKnownPort
    }

    override fun hashCode(): Int = id.hashCode()
}
