package com.erenium.snaplock.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    val elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = if (contentPadding) Modifier.padding(Dimens.spaceMd) else Modifier,
            content = content
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            content = body
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            content = body
        )
    }
}
