package com.erenium.snaplock.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.erenium.snaplock.R
import com.erenium.snaplock.ui.theme.Dimens

private const val COLLAPSED_LINES = 3

@Composable
fun DetailFieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    singleLineValue: Boolean = true,
    selectable: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var expanded by remember(value) { mutableStateOf(false) }
    var overflowing by remember(value) { mutableStateOf(false) }
    val collapsible = singleLineValue && !selectable
    val toggleLabel = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = Dimens.spaceMd)
                .size(Dimens.iconMd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.spaceSm)
                .then(
                    if (collapsible && (overflowing || expanded)) {
                        Modifier.clickable(onClickLabel = toggleLabel) { expanded = !expanded }
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val content: @Composable () -> Unit = {
                Text(
                    text = value,
                    style = valueStyle,
                    color = valueColor,
                    maxLines = if (collapsible && !expanded) COLLAPSED_LINES else Int.MAX_VALUE,
                    overflow = if (collapsible && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
                    onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow }
                )
            }
            if (selectable) SelectionContainer { content() } else content()
        }
        actions()
    }
}
