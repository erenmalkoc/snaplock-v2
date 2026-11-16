package com.erenium.snaplock.presentation.selectfile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.RecentDatabase
import com.erenium.snaplock.presentation.main.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SelectFileViewModel @Inject constructor(
    private val recentDatabasesRepository: RecentDatabasesRepository,
    private val appMessages: AppMessages
) : ViewModel() {

    val recentDatabases: StateFlow<List<RecentDatabase>> =
        recentDatabasesRepository.entries.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _removed = Channel<Unit>(Channel.BUFFERED)
    val removed: Flow<Unit> = _removed.receiveAsFlow()

    private var pendingRemoval: Pair<Uri, Int>? = null

    fun refresh() = recentDatabasesRepository.refresh()

    fun onRemoveRecent(uri: Uri) {
        commitRemoval()
        pendingRemoval = uri to recentDatabasesRepository.hide(uri)
        _removed.trySend(Unit)
    }

    fun onUndoRemove() {
        val (uri, index) = pendingRemoval ?: return
        pendingRemoval = null
        recentDatabasesRepository.restore(uri, index)
    }

    fun onForget(uri: Uri) = recentDatabasesRepository.forget(uri)

    fun onRemovalDismissed() = commitRemoval()

    /** Keeps lasting access to a picked file and warns when it can only be read. */
    fun onFilePicked(uri: Uri) {
        if (!recentDatabasesRepository.keepAccess(uri)) appMessages.post(R.string.file_read_only)
    }

    private fun commitRemoval() {
        pendingRemoval?.let { recentDatabasesRepository.forget(it.first) }
        pendingRemoval = null
    }

    override fun onCleared() = commitRemoval()
}
