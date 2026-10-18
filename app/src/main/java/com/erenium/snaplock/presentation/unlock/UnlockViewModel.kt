package com.erenium.snaplock.presentation.unlock

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.prefs.EncryptedPrefs
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.security.BiometricCryptoManager
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class UnlockUiState(
    val password: String = "",
    val keyFileUri: Uri? = null,
    val keyFileName: String? = null,
    val useBiometrics: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val isLoading: Boolean = false,
    val isUnlockSuccessful: Boolean = false,
    val errorStringId: Int? = null
) {
    val canUnlock: Boolean get() = password.isNotBlank() || keyFileUri != null
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockDatabaseUseCase: UnlockDatabaseUseCase,
    val cryptoManager: BiometricCryptoManager,
    val encryptedPrefs: EncryptedPrefs,
    private val keyFilePrefs: KeyFilePrefs,
    private val documentFiles: DocumentFiles
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (encryptedPrefs.getEncryptedPassword() != null) {
            _uiState.update { it.copy(showBiometricPrompt = true) }
        }
    }

    /** Restores the key file last used with [databaseUri]. */
    fun loadKeyFile(databaseUri: Uri) {
        val keyFile = keyFilePrefs.get(databaseUri) ?: return
        _uiState.update { it.copy(keyFileUri = keyFile, keyFileName = documentFiles.displayName(keyFile)) }
    }

    fun onKeyFileSelected(keyFile: Uri) {
        _uiState.update {
            it.copy(keyFileUri = keyFile, keyFileName = documentFiles.displayName(keyFile), errorStringId = null)
        }
    }

    fun onClearKeyFile() {
        _uiState.update { it.copy(keyFileUri = null, keyFileName = null, errorStringId = null) }
    }

    fun onUseBiometricsChanged(use: Boolean) {
        _uiState.update { it.copy(useBiometrics = use) }
    }

    fun onBiometricFailed() {
        _uiState.update {
            it.copy(showBiometricPrompt = false, errorStringId = R.string.show_validation_error)
        }
    }

    fun onBiometricSuccess(cipher: Cipher, uri: Uri) {
        try {
            val encryptedPassword = encryptedPrefs.getEncryptedPassword()
            val decryptedPassword = cipher.doFinal(encryptedPassword).toString(Charsets.UTF_8)

            unlockWithPassword(uri, decryptedPassword)

        } catch (e: Exception) {
            _uiState.update { it.copy(errorStringId = R.string.biometric_decode_error) }
        }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update {
            it.copy(password = newPassword, errorStringId = null)
        }
    }

    fun onUnlockClicked(uri: Uri) {
        val currentPassword = _uiState.value.password
        unlockWithPassword(uri, currentPassword)
    }


    private fun unlockWithPassword(uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorStringId = null) }

            val keyFileUri = _uiState.value.keyFileUri
            val keyFile = keyFileUri?.let { documentFiles.read(it, MAX_KEY_FILE_BYTES).getOrNull()?.content }
            if (keyFileUri != null && keyFile == null) {
                _uiState.update { it.copy(isLoading = false, errorStringId = R.string.unlock_key_file_error) }
                return@launch
            }

            val result = unlockDatabaseUseCase(uri, password, keyFile)

            result.onSuccess {
                keyFilePrefs.set(uri, keyFileUri)
                if (_uiState.value.useBiometrics) {
                    savePasswordWithBiometrics(password)
                }

                _uiState.update {
                    it.copy(isLoading = false, isUnlockSuccessful = true)
                }
            }
            result.onFailure {
                _uiState.update { it.copy(isLoading = false, errorStringId = R.string.wrong_password) }
            }
        }
    }

    private fun savePasswordWithBiometrics(password: String) {
        try {
            val cipher = cryptoManager.getEncryptCipher()
            val encryptedPassword = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            encryptedPrefs.saveEncryptedCredentials(encryptedPassword, iv)
        } catch (e: Exception) {
            Log.e("UnlockViewModel", "Biyometrik şifre kaydı başarısız.", e)
        }
    }

    private companion object {
        // Real key files are tiny; anything bigger is almost certainly the wrong file.
        const val MAX_KEY_FILE_BYTES = 1024L * 1024
    }
}
