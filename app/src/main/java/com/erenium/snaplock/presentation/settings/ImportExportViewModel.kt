package com.erenium.snaplock.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.local.FileTooLargeException
import com.erenium.snaplock.domain.importer.CsvExporter
import com.erenium.snaplock.domain.importer.CsvImport
import com.erenium.snaplock.domain.importer.CsvImporter
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportExportMessage(val resId: Int, val count: Int? = null, val argument: String? = null)

fun CsvImport.previewTitles(limit: Int = IMPORT_PREVIEW_COUNT): List<String> =
    entries.asSequence().map { it.title.trim() }.filter { it.isNotEmpty() }.take(limit).toList()

const val IMPORT_PREVIEW_COUNT = 3

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val documentFiles: DocumentFiles
) : ViewModel() {
    private val _pendingImport = MutableStateFlow<CsvImport?>(null)
    val pendingImport = _pendingImport.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()

    private val _messages = Channel<ImportExportMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onImportFilePicked(uri: Uri) {
        if (_isWorking.value) return
        _isWorking.value = true
        viewModelScope.launch {
            try {
                val document = documentFiles.read(uri, MAX_CSV_BYTES).getOrElse { error ->
                    val message = if (error is FileTooLargeException) R.string.import_too_large else R.string.import_unreadable
                    _messages.send(ImportExportMessage(message))
                    return@launch
                }
                val parsed = withContext(Dispatchers.Default) {
                    runCatching { CsvImporter.parse(document.content.toString(Charsets.UTF_8)) }.getOrNull()
                }
                if (parsed == null || parsed.entries.isEmpty()) {
                    _messages.send(ImportExportMessage(R.string.import_unrecognised))
                } else {
                    _pendingImport.value = parsed
                }
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun onImportConfirmed(groupName: String) {
        val import = _pendingImport.value ?: return
        _pendingImport.value = null
        _isWorking.value = true
        viewModelScope.launch {
            try {
                repository.importEntries(groupName, import.entries)
                    .onSuccess { _messages.send(ImportExportMessage(R.plurals.import_done, count = it)) }
                    .onFailure { _messages.send(ImportExportMessage(it.saveErrorMessage(R.string.import_error))) }
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun onImportDismissed() {
        _pendingImport.value = null
    }

    fun onExportTargetPicked(uri: Uri) {
        if (_isWorking.value) return
        _isWorking.value = true
        viewModelScope.launch {
            try {
                val csv = runCatching {
                    withContext(Dispatchers.Default) { CsvExporter.write(repository.getExportEntries()) }
                }.getOrElse {
                    _messages.send(ImportExportMessage(R.string.export_error))
                    return@launch
                }
                documentFiles.write(uri, csv.toByteArray(Charsets.UTF_8))
                    .onSuccess { _messages.send(ImportExportMessage(R.string.export_done)) }
                    .onFailure { _messages.send(ImportExportMessage(R.string.export_error)) }
            } finally {
                _isWorking.value = false
            }
        }
    }

    private companion object {
        const val MAX_CSV_BYTES = 20L * 1024 * 1024
    }
}
