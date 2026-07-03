package com.p2pmessenger.repository

import com.p2pmessenger.data.ContactDao
import com.p2pmessenger.data.ContactEntity
import com.p2pmessenger.network.P2pSocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val socketManager: P2pSocketManager,
) {
    fun observeContacts(): Flow<List<ContactEntity>> = contactDao.observeAll()

    suspend fun getById(id: String): ContactEntity? = contactDao.getById(id)

    suspend fun getBySignalName(signalName: String): ContactEntity? = contactDao.getBySignalName(signalName)

    suspend fun upsert(contact: ContactEntity) = contactDao.upsert(contact)

    suspend fun delete(contact: ContactEntity) {
        socketManager.disconnect(contact.signalName)
        contactDao.delete(contact.id)
    }

    /** Attempts a direct connection using the contact's last-known IPv6 address/port. */
    suspend fun connectTo(contact: ContactEntity): Boolean {
        val ipv6 = contact.lastKnownIpv6 ?: return false
        val port = contact.lastKnownPort ?: return false
        return socketManager.connect(contact.signalName, ipv6, port)
    }

    /** signalName -> currently connected. */
    fun connectionState(): StateFlow<Map<String, Boolean>> = socketManager.connectionState
}
