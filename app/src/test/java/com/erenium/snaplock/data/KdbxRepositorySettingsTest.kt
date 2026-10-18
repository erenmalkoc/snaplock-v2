package com.erenium.snaplock.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.header.KdfParameters
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.VaultBackups
import com.erenium.snaplock.data.datasource.local.decodeFromByteArray
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.KdbxRepositoryImpl
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KdbxRepositorySettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = KdbxRepositoryImpl(
        context, KdbxLocalDataSource(context, VaultBackups(context)), SessionCache(), RecentDatabasesPrefs(context)
    )
    private lateinit var file: File

    private suspend fun openNewVault() {
        file = File(folder.root, "vault.kdbx").apply { createNewFile() }
        repository.createDatabase(Uri.fromFile(file), PASSWORD).getOrThrow()
    }

    private fun reopen(): KeePassDatabase =
        decodeFromByteArray(file.readBytes(), Credentials.from(EncryptedValue.fromString(PASSWORD)))

    @Test
    fun newVaultsUseKeePassDefaults() = runTest {
        openNewVault()

        val settings = repository.getDatabaseSettings()!!

        assertTrue(settings.recycleBinEnabled)
        assertEquals(EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.ARGON2D, 8, 32L * 1024 * 1024, 2), settings.encryption)
    }

    @Test
    fun nameRecycleBinAndHistoryAreSavedInTheFile() = runTest {
        openNewVault()
        val current = repository.getDatabaseSettings()!!

        repository.updateDatabaseSettings(current.copy(name = " Family ", recycleBinEnabled = false, historyMaxItems = 3)).getOrThrow()

        val meta = reopen().content.meta
        assertEquals("Family", meta.name)
        assertFalse(meta.recycleBinEnabled)
        assertEquals(3, meta.historyMaxItems)
    }

    @Test
    fun cipherAndKeyDerivationChangeTogether() = runTest {
        openNewVault()
        val current = repository.getDatabaseSettings()!!
        val chaCha = EncryptionSettings(VaultCipher.CHACHA20, KdfAlgorithm.ARGON2ID, 3, 16L * 1024 * 1024, 1)

        repository.updateDatabaseSettings(current.copy(encryption = chaCha)).getOrThrow()

        val header = reopen().header as app.keemobile.kotpass.database.header.DatabaseHeader.Ver4x
        assertEquals(BaseCiphers.ChaCha20.uuid, header.cipherId)
        val kdf = header.kdfParameters as KdfParameters.Argon2
        assertEquals(KdfParameters.Argon2.Variant.Argon2id, kdf.variant)
        assertEquals(3uL, kdf.iterations)
        assertEquals(16uL * 1024u * 1024u, kdf.memory)
        assertEquals(chaCha, repository.getDatabaseSettings()!!.encryption)
    }

    @Test
    fun aesKdfRoundsAreKept() = runTest {
        openNewVault()
        val current = repository.getDatabaseSettings()!!
        val aes = EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.AES_KDF, 60_000, 0, 0)

        repository.updateDatabaseSettings(current.copy(encryption = aes)).getOrThrow()

        val header = reopen().header as app.keemobile.kotpass.database.header.DatabaseHeader.Ver4x
        assertEquals(60_000uL, (header.kdfParameters as KdfParameters.Aes).rounds)
    }

    @Test
    fun unusableArgon2SettingsAreRejectedBeforeSaving() = runTest {
        openNewVault()
        val current = repository.getDatabaseSettings()!!
        val before = file.readBytes()

        val tooLittleMemory = EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.ARGON2D, 2, 4 * 1024, 2)

        assertTrue(repository.updateDatabaseSettings(current.copy(encryption = tooLittleMemory)).isFailure)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    private companion object {
        const val PASSWORD = "master"
    }
}
