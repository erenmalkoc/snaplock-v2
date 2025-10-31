package com.erenium.snaplock.ui.screens.entrylist

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel

@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit,
    viewModel: EntryListViewModel = hiltViewModel()
) {
    val entries by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onNavigateToLock) {
            Text("Manuel Kilitle")
        }
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(entries, key = { it.uuid }) { entry ->
                EntryListItem(entry = entry)
            }
        }
    }
}


@Composable
private fun EntryListItem(entry: Entry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium
            )
            if (entry.username != null) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}