package com.erenium.snaplock.domain.passkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyTest {

    private val passkey = Passkey("Y3JlZA", "-----BEGIN PRIVATE KEY-----\nAA\n-----END PRIVATE KEY-----", "github.com", "octocat", "dXNlcg")

    @Test
    fun fieldsRoundTripWithTheSecretsProtected() {
        val fields = passkey.toFields()

        assertEquals(passkey, Passkey.fromFields(fields.associate { it.name to it.value }))
        assertEquals(
            setOf(Passkey.FIELD_PRIVATE_KEY_PEM, Passkey.FIELD_USER_HANDLE),
            fields.filter { it.isProtected }.map { it.name }.toSet()
        )
        assertTrue(fields.all { Passkey.isPasskeyField(it.name) })
    }

    @Test
    fun keePassXcPasskeysWithoutFlagsCountAsSynced() {
        val fromOldKeePassXc = passkey.toFields()
            .filterNot { it.name == Passkey.FIELD_FLAG_BE || it.name == Passkey.FIELD_FLAG_BS }
            .associate { it.name to it.value }

        val read = Passkey.fromFields(fromOldKeePassXc)!!

        assertTrue(read.backupEligible && read.backupState)
        assertFalse(Passkey.fromFields(fromOldKeePassXc + (Passkey.FIELD_FLAG_BS to "0"))!!.backupState)
    }

    @Test
    fun entriesWithoutAKeyHaveNoPasskey() {
        val fields = passkey.toFields().associate { it.name to it.value }

        assertNull(Passkey.fromFields(fields - Passkey.FIELD_PRIVATE_KEY_PEM))
        assertNull(Passkey.fromFields(fields + (Passkey.FIELD_RELYING_PARTY to " ")))
        assertNull(Passkey.fromFields(emptyMap()))
        assertFalse(Passkey.isPasskeyField("KPEX_OTHER"))
    }
}
