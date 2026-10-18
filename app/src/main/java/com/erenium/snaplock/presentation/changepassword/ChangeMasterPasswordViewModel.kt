package com.erenium.snaplock.presentation.changepassword

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.domain.usecase.ChangeCredentialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangeMasterPasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val keyFileUri: Uri? = null,
    val keyFileName: String? = null,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null
) {
    val canSave: Boolean get() = (password.isNotEmpty() || keyFileUri != null) && !isSaving
}

@HiltViewModel
class ChangeMasterPasswordViewModel @Inject constructor(
    private val changeCredentials: ChangeCredentialsUseCase,
    private val documentFiles: DocumentFiles,
    private val keyFilePrefs: KeyFilePrefs,
    private val encryptedPrefs: EncryptedPrefs,
    private val recentDatabasesPrefs: RecentDatabasesPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeMasterPasswordUiState())
    val uiState = _uiState.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorStringId = null) }

    fun onConfirmationChange(value: String) = _uiState.update { it.copy(confirmation = value, errorStringId = null) }

    fun onKeyFileSelected(keyFile: Uri) = _uiState.update {
        it.copy(keyFileUri = keyFile, keyFileName = documentFiles.displayName(keyFile), errorStringId = null)
    }

    fun onClearKeyFile() = _uiState.update { it.copy(keyFileUri = null, keyFileName = null) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        if (state.password != state.confirmation) {
            _uiState.update { it.copy(errorStringId = R.string.create_db_password_mismatch) }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorStringId = null) }
        viewModelScope.launch {
            val keyFile = state.keyFileUri?.let { uri ->
                documentFiles.read(uri, MAX_KEY_FILE_BYTES).getOrElse {
                    _uiState.update { it.copy(isSaving = false, errorStringId = R.string.unlock_key_file_error) }
                    return@launch
                }.content
            }
            changeCredentials(state.password, keyFile)
                .onSuccess {
                    // The biometric copy holds the old password, so it would only fail from now on.
                    encryptedPrefs.clear()
                    // The open database is always the most recently opened one.
                    recentDatabasesPrefs.uris.value.firstOrNull()?.let { keyFilePrefs.set(it, state.keyFileUri) }
                    _done.trySend(Unit)
                }
                .onFailure { _uiState.update { it.copy(isSaving = false, errorStringId = R.string.entry_save_error) } }
        }
    }

    private companion object {
        const val MAX_KEY_FILE_BYTES = 1024L * 1024
    }
}
