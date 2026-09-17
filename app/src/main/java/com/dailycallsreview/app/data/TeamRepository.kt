package com.dailycallsreview.app.data

import com.dailycallsreview.app.data.contacts.PickableContact
import com.dailycallsreview.app.data.db.TaggedContactDao
import com.dailycallsreview.app.data.db.TaggedContactEntity
import kotlinx.coroutines.flow.Flow

class TeamRepository(private val dao: TaggedContactDao) {

    fun observeTaggedContacts(): Flow<List<TaggedContactEntity>> = dao.observeAll()

    suspend fun tagContact(contact: PickableContact) {
        dao.insertAll(
            contact.phoneNumbersLast10.map { number ->
                TaggedContactEntity(
                    phoneNumberLast10 = number,
                    contactId = contact.contactId,
                    displayName = contact.displayName
                )
            }
        )
    }

    suspend fun untagContact(contactId: Long) {
        dao.deleteByContactId(contactId)
    }

    suspend fun getTaggedNumbersOnce(): Set<String> =
        dao.getAllOnce().map { it.phoneNumberLast10 }.toSet()
}
