package com.erenium.snaplock.ui.screens.selectfile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.RecentDatabase
import com.erenium.snaplock.presentation.selectfile.SelectFileViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppLogo
import com.erenium.snaplock.ui.components.AppScaffold
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
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                onFileSelected(uri)
            }
        }
    )

    AppScaffold { contentModifier ->
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
                    onRemove = viewModel::onRemoveRecent
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
                text = stringResource(R.string.select_file_button),
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
            )
            Spacer(modifier = Modifier.height(Dimens.spaceSm))
            SecondaryButton(
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
    onRemove: (Uri) -> Unit
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
                ListItem(
                    modifier = Modifier.clickable { onOpen(database.uri) },
                    leadingContent = { Icon(Icons.Filled.Key, contentDescription = null) },
                    headlineContent = {
                        Text(database.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(database.uri) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.recent_database_remove)
                            )
                        }
                    }
                )
            }
        }
    }
}
