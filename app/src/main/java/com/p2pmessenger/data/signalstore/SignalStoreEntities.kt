package com.p2pmessenger.data.signalstore

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All private key material below is stored pre-encrypted (AES-256-GCM, key held in the
 * Android Keystore and never exported) by [com.p2pmessenger.crypto.KeystoreCryptoHelper]
 * before it reaches Room -- these entities only ever see ciphertext blobs.
 */
@Entity(tableName = "own_identity")
data class OwnIdentityEntity(
    @PrimaryKey val id: Int = 0,
    val registrationId: Int,
    val encryptedIdentityKeyPair: ByteArray,
) {
    override fun equals(other: Any?) = other is OwnIdentityEntity && id == other.id
    override fun hashCode() = id
}

/** Public identity keys of peers we've paired with -- trust-on-first-use, no encryption needed. */
@Entity(tableName = "remote_identities")
data class RemoteIdentityEntity(
    @PrimaryKey val address: String,
    val identityKeyPublic: ByteArray,
) {
    override fun equals(other: Any?) =
        other is RemoteIdentityEntity && address == other.address && identityKeyPublic.contentEquals(other.identityKeyPublic)
    override fun hashCode() = address.hashCode()
}

@Entity(tableName = "prekeys")
data class PreKeyEntity(
    @PrimaryKey val id: Int,
    val encryptedRecord: ByteArray,
) {
    override fun equals(other: Any?) = other is PreKeyEntity && id == other.id
    override fun hashCode() = id
}

@Entity(tableName = "signed_prekeys")
data class SignedPreKeyEntity(
    @PrimaryKey val id: Int,
    val encryptedRecord: ByteArray,
) {
    override fun equals(other: Any?) = other is SignedPreKeyEntity && id == other.id
    override fun hashCode() = id
}

@Entity(tableName = "kyber_prekeys")
data class KyberPreKeyEntity(
    @PrimaryKey val id: Int,
    val encryptedRecord: ByteArray,
) {
    override fun equals(other: Any?) = other is KyberPreKeyEntity && id == other.id
    override fun hashCode() = id
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val address: String,
    val encryptedRecord: ByteArray,
) {
    override fun equals(other: Any?) = other is SessionEntity && address == other.address
    override fun hashCode() = address.hashCode()
}
