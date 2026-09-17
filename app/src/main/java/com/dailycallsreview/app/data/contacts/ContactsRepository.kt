package com.dailycallsreview.app.data.contacts

import android.content.Context
import android.provider.ContactsContract
import com.dailycallsreview.app.core.PhoneNumberNormalizer

data class PickableContact(
    val contactId: Long,
    val displayName: String,
    val phoneNumbersLast10: List<String>
)

class ContactsRepository(private val context: Context) {

    fun getAllContactsWithPhoneNumbers(): List<PickableContact> {
        val numbersByContact = mutableMapOf<Long, MutableList<String>>()
        val namesByContact = mutableMapOf<Long, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val rawNumber = cursor.getString(numberIndex) ?: continue
                val normalized = PhoneNumberNormalizer.last10Digits(rawNumber)

                namesByContact[contactId] = name
                numbersByContact.getOrPut(contactId) { mutableListOf() }.add(normalized)
            }
        }

        return numbersByContact.map { (contactId, numbers) ->
            PickableContact(
                contactId = contactId,
                displayName = namesByContact[contactId].orEmpty(),
                phoneNumbersLast10 = numbers.distinct()
            )
        }.sortedBy { it.displayName }
    }
}
