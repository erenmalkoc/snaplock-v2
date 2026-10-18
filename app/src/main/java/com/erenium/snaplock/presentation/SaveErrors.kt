package com.erenium.snaplock.presentation

import androidx.annotation.StringRes
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException

/**
 * The message for a failed save: [fallback], unless the vault file was changed elsewhere
 * in a way that cannot be merged, which needs its own explanation and way out.
 */
@StringRes
fun Throwable.saveErrorMessage(@StringRes fallback: Int = R.string.entry_save_error): Int =
    if (this is VaultChangedElsewhereException) R.string.vault_changed_elsewhere else fallback
