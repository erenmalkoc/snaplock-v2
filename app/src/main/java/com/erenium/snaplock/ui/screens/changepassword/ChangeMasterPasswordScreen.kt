package com.erenium.snaplock.ui.screens.changepassword

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.changepassword.ChangeMasterPasswordViewModel
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.AppTextButton
import com.erenium.snaplock.ui.components.PasswordTextField
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun ChangeMasterPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChangeMasterPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { keyFile ->
        if (keyFile != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(keyFile, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.onKeyFileSelected(keyFile)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onNavigateBack() }
    }

    AppScaffold(
        title = stringResource(R.string.change_password_title),
        onNavigateBack = onNavigateBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.change_password_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spaceLg))
            PasswordTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.change_password_new),
                placeholder = "",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Dimens.spaceSm))
            PasswordTextField(
                value = state.confirmation,
                onValueChange = viewModel::onConfirmationChange,
                label = stringResource(R.string.create_db_confirm),
                placeholder = "",
                modifier = Modifier.fillMaxWidth(),
                isError = state.errorStringId != null,
                supportingText = state.errorStringId?.let { errorId ->
                    { Text(stringResource(errorId), color = MaterialTheme.colorScheme.error) }
                }
            )
            val keyFileName = state.keyFileName
            if (keyFileName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceSm)
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null)
                    Text(
                        text = keyFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Dimens.spaceMd)
                    )
                    IconButton(onClick = viewModel::onClearKeyFile) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.unlock_key_file_clear))
                    }
                }
            } else {
                AppTextButton(
                    text = stringResource(R.string.unlock_key_file_choose),
                    onClick = { keyFileLauncher.launch(arrayOf("*/*")) }
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spaceLg))
            PrimaryButton(
                text = stringResource(R.string.change_password_submit),
                onClick = viewModel::onSave,
                enabled = state.canSave
            )
        }
    }
}
