package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KdbxUseCaseTest {

    private val repository = FakeKdbxRepository()

    @Test
    fun getEntriesReturnsRepositoryEntries() = runTest {
        val expected = listOf(
            Entry(
                uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                title = "Email",
                username = "eren"
            )
        )
        repository.entries.value = expected

        val actual = GetEntriesUseCase(repository)().first()

        assertEquals(expected, actual)
    }

    @Test
    fun getEntryDetailsReturnsRepositoryEntry() = runTest {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val expected = EntryDetail(
            uuid = uuid,
            title = "Server",
            username = "root",
            password = "secret",
            url = "https://example.com",
            notes = "production"
        )
        repository.entryDetails[uuid] = expected

        val result = GetEntryDetailsUseCase(repository)(uuid)

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun lockDatabaseMarksRepositoryAsLocked() = runTest {
        repository.locked.value = false

        LockDatabaseUseCase(repository)()

        assertTrue(repository.locked.value)
    }

    @Test
    fun addEntryAppendsEntry() = runTest {
        AddEntryUseCase(repository)(
            EntryFormData(title = "Bank", username = "eren", password = "secret", url = null, notes = null)
        )

        val entries = repository.getEntries().first()
        assertEquals(1, entries.size)
        assertEquals("Bank", entries.first().title)
    }

    @Test
    fun deleteEntryRemovesEntry() = runTest {
        AddEntryUseCase(repository)(
            EntryFormData(title = "Bank", username = null, password = null, url = null, notes = null)
        )
        val uuid = repository.getEntries().first().first().uuid

        DeleteEntryUseCase(repository)(uuid)

        assertTrue(repository.getEntries().first().isEmpty())
    }

    private class FakeKdbxRepository : KdbxRepository {
        val entries = MutableStateFlow<List<Entry>>(emptyList())
        val locked = MutableStateFlow(true)
        val entryDetails = mutableMapOf<UUID, EntryDetail>()

        override suspend fun unlockDatabase(uri: android.net.Uri, password: CharSequence): Result<Unit> {
            locked.value = false
            return Result.success(Unit)
        }

        override suspend fun createDatabase(uri: android.net.Uri, password: CharSequence): Result<Unit> {
            locked.value = false
            return Result.success(Unit)
        }

        override suspend fun lockDatabase() {
            locked.value = true
        }

        override fun isLocked(): Flow<Boolean> = locked

        override fun getEntries(): Flow<List<Entry>> = entries

        override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
            return entryDetails[uuid]?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException(uuid.toString()))
        }

        override suspend fun addEntry(data: EntryFormData): Result<Unit> {
            val uuid = UUID.randomUUID()
            entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes)
            entries.value = entries.value + Entry(uuid, data.title, data.username)
            return Result.success(Unit)
        }

        override suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit> {
            entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes)
            entries.value = entries.value.map {
                if (it.uuid == uuid) Entry(uuid, data.title, data.username) else it
            }
            return Result.success(Unit)
        }

        override suspend fun deleteEntry(uuid: UUID): Result<Unit> {
            entryDetails.remove(uuid)
            entries.value = entries.value.filterNot { it.uuid == uuid }
            return Result.success(Unit)
        }
    }
}
