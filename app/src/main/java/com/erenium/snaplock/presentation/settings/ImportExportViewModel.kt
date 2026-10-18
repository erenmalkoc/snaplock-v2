package com.erenium.snaplock.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
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

/** A user-facing result: a string or plural resource, its count and an optional argument. */
data class ImportExportMessage(val resId: Int, val count: Int? = null, val argument: String? = null)

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val documentFiles: DocumentFiles
) : ViewModel() {

    /** A parsed file waiting for the user to confirm the import. */
    private val _pendingImport = MutableStateFlow<CsvImport?>(null)
    val pendingImport = _pendingImport.asStateFlow()

    private val _messages = Channel<ImportExportMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onImportFilePicked(uri: Uri) {
        viewModelScope.launch {
            val text = documentFiles.read(uri, MAX_CSV_BYTES).getOrNull()?.content?.toString(Charsets.UTF_8)
            val parsed = text?.let { withContext(Dispatchers.Default) { CsvImporter.parse(it) } }
            if (parsed == null || parsed.entries.isEmpty()) {
                _messages.send(ImportExportMessage(R.string.import_unrecognised))
            } else {
                _pendingImport.value = parsed
            }
        }
    }

    /** @param groupName the group the entries go into, already localised. */
    fun onImportConfirmed(groupName: String) {
        val import = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            repository.importEntries(groupName, import.entries)
                .onSuccess { _messages.send(ImportExportMessage(R.plurals.import_done, count = it)) }
                .onFailure { _messages.send(ImportExportMessage(it.saveErrorMessage(R.string.import_error))) }
        }
    }

    fun onImportDismissed() {
        _pendingImport.value = null
    }

    fun onExportTargetPicked(uri: Uri) {
        viewModelScope.launch {
            val csv = withContext(Dispatchers.Default) { CsvExporter.write(repository.getExportEntries()) }
            documentFiles.write(uri, csv.toByteArray(Charsets.UTF_8))
                .onSuccess { _messages.send(ImportExportMessage(R.string.export_done)) }
                .onFailure { _messages.send(ImportExportMessage(R.string.import_error)) }
        }
    }

    private companion object {
        const val MAX_CSV_BYTES = 20L * 1024 * 1024
    }
}
