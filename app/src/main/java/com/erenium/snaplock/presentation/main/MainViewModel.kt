package com.erenium.snaplock.presentation.main

import androidx.lifecycle.ViewModel
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    isDatabaseLockedUseCase: IsDatabaseLockedUseCase
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = isDatabaseLockedUseCase()
}
