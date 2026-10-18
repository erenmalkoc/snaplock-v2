package com.erenium.snaplock.credentials

import android.content.Context
import android.content.pm.SigningInfo
import android.os.Bundle
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.passkeys.Passkey
import com.erenium.snaplock.domain.passkeys.StoredPasskey
import com.erenium.snaplock.testing.FakeKdbxRepository
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bank = Credential(UUID.randomUUID(), "Bank", "client", "1234", emptyList(), listOf(BANK_APP))
    private val other = Credential(UUID.randomUUID(), "Other", "me", "abcd", emptyList(), listOf("com.other.app"))
    private val repository = FakeKdbxRepository().apply { credentials.value = listOf(bank, other) }
    private val provider = PasswordProvider(context, repository)

    private fun passwordRequest(callingPackage: String = BANK_APP) = BeginGetCredentialRequest(
        listOf(BeginGetPasswordOption(emptySet(), Bundle(), "password-option")),
        CallingAppInfo(callingPackage, SigningInfo())
    )

    @Test
    fun lockedVaultOffersOnlyUnlock() = runTest {
        repository.locked.value = true

        val response = provider.beginGet(passwordRequest())

        assertEquals(1, response.authenticationActions.size)
        assertTrue(response.credentialEntries.isEmpty())
    }

    @Test
    fun unlockedVaultListsOnlyTheCallingAppsLogins() = runTest {
        repository.locked.value = false

        val response = provider.beginGet(passwordRequest())

        val entries = response.credentialEntries.filterIsInstance<PasswordCredentialEntry>()
        assertEquals(listOf("client"), entries.map { it.username.toString() })
        assertEquals(listOf("Bank"), entries.map { it.displayName.toString() })
        assertEquals(1, response.actions.size)
    }

    @Test
    fun passkeyRequestsListThePasskeysForTheSite() = runTest {
        repository.locked.value = false
        repository.passkeys += StoredPasskey(UUID.randomUUID(), "Bank", Passkey("Y3JlZA", "pem", "bank.example", "client", "dXNlcg"))
        repository.passkeys += StoredPasskey(UUID.randomUUID(), "Shop", Passkey("b3RoZXI", "pem", "shop.example", "me", "dXNlcg"))
        val passkeyOnly = BeginGetCredentialRequest(
            listOf(BeginGetPublicKeyCredentialOption(Bundle(), "passkey-option", PASSKEY_REQUEST_JSON)),
            CallingAppInfo(BANK_APP, SigningInfo())
        )

        val response = provider.beginGet(passkeyOnly)

        val entries = response.credentialEntries.filterIsInstance<PublicKeyCredentialEntry>()
        assertEquals(listOf("client"), entries.map { it.username.toString() })
        assertEquals(listOf("Bank"), entries.map { it.displayName.toString() })
        assertTrue(response.actions.isEmpty())
    }

    @Test
    fun aLockedVaultOffersUnlockForPasskeysToo() = runTest {
        repository.locked.value = true
        val passkeyOnly = BeginGetCredentialRequest(
            listOf(BeginGetPublicKeyCredentialOption(Bundle(), "passkey-option", PASSKEY_REQUEST_JSON)),
            CallingAppInfo(BANK_APP, SigningInfo())
        )

        assertEquals(1, provider.beginGet(passkeyOnly).authenticationActions.size)
    }

    @Test
    fun offersToSavePasswordsAndPasskeys() {
        val callingApp = CallingAppInfo(BANK_APP, SigningInfo())

        assertEquals(1, provider.beginCreate(BeginCreatePasswordCredentialRequest(callingApp, Bundle())).createEntries.size)
        assertEquals(
            1,
            provider.beginCreate(
                BeginCreatePublicKeyCredentialRequest(PASSKEY_CREATE_JSON, callingApp, Bundle())
            ).createEntries.size
        )
    }

    private companion object {
        const val BANK_APP = "com.bank.app"
        const val PASSKEY_REQUEST_JSON = """{"challenge":"AAAA","rpId":"bank.example"}"""
        const val PASSKEY_CREATE_JSON =
            """{"challenge":"AAAA","rp":{"id":"bank.example","name":"Bank"},"user":{"id":"AAAA","name":"client","displayName":"client"},"pubKeyCredParams":[{"type":"public-key","alg":-7}]}"""
    }
}
