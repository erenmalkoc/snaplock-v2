package com.erenium.snaplock.ui.screens.entrylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.components.EntryAvatar
import com.erenium.snaplock.ui.theme.Dimens
import java.util.UUID

@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit,
    onEntryClick: (UUID) -> Unit,
    viewModel: EntryListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
        if (state.isEmpty && !state.isSearching) {
            EmptyState(
                modifier = contentModifier,
                message = stringResource(R.string.entry_list_empty)
            )
            return@AppScaffold
        }

        Column(modifier = contentModifier.fillMaxSize()) {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceSm
                    )
            )

            if (state.isEmpty) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(R.string.entry_search_no_results)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceSm
                    )
                ) {
                    items(
                        items = state.entries,
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
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.entry_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.entry_search_clear)
                    )
                }
            }
        }
    )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntryAvatar(title = entry.title)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.spaceMd)
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
                        modifier = Modifier.padding(top = Dimens.spaceXxs)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconMd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
