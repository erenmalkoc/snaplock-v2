package com.erenium.snaplock.presentation.changepassword

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.security.BiometricAvailability
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.datasource.security.CipherResult
import com.erenium.snaplock.domain.usecase.ChangeCredentialsUseCase
import com.erenium.snaplock.presentation.main.AppMessages
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class ChangeMasterPasswordUiState(
    val password: String = "",
    val confirmation: String = "",
    val keyFileUri: Uri? = null,
    val keyFileName: String? = null,
    val isSaving: Boolean = false,
    val hadKeyFile: Boolean = false,
    val enrollFingerprint: Boolean = false,
    val errorStringId: Int? = null
) {
    val dropsKeyFile: Boolean get() = hadKeyFile && keyFileUri == null

    val canSave: Boolean get() = (password.isNotEmpty() || keyFileUri != null) && !isSaving
}

@HiltViewModel
class ChangeMasterPasswordViewModel @Inject constructor(
    private val changeCredentials: ChangeCredentialsUseCase,
    private val documentFiles: DocumentFiles,
    private val keyFilePrefs: KeyFilePrefs,
    private val sessionCache: SessionCache,
    private val vault: BiometricVault,
    private val appMessages: AppMessages
) : ViewModel() {
    private var pendingPassword: CharArray? = null
    private var activeEnrollment = false

    private val _uiState = MutableStateFlow(currentKeyFileState())
    val uiState = _uiState.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private fun currentKeyFileState(): ChangeMasterPasswordUiState {
        val keyFile = sessionCache.getUri()?.let { keyFilePrefs.get(it) }
            ?: return ChangeMasterPasswordUiState()
        return ChangeMasterPasswordUiState(
            keyFileUri = keyFile,
            keyFileName = documentFiles.displayName(keyFile),
            hadKeyFile = true
        )
    }

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
                    val database = sessionCache.getUri()
                    var offerEnrollment = false
                    if (database != null) {
                        keyFilePrefs.set(database, state.keyFileUri)
                        val wasEnabled = vault.isEnabled(database)
                        vault.disable(database)
                        if (wasEnabled) {
                            offerEnrollment = vault.availability() == BiometricAvailability.AVAILABLE
                            if (!offerEnrollment) appMessages.post(R.string.change_password_fingerprint_off)
                        }
                    }
                    if (offerEnrollment) {
                        pendingPassword = state.password.toCharArray()
                        _uiState.update { it.copy(enrollFingerprint = true) }
                    } else {
                        _done.trySend(Unit)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, errorStringId = error.saveErrorMessage()) } }
        }
    }

    fun takeCipher(): Cipher? {
        if (!_uiState.value.enrollFingerprint) return null
        _uiState.update { it.copy(enrollFingerprint = false) }
        val result = vault.enrollCipher()
        if (result is CipherResult.Ready) {
            activeEnrollment = true
            return result.cipher
        }
        finishWithoutEnrollment()
        return null
    }

    fun onBiometricSucceeded(cipher: Cipher) {
        if (!activeEnrollment) return
        activeEnrollment = false
        val database = sessionCache.getUri()
        val password = pendingPassword
        pendingPassword = null
        val sealed = database != null && password != null && vault.seal(database, cipher, password)
        password?.fill('\u0000')
        if (sealed) _done.trySend(Unit) else finishWithoutEnrollment()
    }

    fun onBiometricError() {
        if (!activeEnrollment) return
        activeEnrollment = false
        finishWithoutEnrollment()
    }

    private fun finishWithoutEnrollment() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
        appMessages.post(R.string.change_password_fingerprint_off)
        _done.trySend(Unit)
    }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    private companion object {
        const val MAX_KEY_FILE_BYTES = 1024L * 1024
    }
}
