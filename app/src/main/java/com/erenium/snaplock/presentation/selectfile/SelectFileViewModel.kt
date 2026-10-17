package com.erenium.snaplock.presentation.selectfile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.RecentDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SelectFileViewModel @Inject constructor(
    private val recentDatabasesRepository: RecentDatabasesRepository
) : ViewModel() {

    val recentDatabases: StateFlow<List<RecentDatabase>> =
        recentDatabasesRepository.recentDatabases.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onRemoveRecent(uri: Uri) {
        recentDatabasesRepository.remove(uri)
    }
}
