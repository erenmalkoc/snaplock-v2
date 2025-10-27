package com.erenium.snaplock.presentation.entrylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


@Composable
fun EntryListScreen(
    onNavigateToLock: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Burası Şifre Listesi Ekranı.")

        Button(onClick = onNavigateToLock) {
            Text("Manuel Kilitle")
        }
    }
}