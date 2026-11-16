package com.erenium.snaplock.presentation.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.text.localeComparator
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.EmptyRecycleBinUseCase
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetRecycledEntriesUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
    val errorStringId: Int? = null,
    val viewing: Entry? = null
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val recycledEntries: GetRecycledEntriesUseCase,
    private val restoreEntry: RestoreEntryUseCase,
    private val deleteEntry: DeleteEntryUseCase,
    private val emptyRecycleBin: EmptyRecycleBinUseCase,
    private val getEntries: GetEntriesUseCase
) : ViewModel() {

    private data class Dialogs(
        val pendingDelete: Entry? = null,
        val confirmEmpty: Boolean = false,
        val errorStringId: Int? = null,
        val viewing: Entry? = null
    )

    private val dialogs = MutableStateFlow(Dialogs())

    private val _messages = Channel<Pair<Int, String?>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<RecycleBinUiState> =
        combine(recycledEntries(), dialogs) { entries, dialogs ->
            RecycleBinUiState(
                entries = entries.sortedWith(compareBy(localeComparator()) { it.title }),
                isLoading = false,
                pendingDelete = dialogs.pendingDelete,
                confirmEmpty = dialogs.confirmEmpty,
                errorStringId = dialogs.errorStringId,
                viewing = dialogs.viewing
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecycleBinUiState())

    fun onRestore(uuid: UUID) {
        dialogs.update { it.copy(viewing = null) }
        viewModelScope.launch {
            val entry = recycledEntries().first().firstOrNull { it.uuid == uuid }
            restoreEntry(uuid)
                .onSuccess {
                    val restored = getEntries().first().firstOrNull { it.uuid == uuid } ?: return@onSuccess
                    val fellBack = entry?.previousGroupUuid?.let { it != restored.groupUuid } == true
                    _messages.trySend(
                        (if (fellBack) R.string.recycle_bin_restored_fallback else R.string.recycle_bin_restored) to restored.groupName
                    )
                }
                .onFailure { error -> dialogs.update { it.copy(errorStringId = error.saveErrorMessage()) } }
        }
    }

    fun onView(entry: Entry) = dialogs.update { it.copy(viewing = entry) }

    fun onDeleteRequest(entry: Entry) = dialogs.update { it.copy(pendingDelete = entry, viewing = null) }

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

    fun onDismissDialog() = dialogs.update { it.copy(pendingDelete = null, confirmEmpty = false, viewing = null) }

    fun onErrorShown() = dialogs.update { it.copy(errorStringId = null) }

    private fun run(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            action().onFailure { error -> dialogs.update { it.copy(errorStringId = error.saveErrorMessage()) } }
        }
    }
}
