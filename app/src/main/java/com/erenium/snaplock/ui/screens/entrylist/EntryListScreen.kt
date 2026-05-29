package com.erenium.snaplock.ui.screens.entrylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.theme.Dimens
import java.util.UUID

@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit,
    onEntryClick: (UUID) -> Unit,
    viewModel: EntryListViewModel = hiltViewModel()
) {
    val entries by viewModel.uiState.collectAsState()

    AppScaffold(
        title = stringResource(R.string.entry_list_title),
        applyContentPadding = false,
        actions = {
            IconButton(onClick = onNavigateToLock) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.entry_list_lock_button)
                )
            }
        }
    ) { contentModifier ->
        if (entries.isEmpty()) {
            EmptyState(
                modifier = contentModifier,
                message = stringResource(R.string.entry_list_empty)
            )
        } else {
            LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.spaceSm
                )
            ) {
                items(
                    items = entries,
                    key = { it.uuid }
                ) { entry ->
                    EntryListItem(
                        entry = entry,
                        onClick = { onEntryClick(entry.uuid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryListItem(
    entry: Entry,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXs),
        contentPadding = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimens.spaceMd)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium
            )
            if (!entry.username.isNullOrBlank()) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spaceXs)
                )
            }
        }
    }
}
