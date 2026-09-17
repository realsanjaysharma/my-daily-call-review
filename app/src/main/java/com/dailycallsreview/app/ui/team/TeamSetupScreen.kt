package com.dailycallsreview.app.ui.team

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dailycallsreview.app.DailyCallsReviewApplication

@Composable
fun TeamSetupScreen(app: DailyCallsReviewApplication) {
    val viewModel: TeamSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TeamSetupViewModel(app.contactsRepository, app.teamRepository) }
        }
    )
    val contacts by viewModel.allContacts.collectAsState()
    val taggedIds by viewModel.taggedContactIds.collectAsState()

    LazyColumn {
        items(contacts) { contact ->
            val isTagged = contact.contactId in taggedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isTagged,
                    onCheckedChange = { viewModel.toggleTag(contact, isTagged) }
                )
                Text(contact.displayName)
            }
        }
    }
}
