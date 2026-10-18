package com.erenium.snaplock.ui.screens.autofill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.erenium.snaplock.R
import com.erenium.snaplock.autofill.AutofillViewModel
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.components.EntryAvatar
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen
import com.erenium.snaplock.ui.theme.Dimens

/**
 * Shown when the user taps a Snaplock suggestion that needs the app: unlocks the most
 * recent database if needed, then lets the user pick the entry to fill.
 */
@Composable
fun AutofillScreen(
    viewModel: AutofillViewModel,
    target: String,
    onPicked: (Credential) -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.picked.collect(onPicked)
    }

    val database = state.database
    when {
        state.isLoading -> LoadingSpinner()
        state.isLocked && database == null -> NoDatabase(onOpenApp = onOpenApp, onCancel = onCancel)
        state.isLocked && database != null -> UnlockScreen(
            uri = database.uri,
            onUnlockSuccess = {},
            onNavigateBack = onCancel
        )
        else -> AppScaffold(
            title = stringResource(R.string.autofill_pick_title),
            onNavigateBack = onCancel,
            applyContentPadding = false
        ) { contentModifier ->
            Column(modifier = contentModifier.fillMaxSize()) {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm)
                )
                if (state.others.isNotEmpty()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.autofill_link_picked, target)) },
                        trailingContent = {
                            Switch(checked = state.linkPicked, onCheckedChange = viewModel::onLinkPickedChange)
                        },
                        modifier = Modifier.clickable { viewModel.onLinkPickedChange(!state.linkPicked) }
                    )
                }
                if (state.matches.isEmpty() && state.others.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        message = stringResource(R.string.entry_search_no_results)
                    )
                } else {
                    val matchesHeader = stringResource(R.string.autofill_matches_header, target)
                    val allHeader = stringResource(R.string.autofill_all_header)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Dimens.spaceLg)
                    ) {
                        section(
                            header = matchesHeader,
                            credentials = state.matches,
                            onPick = viewModel::onPick
                        )
                        section(
                            header = allHeader,
                            credentials = state.others,
                            onPick = viewModel::onPick
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    header: String,
    credentials: List<Credential>,
    onPick: (Credential) -> Unit
) {
    if (credentials.isEmpty()) return
    item(key = "header-$header") {
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                top = Dimens.spaceMd,
                bottom = Dimens.spaceXs
            )
        )
    }
    items(items = credentials, key = { it.uuid }) { credential ->
        ListItem(
            modifier = Modifier.clickable { onPick(credential) },
            leadingContent = { EntryAvatar(title = credential.title, icon = credential.icon) },
            headlineContent = {
                Text(credential.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = credential.username?.let { username ->
                { Text(username, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        )
    }
}

@Composable
private fun SearchField(
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
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.entry_search_clear))
                }
            }
        }
    )
}

@Composable
internal fun NoDatabase(onOpenApp: () -> Unit, onCancel: () -> Unit) {
    AppScaffold(onNavigateBack = onCancel) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.autofill_no_database),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceLg))
            PrimaryButton(text = stringResource(R.string.autofill_open_app), onClick = onOpenApp)
        }
    }
}
