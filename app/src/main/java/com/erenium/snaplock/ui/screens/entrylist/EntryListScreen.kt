package com.erenium.snaplock.ui.screens.entrylist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.components.EntryAvatar
import com.erenium.snaplock.ui.theme.Dimens
import java.util.UUID

private enum class GroupDialog { CREATE, RENAME, DELETE }

@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onAddEntry: (groupUuid: UUID?) -> Unit,
    onEntryClick: (UUID) -> Unit,
    focusSearch: Boolean = false,
    onSearchFocused: () -> Unit = {},
    viewModel: EntryListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
            onSearchFocused()
        }
    }
    var dialog by remember { mutableStateOf<GroupDialog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.errorStringId?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }

    BackHandler(enabled = !state.isRoot && !state.isSearching) {
        viewModel.onNavigateUp()
    }

    AppScaffold(
        title = if (state.isRoot) {
            stringResource(R.string.entry_list_title)
        } else {
            state.currentGroup?.name.orEmpty()
        },
        onNavigateBack = if (state.isRoot) null else ({ viewModel.onNavigateUp() }),
        applyContentPadding = false,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddEntry(state.currentGroup?.uuid) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.entry_form_add_title)
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToGenerator) {
                Icon(
                    imageVector = Icons.Filled.Password,
                    contentDescription = stringResource(R.string.pwgen_title)
                )
            }
            IconButton(onClick = onNavigateToLock) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.entry_list_lock_button)
                )
            }
            OverflowMenu(
                isRoot = state.isRoot,
                onCreateGroup = { dialog = GroupDialog.CREATE },
                onRenameGroup = { dialog = GroupDialog.RENAME },
                onDeleteGroup = { dialog = GroupDialog.DELETE },
                onNavigateToHealth = onNavigateToHealth,
                onNavigateToRecycleBin = onNavigateToRecycleBin,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { contentModifier ->
        Column(modifier = contentModifier.fillMaxSize()) {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                focusRequester = searchFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceSm
                    )
            )

            if (state.isEmpty && !state.isLoading) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(
                        when {
                            state.isSearching -> R.string.entry_search_no_results
                            state.isRoot -> R.string.entry_list_empty
                            else -> R.string.group_empty
                        }
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceSm
                    )
                ) {
                    if (state.subgroups.isNotEmpty()) {
                        item(key = "groups") {
                            AppCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Dimens.spaceXs),
                                contentPadding = false
                            ) {
                                state.subgroups.forEachIndexed { index, group ->
                                    if (index > 0) HorizontalDivider()
                                    GroupListItem(group = group, onClick = { viewModel.onOpenGroup(group.uuid) })
                                }
                            }
                        }
                    }
                    items(
                        items = state.entries,
                        key = { it.uuid }
                    ) { entry ->
                        EntryListItem(
                            entry = entry,
                            showGroup = state.isSearching,
                            onClick = { onEntryClick(entry.uuid) }
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        GroupDialog.CREATE -> NameDialog(
            title = stringResource(R.string.group_create),
            initialName = "",
            onConfirm = { name ->
                dialog = null
                viewModel.onCreateGroup(name)
            },
            onDismiss = { dialog = null }
        )
        GroupDialog.RENAME -> NameDialog(
            title = stringResource(R.string.group_rename),
            initialName = state.currentGroup?.name.orEmpty(),
            onConfirm = { name ->
                dialog = null
                viewModel.onRenameCurrentGroup(name)
            },
            onDismiss = { dialog = null }
        )
        GroupDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(state.currentGroup?.name.orEmpty()) },
            text = { Text(stringResource(R.string.group_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    viewModel.onDeleteCurrentGroup()
                }) { Text(stringResource(R.string.entry_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
        null -> Unit
    }
}

@Composable
private fun OverflowMenu(
    isRoot: Boolean,
    onCreateGroup: () -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    fun close(action: () -> Unit) = {
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_create)) },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                onClick = close(onCreateGroup)
            )
            if (!isRoot) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_rename)) },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    onClick = close(onRenameGroup)
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_delete)) },
                    leadingIcon = { Icon(Icons.Filled.FolderDelete, contentDescription = null) },
                    onClick = close(onDeleteGroup)
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.health_title)) },
                leadingIcon = { Icon(Icons.Filled.HealthAndSafety, contentDescription = null) },
                onClick = close(onNavigateToHealth)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recycle_bin_title)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = close(onNavigateToRecycleBin)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = close(onNavigateToSettings)
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.entry_form_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } }
    )
}

@Composable
private fun GroupListItem(group: Group, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
        headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.group_entry_count, group.entryCount, group.entryCount))
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    )
}

/** The stock Material 3 search field; results filter the list below as the user types. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { focusManager.clearFocus() },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
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
}

@Composable
private fun EntryListItem(
    entry: Entry,
    showGroup: Boolean,
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
            EntryAvatar(title = entry.title, icon = entry.icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.spaceMd)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium
                )
                val subtitle = listOfNotNull(
                    entry.username?.takeIf { it.isNotBlank() },
                    entry.groupName.takeIf { showGroup }
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
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
