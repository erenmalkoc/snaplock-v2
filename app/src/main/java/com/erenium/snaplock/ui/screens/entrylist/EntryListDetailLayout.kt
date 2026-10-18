package com.erenium.snaplock.ui.screens.entrylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.erenium.snaplock.R
import com.erenium.snaplock.presentation.entrydetail.EntryDetailViewModel
import com.erenium.snaplock.ui.components.EmptyState
import com.erenium.snaplock.ui.screens.entrydetail.EntryDetailScreen
import java.util.UUID

/** Wide enough for the list and an entry side by side: tablets and unfolded foldables. */
private val TwoPaneMinWidth = 720.dp

@Composable
fun isTwoPaneWidth(): Boolean {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return width >= TwoPaneMinWidth
}

/**
 * The entry list with the selected entry beside it. Phones keep opening entries as their
 * own screen; this is only used when [isTwoPaneWidth] holds.
 */
@Composable
fun EntryListDetailLayout(
    selectedEntry: UUID?,
    onSelectEntry: (UUID?) -> Unit,
    onNavigateToEdit: (UUID) -> Unit,
    list: @Composable (onEntryClick: (UUID) -> Unit) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(LIST_WEIGHT).fillMaxHeight()) {
            list { onSelectEntry(it) }
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f - LIST_WEIGHT).fillMaxHeight()) {
            if (selectedEntry == null) {
                EmptyState(message = stringResource(R.string.entry_pick_one), icon = Icons.Outlined.Key)
            } else {
                // One detail model for the pane; it follows the selection.
                val viewModel: EntryDetailViewModel = hiltViewModel(key = "entry-detail-pane")
                LaunchedEffect(selectedEntry) { viewModel.show(selectedEntry) }
                EntryDetailScreen(
                    onNavigateBack = { onSelectEntry(null) },
                    onNavigateToEdit = onNavigateToEdit,
                    viewModel = viewModel
                )
            }
        }
    }
}

private const val LIST_WEIGHT = 0.42f
