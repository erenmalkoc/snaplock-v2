package com.erenium.snaplock.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.erenium.snaplock.R

private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.auto_lock_immediate)
    seconds % SECONDS_PER_MINUTE == 0 -> {
        val minutes = seconds / SECONDS_PER_MINUTE
        pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
    }
    else -> pluralStringResource(R.plurals.duration_seconds, seconds, seconds)
}
