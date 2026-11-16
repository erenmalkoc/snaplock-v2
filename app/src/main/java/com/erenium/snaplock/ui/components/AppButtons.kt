package com.erenium.snaplock.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.erenium.snaplock.ui.theme.Dimens

@Composable
private fun RowScope.ButtonLabel(text: String, icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = Dimens.buttonHeight)
    ) {
        ButtonLabel(text, icon)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = Dimens.buttonHeight)
    ) {
        ButtonLabel(text, icon)
    }
}

@Composable
fun AppTextButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        ButtonLabel(text, icon)
    }
}
