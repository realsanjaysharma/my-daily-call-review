package com.dailycallsreview.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailycallsreview.app.data.TeamRepository
import com.dailycallsreview.app.data.contacts.ContactsRepository
import com.dailycallsreview.app.data.contacts.PickableContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamSetupViewModel(
    private val contactsRepository: ContactsRepository,
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<PickableContact>>(emptyList())
    val allContacts: StateFlow<List<PickableContact>> = _allContacts.asStateFlow()

    val taggedContactIds: StateFlow<Set<Long>> = teamRepository.observeTaggedContacts()
        .map { entities -> entities.map { it.contactId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            _allContacts.value = contactsRepository.getAllContactsWithPhoneNumbers()
        }
    }

    fun toggleTag(contact: PickableContact, currentlyTagged: Boolean) {
        viewModelScope.launch {
            if (currentlyTagged) {
                teamRepository.untagContact(contact.contactId)
            } else {
                teamRepository.tagContact(contact)
            }
        }
    }
}
