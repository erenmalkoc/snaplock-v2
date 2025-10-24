package com.erenium.snaplock.ui.screens.selectfile

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erenium.snaplock.R

/**
 * This Composable allows the user to select a database file.
 * @param onFileSelected Triggered when a file is successfully selected.
 */
@Composable
fun SelectFileScreen(
    onFileSelected: (Uri) -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                Log.d("SelectFileScreen", "Selected file: $uri")
                onFileSelected(uri)
            } else {
                Log.d("SelectFileScreen", "File not selected.")
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.select_file_description))
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        ) {
            Text(text = stringResource(id = R.string.select_file_button))
        }
    }
}