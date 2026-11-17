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

data class DatabaseSettingsFields(
    val name: String,
    val recycleBinEnabled: Boolean,
    val historyMaxItems: Int,
    val cipher: VaultCipher,
    val kdf: KdfAlgorithm,
    val iterations: String,
    val memoryMiB: String,
    val parallelism: String
)

data class BenchmarkLowering(val measured: Long, val current: Long)

fun benchmarkOutcome(current: Long?, measured: Long, max: Long): Pair<Long, BenchmarkLowering?> {
    val capped = measured.coerceIn(1, max)
    return if (current != null && capped < current) current to BenchmarkLowering(capped, current) else capped to null
}

data class DatabaseSettingsUiState(
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
    val errorStringId: Int? = null,
    val loadFailed: Boolean = false,
    val baseline: DatabaseSettingsFields? = null,
    val benchmarkIterations: Long? = null,
    val lowering: BenchmarkLowering? = null,
    val confirmEncryptionSave: Boolean = false
) {
    val isLoading: Boolean get() = saved == null && !loadFailed

    val fields: DatabaseSettingsFields
        get() = DatabaseSettingsFields(
            name.trim(), recycleBinEnabled, historyMaxItems, cipher, kdf, iterations, memoryMiB, parallelism
        )

    val hasUnsavedChanges: Boolean get() = baseline != null && fields != baseline

    val showRecycleBinWarning: Boolean get() = saved != null && !recycleBinEnabled

    val showHistoryWarning: Boolean get() = saved != null && historyMaxItems == 0

    val encryptionChanged: Boolean
        get() = saved?.encryption != null && edited?.encryption != saved.encryption

    val estimatedUnlockSeconds: Int?
        get() {
            val rate = benchmarkIterations ?: return null
            val passes = iterations.toLongOrNull() ?: return null
            return ((passes + rate / 2) / rate).toInt().coerceAtLeast(1)
        }

    val isArgon2: Boolean get() = kdf != KdfAlgorithm.AES_KDF

    val encryption: EncryptionSettings?
        get() {
            val original = saved?.encryption ?: return null
            val passes = iterations.toLongOrNull() ?: return null
            if (!isArgon2) return EncryptionSettings(cipher, kdf, passes, 0, 0)
            val mebibytes = memoryMiB.toLongOrNull() ?: return null
            val lanes = parallelism.toIntOrNull() ?: return null
            val memory = if (original.memoryBytes / BYTES_PER_MIB == mebibytes && original.kdf != KdfAlgorithm.AES_KDF) {
                original.memoryBytes
            } else {
                mebibytes * BYTES_PER_MIB
            }
            return EncryptionSettings(cipher, kdf, passes, memory, lanes)
        }

    private val savedSameFamily: EncryptionSettings?
        get() = saved?.encryption?.takeIf { (it.kdf == KdfAlgorithm.AES_KDF) == !isArgon2 }

    private val maxIterations: Long get() = if (isArgon2) MAX_ARGON2_PASSES else MAX_AES_ROUNDS

    val maxIterationsAllowed: Long get() = maxIterations

    val iterationsInvalid: Boolean
        get() = iterations.toLongOrNull()?.let {
            it < 1 || (it > maxIterations && it != savedSameFamily?.iterations)
        } ?: true

    val memoryInvalid: Boolean
        get() = isArgon2 && (memoryMiB.toLongOrNull()?.let {
            it < 1 || (it > MAX_MEMORY_MIB && it != savedSameFamily?.let { e -> e.memoryBytes / BYTES_PER_MIB })
        } ?: true)

    val parallelismInvalid: Boolean
        get() = isArgon2 && (parallelism.toIntOrNull()?.let { it !in 1..MAX_ARGON2_PARALLELISM } ?: true)

    val memoryIsHigh: Boolean get() = isArgon2 && (memoryMiB.toLongOrNull() ?: 0) > HIGH_MEMORY_MIB

    val edited: DatabaseSettings?
        get() {
            val original = saved ?: return null
            val encryption = if (original.encryption == null) {
                null
            } else {
                if (iterationsInvalid || memoryInvalid || parallelismInvalid) return null
                encryption?.takeIf { it.problem() == null } ?: return null
            }
            return DatabaseSettings(name.trim(), recycleBinEnabled, historyMaxItems, encryption)
        }

    val canSave: Boolean
        get() = edited.let { it != null && it != saved } && !isSaving && !isBenchmarking

    companion object {
        const val HIGH_MEMORY_MIB = 256L
        const val MAX_MEMORY_MIB = 1024L
        const val MAX_ARGON2_PASSES = 1000L
        const val MAX_AES_ROUNDS = 100_000_000L
    }
}

