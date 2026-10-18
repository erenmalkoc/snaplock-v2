package com.erenium.snaplock.presentation

import com.erenium.snaplock.R
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveErrorsTest {

    @Test
    fun aVaultChangedElsewhereGetsItsOwnMessage() {
        val error = VaultChangedElsewhereException(IOException("wrong key"))

        assertEquals(R.string.vault_changed_elsewhere, error.saveErrorMessage())
        assertEquals(R.string.vault_changed_elsewhere, error.saveErrorMessage(R.string.attachment_error))
    }

    @Test
    fun otherFailuresKeepTheMessageOfTheAction() {
        assertEquals(R.string.entry_save_error, IOException().saveErrorMessage())
        assertEquals(R.string.entry_delete_error, IOException().saveErrorMessage(R.string.entry_delete_error))
    }
}
