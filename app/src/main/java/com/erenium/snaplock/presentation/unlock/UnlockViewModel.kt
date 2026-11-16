package com.erenium.snaplock.presentation.unlock

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.local.FileTooLargeException
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.security.BiometricAvailability
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.data.datasource.security.CipherResult
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.usecase.UnlockDatabaseUseCase
import com.erenium.snaplock.presentation.main.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

enum class PromptRequest { ENROLL, UNLOCK }

enum class BiometricState { UNAVAILABLE, NOT_ENROLLED, AVAILABLE, ENABLED }

data class UnlockUiState(
    val password: String = "",
    val keyFileUri: Uri? = null,
    val keyFileName: String? = null,
    val useBiometrics: Boolean = false,
    val biometric: BiometricState = BiometricState.UNAVAILABLE,
    val prompt: PromptRequest? = null,
    val isLoading: Boolean = false,
    val isUnlockSuccessful: Boolean = false,
    val error: UnlockError? = null,
    val infoStringId: Int? = null,
    val databaseName: String = "",
    val started: Boolean = false
) {
    val canUnlock: Boolean get() = password.isNotEmpty() || keyFileUri != null
    val errorStringId: Int? get() = error?.message
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockDatabaseUseCase: UnlockDatabaseUseCase,
    private val vault: BiometricVault,
    private val keyFilePrefs: KeyFilePrefs,
    private val documentFiles: DocumentFiles,
    private val recentDatabases: RecentDatabasesRepository,
    private val appMessages: AppMessages
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState = _uiState.asStateFlow()

    private var database: Uri? = null
    private var activeRequest: PromptRequest? = null
    private var pendingPassword: CharArray? = null
    private var autoPromptOffered = false

    init {
        vault.migrateLegacy()
    }

    fun start(uri: Uri) {
        database = uri
        _uiState.update { it.copy(databaseName = documentFiles.displayName(uri)) }
        loadKeyFile(uri)
        refreshBiometric()
        _uiState.update { it.copy(started = true) }
        val state = _uiState.value
        if (!autoPromptOffered && state.biometric == BiometricState.ENABLED && state.prompt == null && activeRequest == null) {
            autoPromptOffered = true
            _uiState.update { it.copy(prompt = PromptRequest.UNLOCK) }
        }
    }

    fun refreshBiometric() {
        val uri = database ?: return
        val availability = vault.availability()
        val enabled = vault.isEnabled(uri)
        val next = when (availability) {
            BiometricAvailability.UNAVAILABLE -> BiometricState.UNAVAILABLE
            BiometricAvailability.NOT_ENROLLED -> BiometricState.NOT_ENROLLED
            BiometricAvailability.AVAILABLE -> if (enabled) BiometricState.ENABLED else BiometricState.AVAILABLE
        }
        _uiState.update { it.copy(biometric = next, useBiometrics = it.useBiometrics && next == BiometricState.AVAILABLE) }
    }

    /** Restores the key file last used with [databaseUri]. */
    fun loadKeyFile(databaseUri: Uri) {
        if (_uiState.value.keyFileUri != null) return
        val keyFile = keyFilePrefs.get(databaseUri) ?: return
        _uiState.update { it.copy(keyFileUri = keyFile, keyFileName = documentFiles.displayName(keyFile)) }
    }

    fun onKeyFileSelected(keyFile: Uri) {
        _uiState.update {
            it.copy(keyFileUri = keyFile, keyFileName = documentFiles.displayName(keyFile), error = null)
        }
    }

    fun onClearKeyFile() {
        _uiState.update { it.copy(keyFileUri = null, keyFileName = null, error = null) }
    }

    fun onUseBiometricsChanged(use: Boolean) {
        _uiState.update { it.copy(useBiometrics = use && it.biometric == BiometricState.AVAILABLE) }
    }

    fun onPasswordChange(newPassword: String) {
        _uiState.update { it.copy(password = newPassword, error = null) }
    }

    fun onUnlockClicked(uri: Uri) {
        database = uri
        unlock(uri, _uiState.value.password, viaBiometric = false)
    }

    fun onFingerprintClicked() {
        if (_uiState.value.biometric != BiometricState.ENABLED || _uiState.value.isLoading) return
        _uiState.update { it.copy(prompt = PromptRequest.UNLOCK, error = null) }
    }

    fun onTurnOffFingerprint() {
        val uri = database ?: return
        vault.disable(uri)
        refreshBiometric()
    }

    fun onDatabasePicked(uri: Uri) {
        recentDatabases.keepAccess(uri)
    }

    fun onRemoveFromList(uri: Uri) {
        recentDatabases.remove(uri)
    }

    /** The cipher the system prompt must be bound to, or null when [request] cannot go ahead. */
    fun takeCipher(request: PromptRequest): Cipher? {
        val uri = database
        if (_uiState.value.prompt != request || uri == null) return null
        _uiState.update { it.copy(prompt = null) }
        val result = when (request) {
            PromptRequest.ENROLL -> vault.enrollCipher()
            PromptRequest.UNLOCK -> vault.unlockCipher(uri)
        }
        return when (result) {
            is CipherResult.Ready -> {
                activeRequest = request
                result.cipher
            }
            CipherResult.Invalidated -> {
                refreshBiometric()
                _uiState.update { it.copy(infoStringId = R.string.fingerprint_invalidated) }
                null
            }
            CipherResult.Failed -> {
                if (request == PromptRequest.ENROLL) {
                    skipEnrollment()
                } else {
                    refreshBiometric()
                    _uiState.update { it.copy(error = UnlockError.FINGERPRINT_FAILED) }
                }
                null
            }
        }
    }

    fun onBiometricSucceeded(cipher: Cipher) {
        val uri = database
        val request = activeRequest
        activeRequest = null
        if (uri == null) return
        when (request) {
            PromptRequest.ENROLL -> finishEnrollment(uri, cipher)
            PromptRequest.UNLOCK -> {
                val chars = vault.open(uri, cipher)
                if (chars == null) {
                    passwordChanged(uri)
                } else {
                    val password = String(chars)
                    chars.fill('\u0000')
                    unlock(uri, password, viaBiometric = true)
                }
            }
            null -> Unit
        }
    }

    fun onBiometricError(code: Int) {
        val request = activeRequest
        activeRequest = null
        when (request) {
            PromptRequest.ENROLL -> skipEnrollment()
            PromptRequest.UNLOCK -> when (val outcome = biometricErrorOutcome(code)) {
                BiometricErrorOutcome.Silent -> Unit
                BiometricErrorOutcome.Unavailable -> refreshBiometric()
                is BiometricErrorOutcome.Show -> _uiState.update { it.copy(error = outcome.error) }
            }
            null -> Unit
        }
    }

    private fun finishEnrollment(uri: Uri, cipher: Cipher) {
        val password = pendingPassword
        pendingPassword = null
        val sealed = password != null && vault.seal(uri, cipher, password)
        password?.fill('\u0000')
        if (sealed) {
            _uiState.update { it.copy(isUnlockSuccessful = true) }
        } else {
            skipEnrollment()
        }
    }

    private fun skipEnrollment() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
        appMessages.post(R.string.fingerprint_not_enabled)
        _uiState.update { it.copy(isUnlockSuccessful = true) }
    }

    private fun passwordChanged(uri: Uri) {
        vault.disable(uri)
        refreshBiometric()
        _uiState.update { it.copy(isLoading = false, error = null, infoStringId = R.string.fingerprint_password_changed) }
    }

    private fun unlock(uri: Uri, password: String, viaBiometric: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, infoStringId = null) }

            val keyFileUri = _uiState.value.keyFileUri
            var keyFile: ByteArray? = null
            if (keyFileUri != null) {
                val read = documentFiles.read(keyFileUri, MAX_KEY_FILE_BYTES)
                keyFile = read.getOrNull()?.content
                if (keyFile == null) {
                    val lost = read.exceptionOrNull() !is FileTooLargeException
                    if (lost) {
                        keyFilePrefs.set(uri, null)
                        _uiState.update {
                            it.copy(isLoading = false, keyFileUri = null, keyFileName = null, error = UnlockError.KEY_FILE_LOST)
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = UnlockError.KEY_FILE) }
                    }
                    return@launch
                }
            }

            val result = try {
                unlockDatabaseUseCase(uri, password, keyFile)
            } catch (e: OutOfMemoryError) {
                Result.failure(e)
            }

            result.onSuccess {
                keyFilePrefs.set(uri, keyFileUri)
                val state = _uiState.value
                if (!viaBiometric && state.useBiometrics && state.biometric == BiometricState.AVAILABLE) {
                    pendingPassword = password.toCharArray()
                    _uiState.update { it.copy(isLoading = false, prompt = PromptRequest.ENROLL) }
                } else {
                    _uiState.update { it.copy(isLoading = false, isUnlockSuccessful = true) }
                }
            }
            result.onFailure { failure ->
                val error = failure.toUnlockError()
                if (viaBiometric && error == UnlockError.WRONG_CREDENTIALS) {
                    passwordChanged(uri)
                } else {
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
            }
        }
    }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    private companion object {
        const val MAX_KEY_FILE_BYTES = 1024L * 1024
    }
}
