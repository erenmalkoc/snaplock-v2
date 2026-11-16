package com.erenium.snaplock.ui.screens.selectfile

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.RecentDatabase
import com.erenium.snaplock.presentation.selectfile.SelectFileViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppLogo
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun SelectFileScreen(
    onFileSelected: (Uri) -> Unit,
    onCreateDatabase: () -> Unit,
    viewModel: SelectFileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val recentDatabases by viewModel.recentDatabases.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var locating by remember { mutableStateOf<Uri?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onFilePicked(uri)
                onFileSelected(uri)
            }
        }
    )
    val locateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                locating?.let(viewModel::onForget)
                viewModel.onFilePicked(uri)
                onFileSelected(uri)
            }
        }
    )

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val removedMessage = stringResource(R.string.recent_removed)
    val undoLabel = stringResource(R.string.recent_undo)
    LaunchedEffect(viewModel) {
        viewModel.removed.collect {
            val result = snackbarHostState.showSnackbar(
                message = removedMessage,
                actionLabel = undoLabel,
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove() else viewModel.onRemovalDismissed()
        }
    }

    AppScaffold(snackbarHostState = snackbarHostState) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(size = Dimens.spaceXxl * 2)

            Spacer(modifier = Modifier.height(Dimens.spaceLg))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spaceXs))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (recentDatabases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spaceXl))
                RecentDatabasesCard(
                    databases = recentDatabases,
                    onOpen = onFileSelected,
                    onRemove = viewModel::onRemoveRecent,
                    onLocate = { missing ->
                        locating = missing
                        locateLauncher.launch(arrayOf("*/*"))
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(Dimens.spaceMd))
                Text(
                    text = stringResource(R.string.select_file_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXl))
            PrimaryButton(
                icon = Icons.Filled.FolderOpen,
                text = stringResource(R.string.select_file_button),
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(Dimens.spaceSm))
            SecondaryButton(
                icon = Icons.Filled.Add,
                text = stringResource(R.string.create_db_button),
                onClick = onCreateDatabase
            )
        }
    }
}

@Composable
private fun RecentDatabasesCard(
    databases: List<RecentDatabase>,
    onOpen: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
    onLocate: (Uri) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.recent_databases_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Dimens.spaceXs, bottom = Dimens.spaceSm)
        )
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = false) {
            databases.forEachIndexed { index, database ->
                if (index > 0) HorizontalDivider()
                if (database.available) {
                    AvailableRow(database, onOpen, onRemove)
                } else {
                    MissingRow(database, onRemove, onLocate)
                }
            }
        }
    }
}

@Composable
private fun AvailableRow(database: RecentDatabase, onOpen: (Uri) -> Unit, onRemove: (Uri) -> Unit) {
    val details = listOfNotNull(
        database.location,
        database.lastOpened?.let { stringResource(R.string.recent_opened_at, relativeTime(it)) },
        if (database.readOnly) stringResource(R.string.recent_read_only_badge) else null
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable { onOpen(database.uri) },
        leadingContent = { Icon(Icons.Filled.Key, contentDescription = null) },
        headlineContent = { Text(database.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = { onRemove(database.uri) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recent_database_remove))
            }
        }
    )
}

@Composable
private fun MissingRow(database: RecentDatabase, onRemove: (Uri) -> Unit, onLocate: (Uri) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            headlineContent = { Text(database.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.recent_missing),
                    color = MaterialTheme.colorScheme.error
                )
            }
        )
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spaceSm)
        ) {
            AppTextButton(
                icon = Icons.Filled.Delete,
                text = stringResource(R.string.recent_remove),
                onClick = { onRemove(database.uri) }
            )
            AppTextButton(
                icon = Icons.Filled.Search,
                text = stringResource(R.string.recent_locate_again),
                onClick = { onLocate(database.uri) }
            )
        }
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
