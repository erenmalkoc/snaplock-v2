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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.erenium.snaplock.R
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.PrimaryButton
import com.erenium.snaplock.ui.theme.Dimens

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

    AppScaffold(title = stringResource(R.string.select_file_title)) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.select_file_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spaceLg))
            PrimaryButton(
                text = stringResource(id = R.string.select_file_button),
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                fillMaxWidth = false
            )
        }
    }
}