@HiltViewModel
class DatabaseSettingsViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val benchmark: KdfBenchmark
) : ViewModel() {
    private val _uiState = MutableStateFlow(DatabaseSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
    }

    fun onRetry() {
        _uiState.update { it.copy(loadFailed = false) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val settings = runCatching { repository.getDatabaseSettings() }.getOrNull()
            if (settings == null) {
                _uiState.update { it.copy(loadFailed = true) }
                return@launch
            }
            val encryption = settings.encryption
            _uiState.update {
                val loaded = it.copy(
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
                loaded.copy(baseline = loaded.fields)
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorStringId = null) }

    fun onRecycleBinChange(enabled: Boolean) = _uiState.update { it.copy(recycleBinEnabled = enabled, errorStringId = null) }

    fun onHistoryMaxItemsChange(count: Int) = _uiState.update { it.copy(historyMaxItems = count, errorStringId = null) }

    fun onCipherChange(cipher: VaultCipher) = _uiState.update { it.copy(cipher = cipher, benchmarkIterations = null, errorStringId = null) }

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
            benchmarkIterations = null,
            lowering = null,
            errorStringId = null
        )
    }

    fun onIterationsChange(value: String) = _uiState.update { it.copy(iterations = value.digitsOnly(), errorStringId = null) }

    fun onMemoryChange(value: String) =
        _uiState.update { it.copy(memoryMiB = value.digitsOnly(), benchmarkIterations = null, errorStringId = null) }

    fun onParallelismChange(value: String) =
        _uiState.update { it.copy(parallelism = value.digitsOnly(), benchmarkIterations = null, errorStringId = null) }

    fun onBenchmark() {
        val probe = _uiState.value.copy(iterations = "1").encryption?.takeIf { it.problem() == null } ?: return
        _uiState.update { it.copy(isBenchmarking = true) }
        viewModelScope.launch {
            val iterations = try {
                benchmark.iterationsFor(probe)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
            onBenchmarkResult(iterations)
        }
    }

    internal fun onBenchmarkResult(iterations: Long?) {
        _uiState.update { state ->
            if (iterations == null) {
                state.copy(isBenchmarking = false, errorStringId = R.string.database_benchmark_error)
            } else {
                val (chosen, lowering) = benchmarkOutcome(
                    state.iterations.toLongOrNull(), iterations, state.maxIterationsAllowed
                )
                state.copy(
                    iterations = chosen.toString(),
                    isBenchmarking = false,
                    benchmarkIterations = iterations.coerceIn(1, state.maxIterationsAllowed),
                    lowering = lowering,
                    errorStringId = null
                )
            }
        }
    }

    fun onLoweringConfirmed() = _uiState.update { state ->
        val lowering = state.lowering ?: return@update state
        state.copy(iterations = lowering.measured.toString(), lowering = null)
    }

    fun onLoweringDismissed() = _uiState.update { it.copy(lowering = null) }

    fun onSave(confirmed: Boolean = false) {
        val state = _uiState.value
        if (!state.canSave) return
        if (state.encryptionChanged && !confirmed) {
            _uiState.update { it.copy(confirmEncryptionSave = true) }
            return
        }
        val settings = state.edited ?: return
        _uiState.update { it.copy(isSaving = true, confirmEncryptionSave = false, errorStringId = null) }
        viewModelScope.launch {
            val result = runCatching { repository.updateDatabaseSettings(settings).getOrThrow() }
            result
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saved = settings, baseline = it.fields) }
                    _messages.send(R.string.database_settings_saved)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorStringId = error.saveErrorMessage(R.string.database_settings_save_error))
                    }
                }
        }
    }

    fun onEncryptionSaveDismissed() = _uiState.update { it.copy(confirmEncryptionSave = false) }

    fun onEncryptionSaveConfirmed() = onSave(confirmed = true)

    private fun String.digitsOnly(): String = filter(Char::isDigit).take(MAX_DIGITS)

    private companion object {
        const val DEFAULT_ARGON2_PASSES = 8L
        const val DEFAULT_AES_ROUNDS = 600_000L
        const val DEFAULT_MEMORY_MIB = 32L
        const val DEFAULT_PARALLELISM = 2
        const val MAX_DIGITS = 10
    }
}
