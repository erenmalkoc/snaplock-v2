package com.erenium.snaplock.presentation.databasesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.crypto.KdfBenchmark
import com.erenium.snaplock.domain.model.DatabaseSettings
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.MAX_ARGON2_PARALLELISM
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.domain.model.problem
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BYTES_PER_MIB = 1024L * 1024

data class DatabaseSettingsUiState(
    /** The settings as the file holds them; null until loaded. */
    val saved: DatabaseSettings? = null,
    val name: String = "",
    val recycleBinEnabled: Boolean = true,
    val historyMaxItems: Int = 10,
    val cipher: VaultCipher = VaultCipher.AES_256,
    val kdf: KdfAlgorithm = KdfAlgorithm.ARGON2D,
    val iterations: String = "",
    val memoryMiB: String = "",
    val parallelism: String = "",
    val isBenchmarking: Boolean = false,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null
) {
    val isArgon2: Boolean get() = kdf != KdfAlgorithm.AES_KDF

    /** Null when the file has no editable encryption (KDBX 3) or a value does not parse. */
    val encryption: EncryptionSettings?
        get() {
            val original = saved?.encryption ?: return null
            val passes = iterations.toLongOrNull() ?: return null
            if (!isArgon2) return EncryptionSettings(cipher, kdf, passes, 0, 0)
            val mebibytes = memoryMiB.toLongOrNull() ?: return null
            val lanes = parallelism.toIntOrNull() ?: return null
            // An unchanged field keeps the exact byte count, which need not be whole MiB.
            val memory = if (original.memoryBytes / BYTES_PER_MIB == mebibytes && original.kdf != KdfAlgorithm.AES_KDF) {
                original.memoryBytes
            } else {
                mebibytes * BYTES_PER_MIB
            }
            return EncryptionSettings(cipher, kdf, passes, memory, lanes)
        }

    val iterationsInvalid: Boolean get() = iterations.toLongOrNull()?.let { it < 1 } ?: true

    // Whole MiB always cover Argon2's 8 KiB per lane, so any positive amount works.
    val memoryInvalid: Boolean get() = isArgon2 && (memoryMiB.toLongOrNull()?.let { it < 1 } ?: true)

    val parallelismInvalid: Boolean
        get() = isArgon2 && (parallelism.toIntOrNull()?.let { it !in 1..MAX_ARGON2_PARALLELISM } ?: true)

    /** High enough that phones with little memory may fail to open the vault. */
    val memoryIsHigh: Boolean get() = isArgon2 && (memoryMiB.toLongOrNull() ?: 0) > HIGH_MEMORY_MIB

    val edited: DatabaseSettings?
        get() {
            val original = saved ?: return null
            val encryption = if (original.encryption == null) {
                null
            } else {
                encryption?.takeIf { it.problem() == null } ?: return null
            }
            return DatabaseSettings(name.trim(), recycleBinEnabled, historyMaxItems, encryption)
        }

    val canSave: Boolean
        get() = edited.let { it != null && it != saved } && !isSaving && !isBenchmarking

    companion object {
        const val HIGH_MEMORY_MIB = 256L
    }
}

/** Edits the settings stored in the open vault file. */
@HiltViewModel
class DatabaseSettingsViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val benchmark: KdfBenchmark
) : ViewModel() {

    private val _uiState = MutableStateFlow(DatabaseSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = repository.getDatabaseSettings() ?: return@launch
            val encryption = settings.encryption
            _uiState.update {
                it.copy(
                    saved = settings,
                    name = settings.name,
                    recycleBinEnabled = settings.recycleBinEnabled,
                    historyMaxItems = settings.historyMaxItems,
                    cipher = encryption?.cipher ?: VaultCipher.AES_256,
                    kdf = encryption?.kdf ?: KdfAlgorithm.ARGON2D,
                    iterations = encryption?.iterations?.toString().orEmpty(),
                    memoryMiB = encryption?.takeIf { e -> e.kdf != KdfAlgorithm.AES_KDF }
                        ?.let { e -> (e.memoryBytes / BYTES_PER_MIB).toString() } ?: DEFAULT_MEMORY_MIB.toString(),
                    parallelism = encryption?.takeIf { e -> e.kdf != KdfAlgorithm.AES_KDF }
                        ?.parallelism?.toString() ?: DEFAULT_PARALLELISM.toString()
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorStringId = null) }

    fun onRecycleBinChange(enabled: Boolean) = _uiState.update { it.copy(recycleBinEnabled = enabled, errorStringId = null) }

    fun onHistoryMaxItemsChange(count: Int) = _uiState.update { it.copy(historyMaxItems = count, errorStringId = null) }

    fun onCipherChange(cipher: VaultCipher) = _uiState.update { it.copy(cipher = cipher, errorStringId = null) }

    /** Passes and rounds mean different amounts of work, so switching families resets the count. */
    fun onKdfChange(kdf: KdfAlgorithm) = _uiState.update { state ->
        val familyChanged = (kdf == KdfAlgorithm.AES_KDF) != (state.kdf == KdfAlgorithm.AES_KDF)
        state.copy(
            kdf = kdf,
            iterations = if (!familyChanged) {
                state.iterations
            } else if (kdf == KdfAlgorithm.AES_KDF) {
                DEFAULT_AES_ROUNDS.toString()
            } else {
                DEFAULT_ARGON2_PASSES.toString()
            },
            errorStringId = null
        )
    }

    fun onIterationsChange(value: String) = _uiState.update { it.copy(iterations = value.digitsOnly(), errorStringId = null) }

    fun onMemoryChange(value: String) = _uiState.update { it.copy(memoryMiB = value.digitsOnly(), errorStringId = null) }

    fun onParallelismChange(value: String) = _uiState.update { it.copy(parallelism = value.digitsOnly(), errorStringId = null) }

    /** Sets the iterations that make unlocking take about a second on this device. */
    fun onBenchmark() {
        val probe = _uiState.value.copy(iterations = "1").encryption?.takeIf { it.problem() == null } ?: return
        _uiState.update { it.copy(isBenchmarking = true) }
        viewModelScope.launch {
            val iterations = benchmark.iterationsFor(probe)
            _uiState.update { it.copy(iterations = iterations.toString(), isBenchmarking = false) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        val settings = state.edited ?: return
        _uiState.update { it.copy(isSaving = true, errorStringId = null) }
        viewModelScope.launch {
            repository.updateDatabaseSettings(settings)
                .onSuccess { _done.send(Unit) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorStringId = error.saveErrorMessage(R.string.database_settings_save_error))
                    }
                }
        }
    }

    private fun String.digitsOnly(): String = filter(Char::isDigit).take(MAX_DIGITS)

    private companion object {
        const val DEFAULT_ARGON2_PASSES = 8L
        const val DEFAULT_AES_ROUNDS = 600_000L
        const val DEFAULT_MEMORY_MIB = 32L
        const val DEFAULT_PARALLELISM = 2
        const val MAX_DIGITS = 10
    }
}
