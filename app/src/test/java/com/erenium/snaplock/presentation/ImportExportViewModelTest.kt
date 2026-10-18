package com.erenium.snaplock.presentation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.domain.importer.ExportedEntry
import com.erenium.snaplock.presentation.settings.ImportExportViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val viewModel = ImportExportViewModel(repository, DocumentFiles(context))

    // File reading happens on a real IO thread, so wait in real time.
    private suspend fun <T> eventually(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }

    private fun csvFile(text: String): Uri = Uri.fromFile(folder.newFile("export.csv").apply { writeText(text) })

    @Test
    fun recognisedFileWaitsForConfirmationThenImports() = runTest {
        viewModel.onImportFilePicked(csvFile("name,url,username,password\nGitHub,https://github.com,octocat,pw\n"))

        val pending = eventually { viewModel.pendingImport.filterNotNull().first() }
        assertEquals("Chrome", pending.source)
        assertTrue(repository.imported.isEmpty())

        viewModel.onImportConfirmed("Imported (Chrome)")

        val message = eventually { viewModel.messages.first() }
        assertEquals(R.plurals.import_done, message.resId)
        assertEquals(1, message.count)
        assertEquals("Imported (Chrome)", repository.imported.single().first)
        assertNull(viewModel.pendingImport.value)
    }

    @Test
    fun unrecognisedFileIsReported() = runTest {
        viewModel.onImportFilePicked(csvFile("name,phone\nAlice,555\n"))

        assertEquals(R.string.import_unrecognised, eventually { viewModel.messages.first() }.resId)
        assertNull(viewModel.pendingImport.value)
    }

    @Test
    fun exportWritesTheCsvToTheChosenFile() = runTest {
        repository.exportEntries = listOf(ExportedEntry("Web", "GitHub", "octocat", "pw", "https://github.com", null, null))
        val target = folder.newFile("out.csv")

        viewModel.onExportTargetPicked(Uri.fromFile(target))

        assertEquals(R.string.export_done, eventually { viewModel.messages.first() }.resId)
        assertTrue(target.readText().contains("\"Web\",\"GitHub\",\"octocat\",\"pw\""))
    }
}
