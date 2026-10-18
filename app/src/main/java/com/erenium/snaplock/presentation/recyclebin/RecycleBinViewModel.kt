package com.erenium.snaplock.presentation.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.EmptyRecycleBinUseCase
import com.erenium.snaplock.domain.usecase.GetRecycledEntriesUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RecycleBinUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = true,
    /** Entry waiting for the "delete permanently" confirmation. */
    val pendingDelete: Entry? = null,
    val confirmEmpty: Boolean = false,
    val errorStringId: Int? = null
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    getRecycledEntries: GetRecycledEntriesUseCase,
    private val restoreEntry: RestoreEntryUseCase,
    private val deleteEntry: DeleteEntryUseCase,
    private val emptyRecycleBin: EmptyRecycleBinUseCase
) : ViewModel() {

    private data class Dialogs(
        val pendingDelete: Entry? = null,
        val confirmEmpty: Boolean = false,
        val errorStringId: Int? = null
    )

    private val dialogs = MutableStateFlow(Dialogs())

    val uiState: StateFlow<RecycleBinUiState> =
        combine(getRecycledEntries(), dialogs) { entries, dialogs ->
            RecycleBinUiState(
                entries = entries.sortedBy { it.title.lowercase() },
                isLoading = false,
                pendingDelete = dialogs.pendingDelete,
                confirmEmpty = dialogs.confirmEmpty,
                errorStringId = dialogs.errorStringId
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecycleBinUiState())

    fun onRestore(uuid: UUID) = run { restoreEntry(uuid) }

    fun onDeleteRequest(entry: Entry) = dialogs.update { it.copy(pendingDelete = entry) }

    fun onDeleteConfirmed() {
        val entry = dialogs.value.pendingDelete ?: return
        dialogs.update { it.copy(pendingDelete = null) }
        run { deleteEntry(entry.uuid) }
    }

    fun onEmptyRequest() = dialogs.update { it.copy(confirmEmpty = true) }

    fun onEmptyConfirmed() {
        dialogs.update { it.copy(confirmEmpty = false) }
        run { emptyRecycleBin() }
    }

    fun onDismissDialog() = dialogs.update { it.copy(pendingDelete = null, confirmEmpty = false) }

    fun onErrorShown() = dialogs.update { it.copy(errorStringId = null) }

    private fun run(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { error -> dialogs.update { it.copy(errorStringId = error.saveErrorMessage()) } }
        }
    }
}
