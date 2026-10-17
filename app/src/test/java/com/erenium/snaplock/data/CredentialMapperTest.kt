package com.erenium.snaplock.data

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import com.erenium.snaplock.data.repository.toCredential
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialMapperTest {

    @Test
    fun readsLoginFieldsAndEveryUrlField() {
        val credential = entry(
            "Title" to plain("GitHub"),
            "UserName" to plain("octocat"),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("s3cret")),
            "URL" to plain("https://github.com/login"),
            "KP2A_URL" to plain("https://gist.github.com"),
            "KP2A_URL_2" to plain("https://api.github.com")
        ).toCredential(UNTITLED)

        assertEquals("GitHub", credential.title)
        assertEquals("octocat", credential.username)
        assertEquals("s3cret", credential.password)
        assertEquals(
            setOf("https://github.com/login", "https://gist.github.com", "https://api.github.com"),
            credential.urls.toSet()
        )
        assertEquals(emptyList<String>(), credential.androidApps)
    }

    @Test
    fun collectsAppsFromKeePassDxFieldsAndAndroidAppUrls() {
        val credential = entry(
            "Title" to plain("Bank"),
            "URL" to plain("androidapp://com.bank.app"),
            "KP2A_URL_1" to plain("https://bank.example"),
            "AndroidApp" to plain("com.bank.business"),
            "AndroidApp_1" to plain(" com.bank.app ")
        ).toCredential(UNTITLED)

        assertEquals(listOf("https://bank.example"), credential.urls)
        assertEquals(setOf("com.bank.app", "com.bank.business"), credential.androidApps.toSet())
        assertEquals(2, credential.androidApps.size)
    }

    @Test
    fun ignoresFieldsThatOnlyLookLikeLinkFields() {
        val credential = entry(
            "AndroidApp_" to plain("com.not.numbered"),
            "AndroidAppX" to plain("com.not.related"),
            "KP2A_URL_a" to plain("https://not.numbered")
        ).toCredential(UNTITLED)

        assertEquals(emptyList<String>(), credential.androidApps)
        assertEquals(emptyList<String>(), credential.urls)
    }

    @Test
    fun usesFallbackTitleAndNullForEmptyFields() {
        val credential = entry(
            "Title" to plain(" "),
            "UserName" to plain(""),
            "Password" to plain("")
        ).toCredential(UNTITLED)

        assertEquals(UNTITLED, credential.title)
        assertNull(credential.username)
        assertNull(credential.password)
    }

    private fun entry(vararg fields: Pair<String, EntryValue>) =
        Entry(uuid = UUID.randomUUID(), fields = EntryFields(mapOf(*fields)))

    private fun plain(value: String) = EntryValue.Plain(value)

    private companion object {
        const val UNTITLED = "Untitled"
    }
}
