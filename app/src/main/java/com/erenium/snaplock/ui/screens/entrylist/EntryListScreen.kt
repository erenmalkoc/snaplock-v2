package com.erenium.snaplock.ui.screens.entrylist // Kendi paket adınız

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import java.util.UUID

@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit,
    onEntryClick: (UUID) -> Unit,
    viewModel: EntryListViewModel = hiltViewModel()
) {
    val entries by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onNavigateToLock,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
          Text(text = stringResource(id = R.string.entry_list_lock_button))
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = entries,
                key = { it.uuid }
            ) { entry ->
                EntryListItem(
                    entry = entry,
                    onClick = {
                        onEntryClick(entry.uuid)
                    }
                )
            }
        }
    }
}

@Composable
private fun EntryListItem(
    entry: Entry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium
            )
            if (!entry.username.isNullOrBlank()) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}