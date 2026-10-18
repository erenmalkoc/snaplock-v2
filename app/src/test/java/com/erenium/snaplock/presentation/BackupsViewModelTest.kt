package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.domain.model.VaultBackup
import com.erenium.snaplock.presentation.settings.BackupsViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val viewModel = BackupsViewModel(repository, DocumentFiles(context))

    // File writing happens on a real IO thread, so wait in real time.
    private suspend fun <T> eventually(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }

    @Test
    fun listsBackupsNewestFirstAndSavesTheChosenOne() = runTest {
        val older = VaultBackup("1", Instant.parse("2026-10-17T09:00:00Z"), 3)
        val newer = VaultBackup("2", Instant.parse("2026-10-18T09:00:00Z"), 3)
        repository.backups[older] = byteArrayOf(1, 2, 3)
        repository.backups[newer] = byteArrayOf(4, 5, 6)

        viewModel.onShowBackups()
        assertEquals(listOf(newer, older), viewModel.backups.filterNotNull().first())

        val target = folder.newFile("copy.kdbx")
        viewModel.onBackupChosen(older)
        assertNull(viewModel.backups.value)
        viewModel.onExportTargetPicked(Uri.fromFile(target))

        assertEquals(R.string.backup_saved, eventually { viewModel.messages.first() })
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    @Test
    fun aPickedFileWithoutAChosenBackupIsIgnored() = runTest {
        val target = folder.newFile("copy.kdbx")

        viewModel.onExportTargetPicked(Uri.fromFile(target))

        assertEquals(0, target.length())
    }
}
