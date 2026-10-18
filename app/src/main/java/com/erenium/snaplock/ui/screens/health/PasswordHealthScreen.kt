package com.erenium.snaplock.ui.screens.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.passwords.BreachCheck
import com.erenium.snaplock.presentation.health.PasswordHealthViewModel
import com.erenium.snaplock.ui.components.AppCard
import com.erenium.snaplock.ui.components.AppScaffold
import com.erenium.snaplock.ui.components.LoadingSpinner
import com.erenium.snaplock.ui.theme.Dimens
import com.erenium.snaplock.ui.theme.extendedColors
import java.util.UUID

@Composable
fun PasswordHealthScreen(
    onNavigateBack: () -> Unit,
    onEntryClick: (UUID) -> Unit,
    viewModel: PasswordHealthViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsState()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    AppScaffold(
        title = stringResource(R.string.health_title),
        onNavigateBack = onNavigateBack,
        applyContentPadding = false
    ) { contentModifier ->
        val current = report
        if (current == null) {
            LoadingSpinner(modifier = contentModifier)
            return@AppScaffold
        }
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.screenPadding)
        ) {
            item(key = "score") {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.health_score, current.score),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    LinearProgressIndicator(
                        progress = { current.score / 100f },
                        color = when {
                            current.score >= 80 -> MaterialTheme.extendedColors.success
                            current.score >= 50 -> MaterialTheme.extendedColors.warning
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spaceSm)
                    )
                    Text(
                        text = if (current.affected.isEmpty()) {
                            stringResource(R.string.health_all_good, current.checked)
                        } else {
                            pluralStringResource(R.plurals.health_affected, current.affected.size, current.affected.size, current.checked)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            when (current.breachCheck) {
                                BreachCheck.OFF -> R.string.health_breach_off
                                BreachCheck.DONE -> R.string.health_breach_done
                                BreachCheck.FAILED -> R.string.health_breach_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.spaceSm)
                    )
                }
            }
            section(R.string.health_breached, R.string.health_breached_desc, Icons.Filled.GppBad, current.breached, onEntryClick)
            section(R.string.health_weak, R.string.health_weak_desc, Icons.Filled.Warning, current.weak, onEntryClick)
            section(
                R.string.health_reused,
                R.string.health_reused_desc,
                Icons.Filled.SyncProblem,
                current.reused.flatten(),
                onEntryClick
            )
            section(R.string.health_old, R.string.health_old_desc, Icons.Filled.History, current.old, onEntryClick)
            section(R.string.health_expired, R.string.health_expired_desc, Icons.Filled.EventBusy, current.expired, onEntryClick)
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    descriptionRes: Int,
    icon: ImageVector,
    entries: List<AuditEntry>,
    onEntryClick: (UUID) -> Unit
) {
    if (entries.isEmpty()) return
    item(key = "header-$titleRes") {
        Column(modifier = Modifier.padding(top = Dimens.spaceLg, bottom = Dimens.spaceSm, start = Dimens.spaceXs)) {
            Text(
                text = "${stringResource(titleRes)} (${entries.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    items(items = entries, key = { "$titleRes-${it.uuid}" }) { entry ->
        ListItem(
            modifier = Modifier.clickable { onEntryClick(entry.uuid) },
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = entry.username?.takeIf { it.isNotBlank() }?.let { username ->
                { Text(username, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        )
    }
}
