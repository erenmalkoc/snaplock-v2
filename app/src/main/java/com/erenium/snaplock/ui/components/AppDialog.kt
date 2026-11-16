package com.erenium.snaplock.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.erenium.snaplock.R

@Composable
fun AppDialog(
    title: String,
    confirmText: String,
    confirmIcon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = stringResource(R.string.cancel_button),
    dismissIcon: ImageVector = Icons.Filled.Close,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            AppTextButton(
                text = confirmText,
                icon = confirmIcon,
                onClick = onConfirm,
                enabled = confirmEnabled
            )
        },
        dismissButton = dismissText?.let {
            {
                AppTextButton(text = it, icon = dismissIcon, onClick = onDismiss)
            }
        }
    )
}
