package com.erenium.snaplock.data

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.findEntry
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.errors.CryptoError
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.data.datasource.local.decodeFromByteArray
import com.erenium.snaplock.data.datasource.local.encodeToByteArray
import java.io.ByteArrayInputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saves databases the way the app does ([encodeToByteArray]) and opens them again with
 * kotpass, so a kotpass upgrade or fork that breaks the KDBX round trip fails here first.
 */
class KotpassRoundTripTest {

    @Test
    fun savedDatabaseOpensWithSamePassword() {
        val bytes = newDatabase().encodeToByteArray()

        val reopened = open(bytes, MASTER_PASSWORD)

        assertEquals(ROOT_NAME, reopened.content.group.name)
        assertEquals(GENERATOR, reopened.content.meta.generator)
        assertEntry(reopened, EMAIL_UUID, EMAIL_FIELDS)
    }

    @Test
    fun savedDatabaseRejectsWrongPassword() {
        val bytes = newDatabase().encodeToByteArray()

        assertThrows(CryptoError.InvalidKey::class.java) {
            open(bytes, "wrong password")
        }
    }

    @Test
    fun editedDatabaseKeepsEveryEntryAfterSave() {
        val opened = open(newDatabase().encodeToByteArray(), MASTER_PASSWORD)
        val edited = opened
            .modifyEntry(EMAIL_UUID) {
                copy(fields = fields.plus(PASSWORD to protectedValue("n3w-pässwörd")))
            }
            .modifyGroup(opened.content.group.uuid) {
                copy(entries = entries + Entry(uuid = BANK_UUID, fields = EntryFields(BANK_FIELDS)))
            }

        val reopened = open(edited.encodeToByteArray(), MASTER_PASSWORD)

        assertEntry(reopened, EMAIL_UUID, EMAIL_FIELDS + (PASSWORD to protectedValue("n3w-pässwörd")))
        assertEntry(reopened, BANK_UUID, BANK_FIELDS)
    }

    @Test
    fun twofishVaultsOpenAndSaveAsTwofish() {
        val database = newDatabase() as KeePassDatabase.Ver4x
        val twofish = database.copy(header = database.header.copy(cipherId = TwofishCipher.uuid))

        val reopened = decodeFromByteArray(twofish.encodeToByteArray(), credentials(MASTER_PASSWORD))

        assertEquals(TwofishCipher.uuid, reopened.header.cipherId)
        assertEntry(reopened, EMAIL_UUID, EMAIL_FIELDS)
    }

    private fun newDatabase(): KeePassDatabase {
        val database = KeePassDatabase.Ver4x.create(
            rootName = ROOT_NAME,
            meta = Meta(generator = GENERATOR, name = ROOT_NAME),
            credentials = credentials(MASTER_PASSWORD)
        )
        return database.modifyGroup(database.content.group.uuid) {
            copy(entries = entries + Entry(uuid = EMAIL_UUID, fields = EntryFields(EMAIL_FIELDS)))
        }
    }

    private fun open(bytes: ByteArray, password: String): KeePassDatabase =
        KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials(password))

    private fun assertEntry(database: KeePassDatabase, uuid: UUID, expected: Map<String, EntryValue>) {
        val entry = database.findEntry { it.uuid == uuid }?.second
        assertNotNull("entry $uuid is missing", entry)
        val fields = entry!!.fields
        assertEquals(expected.mapValues { it.value.content }, fields.entries.associate { it.key to it.value.content })
        expected.filterValues { it is EntryValue.Encrypted }.keys.forEach { key ->
            assertTrue("$key must stay protected", fields[key] is EntryValue.Encrypted)
        }
    }

    private companion object {
        const val ROOT_NAME = "Passwords"
        const val GENERATOR = "Snaplock"
        const val MASTER_PASSWORD = "correct horse battery staple ğüşiöç"
        const val PASSWORD = "Password"

        val EMAIL_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val BANK_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

        val EMAIL_FIELDS: Map<String, EntryValue> = mapOf(
            "Title" to EntryValue.Plain("Email"),
            "UserName" to EntryValue.Plain("eren"),
            PASSWORD to protectedValue("s3cret-Pässwörd!"),
            "URL" to EntryValue.Plain("https://example.com"),
            "Notes" to EntryValue.Plain("line one\nline two")
        )
        val BANK_FIELDS: Map<String, EntryValue> = mapOf(
            "Title" to EntryValue.Plain("Bank"),
            PASSWORD to protectedValue("1234")
        )

        fun protectedValue(value: String): EntryValue = EntryValue.Encrypted(EncryptedValue.fromString(value))

        fun credentials(password: String): Credentials =
            Credentials.from(EncryptedValue.fromString(password))
    }
}
