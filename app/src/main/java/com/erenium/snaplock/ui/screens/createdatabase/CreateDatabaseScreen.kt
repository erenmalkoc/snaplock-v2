package com.erenium.snaplock.ui.screens.createdatabase

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.createdatabase.CreateDatabaseViewModel
import com.erenium.snaplock.ui.components.AppLogo
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.theme.Dimens

private const val DEFAULT_DATABASE_FILE_NAME = "snaplock.kdbx"

@Composable
fun CreateDatabaseScreen(
    onNavigateBack: () -> Unit,
    onDatabaseCreated: () -> Unit,
    viewModel: CreateDatabaseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri -> if (uri != null) viewModel.onCreate(uri) }
    )

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
                modifier = Modifier.fillMaxWidth()
            )

            val showMismatch = state.confirmPassword.isNotEmpty() && !state.passwordsMatch
            PasswordTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = stringResource(R.string.create_db_confirm),
                placeholder = stringResource(R.string.enter_password_placeholder),
                isError = showMismatch || state.errorStringId != null,
                supportingText = when {
                    showMismatch -> {
                        {
                            Text(
                                text = stringResource(R.string.create_db_password_mismatch),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    state.errorStringId != null -> {
                        {
                            Text(
                                text = stringResource(state.errorStringId!!),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> null
                },
                modifier = Modifier.fillMaxWidth()
            )

            PrimaryButton(
                text = stringResource(R.string.create_db_submit),
                onClick = { createDocumentLauncher.launch(DEFAULT_DATABASE_FILE_NAME) },
                enabled = state.canSubmit
            )
        }
    }
}
