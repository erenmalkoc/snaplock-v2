package com.erenium.snaplock.ui.screens.createdatabase

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.createdatabase.CreateDatabaseViewModel
import com.erenium.snaplock.ui.components.AppLogo
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.StrengthMeter
import com.erenium.snaplock.ui.theme.Dimens

private const val DEFAULT_DATABASE_FILE_NAME = "snaplock.kdbx"

@Composable
fun CreateDatabaseScreen(
    onNavigateBack: () -> Unit,
    onDatabaseCreated: () -> Unit,
    viewModel: CreateDatabaseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                // Keep access after a restart so the database shows up in the recent list
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                viewModel.onCreate(uri)
            }
        }
    )

    fun launchPicker() {
        if (viewModel.onSubmit()) createDocumentLauncher.launch(DEFAULT_DATABASE_FILE_NAME)
    }

    LaunchedEffect(viewModel) {
        viewModel.createdEvents.collect { onDatabaseCreated() }
    }

    AppScaffold(
        title = stringResource(R.string.create_db_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        if (state.isCreating) {
            LoadingSpinner(
                modifier = contentModifier.fillMaxSize(),
                message = stringResource(R.string.create_db_progress)
            )
            return@AppScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            AppLogo(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = Dimens.spaceXxl * 2
            )

            Text(
                text = stringResource(R.string.create_db_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PasswordTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.create_db_password),
                placeholder = stringResource(R.string.enter_password_placeholder),
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth()
            )
            state.strength?.let { StrengthMeter(it) }
            if (state.isShort) {
                Text(
                    text = stringResource(R.string.create_db_short_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val confirmError = when {
                state.showMismatch -> R.string.create_db_password_mismatch
                else -> state.errorStringId
            }
            PasswordTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = stringResource(R.string.create_db_confirm),
                placeholder = stringResource(R.string.enter_password_placeholder),
                isError = confirmError != null,
                supportingText = confirmError?.let { errorId ->
                    { Text(text = stringResource(errorId), color = MaterialTheme.colorScheme.error) }
                },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { launchPicker() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) viewModel.onConfirmFocusLost() }
            )

            PrimaryButton(
                icon = Icons.Filled.Add,
                text = stringResource(R.string.create_db_submit),
                onClick = ::launchPicker,
                enabled = state.canSubmit
            )
        }
    }
}
