package com.erenium.snaplock.ui.screens.unlock

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.erenium.snaplock.presentation.unlock.UnlockViewModel

@Composable
fun UnlockScreen(
    uri: Uri,
    viewModel: UnlockViewModel = hiltViewModel(),
    onUnlockSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isUnlockSuccessful) {
        if (state.isUnlockSuccessful) {
            onUnlockSuccess()
        }
    }

    Column(
    ) {
    }
}