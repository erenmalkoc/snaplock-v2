package com.erenium.snaplock.ui.screens.autofill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.erenium.snaplock.R
import com.erenium.snaplock.autofill.AutofillSaveViewModel
import com.erenium.snaplock.autofill.SaveDecision
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.components.SecondaryButton
import com.erenium.snaplock.ui.screens.unlock.UnlockScreen
import com.erenium.snaplock.ui.theme.Dimens

/** Offers to store a login the user just typed into another app or website. */
@Composable
fun AutofillSaveScreen(
    viewModel: AutofillSaveViewModel,
    target: String,
    onDone: () -> Unit,
    onOpenApp: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onDone() }
    }

    val database = state.database
    val decision = state.decision
    when {
        state.isLoading -> LoadingSpinner()
        state.isLocked && database == null -> NoDatabase(onOpenApp = onOpenApp, onCancel = onCancel)
        state.isLocked && database != null -> UnlockScreen(
            uri = database.uri,
            onUnlockSuccess = {},
            onNavigateBack = onCancel
        )
        decision == null || decision == SaveDecision.AlreadySaved -> LoadingSpinner()
        else -> {
            val update = decision as? SaveDecision.Update
            AppScaffold(
                title = stringResource(
                    if (update != null) R.string.autofill_update_title else R.string.autofill_save_title
                ),
                onNavigateBack = onCancel
            ) { contentModifier ->
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (update != null) {
                            stringResource(R.string.autofill_update_message, update.credential.title)
                        } else {
                            stringResource(R.string.autofill_matches_header, target)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Dimens.spaceMd))
                    if (update == null) {
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text(stringResource(R.string.entry_form_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Dimens.spaceSm))
                    }
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text(stringResource(R.string.entry_username_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimens.spaceSm))
                    PasswordTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = stringResource(R.string.entry_password_label),
                        placeholder = "",
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.errorStringId != null,
                        supportingText = state.errorStringId?.let { errorId ->
                            { Text(stringResource(errorId), color = MaterialTheme.colorScheme.error) }
                        }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spaceLg))
                    PrimaryButton(
                        text = stringResource(
                            if (update != null) R.string.autofill_update_button else R.string.entry_form_save
                        ),
                        onClick = { viewModel.onSave() },
                        enabled = !state.isSaving && state.password.isNotEmpty()
                    )
                    if (update != null) {
                        Spacer(modifier = Modifier.height(Dimens.spaceSm))
                        SecondaryButton(
                            text = stringResource(R.string.autofill_save_as_new),
                            onClick = { viewModel.onSave(asNew = true) },
                            enabled = !state.isSaving && state.password.isNotEmpty()
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimens.spaceSm))
                    AppTextButton(
                        text = stringResource(R.string.cancel_button),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
