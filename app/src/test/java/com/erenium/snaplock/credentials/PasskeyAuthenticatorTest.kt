package com.erenium.snaplock.credentials

import com.erenium.snaplock.domain.passkeys.WebAuthn
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Credential Manager passes WebAuthn JSON; org.json needs Robolectric. */
@RunWith(RobolectricTestRunner::class)
class PasskeyAuthenticatorTest {

    private val creationJson = """
        {"rp":{"id":"webauthn.io","name":"WebAuthn.io"},
         "user":{"id":"dXNlci0x","name":"octocat","displayName":"The Octocat"},
         "challenge":"Y2hhbGxlbmdlLTE",
         "pubKeyCredParams":[{"type":"public-key","alg":-8},{"type":"public-key","alg":-7},{"type":"public-key","alg":-257}],
         "excludeCredentials":[],
         "authenticatorSelection":{"residentKey":"required","userVerification":"preferred"}}
    """.trimIndent()

    private val app = PasskeyCaller(origin = "android:apk-key-hash:abc_-123", packageName = "com.example.app")

    private fun decodeJson(base64Url: String) = JSONObject(String(WebAuthn.fromBase64Url(base64Url)!!, Charsets.UTF_8))

    @Test
    fun registrationReturnsAPasskeyAndAResponseTheSiteCanCheck() {
        val request = PasskeyAuthenticator.parseCreation(creationJson)!!

        val registration = PasskeyAuthenticator.register(request, app, userVerified = true).getOrThrow()

        val passkey = registration.passkey
        assertEquals("webauthn.io", passkey.relyingParty)
        assertEquals("octocat", passkey.username)
        assertEquals("dXNlci0x", passkey.userHandle)
        val json = JSONObject(registration.responseJson)
        assertEquals(passkey.credentialId, json.getString("id"))
        assertEquals("public-key", json.getString("type"))
        val response = json.getJSONObject("response")
        assertEquals(-7, response.getInt("publicKeyAlgorithm"))
        val clientData = decodeJson(response.getString("clientDataJSON"))
        assertEquals("webauthn.create", clientData.getString("type"))
        assertEquals("Y2hhbGxlbmdlLTE", clientData.getString("challenge"))
        assertEquals(app.origin, clientData.getString("origin"))
        assertEquals("com.example.app", clientData.getString("androidPackageName"))
        val authData = WebAuthn.fromBase64Url(response.getString("authenticatorData"))!!
        assertEquals(WebAuthn.FLAG_ATTESTED_DATA, authData[32].toInt() and WebAuthn.FLAG_ATTESTED_DATA)
        assertTrue(json.getJSONObject("clientExtensionResults").getJSONObject("credProps").getBoolean("rk"))
    }

    @Test
    fun sitesWithoutEs256AreRefused() {
        val rsaOnly = creationJson.replace("""{"type":"public-key","alg":-7},""", "").replace("""{"type":"public-key","alg":-8},""", "")
        val request = PasskeyAuthenticator.parseCreation(rsaOnly)!!

        assertTrue(PasskeyAuthenticator.register(request, app, userVerified = true).isFailure)
    }

    @Test
    fun anAssertionVerifiesWithTheRegisteredPublicKey() {
        val registration = PasskeyAuthenticator.register(PasskeyAuthenticator.parseCreation(creationJson)!!, app, true).getOrThrow()
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(WebAuthn.fromBase64Url(JSONObject(registration.responseJson).getJSONObject("response").getString("publicKey")))
        )
        val request = PasskeyAuthenticator.parseAssertion(
            """{"rpId":"webauthn.io","challenge":"c2lnbi1pbg==","allowCredentials":[{"type":"public-key","id":"${registration.passkey.credentialId}"}],"userVerification":"required"}"""
        )!!
        assertTrue(PasskeyAuthenticator.matches(request, registration.passkey))

        val json = JSONObject(PasskeyAuthenticator.assert(request, registration.passkey, app, userVerified = true).getOrThrow())

        val response = json.getJSONObject("response")
        val clientDataJson = WebAuthn.fromBase64Url(response.getString("clientDataJSON"))!!
        val authData = WebAuthn.fromBase64Url(response.getString("authenticatorData"))!!
        assertEquals("webauthn.get", decodeJson(response.getString("clientDataJSON")).getString("type"))
        assertEquals("c2lnbi1pbg", decodeJson(response.getString("clientDataJSON")).getString("challenge"))
        assertEquals(WebAuthn.FLAG_USER_VERIFIED, authData[32].toInt() and WebAuthn.FLAG_USER_VERIFIED)
        assertEquals("dXNlci0x", response.getString("userHandle"))
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(authData)
            update(WebAuthn.sha256(clientDataJson))
            verify(WebAuthn.fromBase64Url(response.getString("signature")))
        }
        assertTrue(verified)
    }

    @Test
    fun privilegedBrowsersSignTheirOwnClientDataHash() {
        val registration = PasskeyAuthenticator.register(PasskeyAuthenticator.parseCreation(creationJson)!!, app, true).getOrThrow()
        val browserHash = WebAuthn.sha256("browser-built client data".toByteArray())
        val browser = PasskeyCaller("https://webauthn.io", "com.android.chrome", browserHash)
        val request = PasskeyAuthenticator.parseAssertion("""{"rpId":"webauthn.io","challenge":"YQ"}""")!!

        val response = JSONObject(PasskeyAuthenticator.assert(request, registration.passkey, browser, true).getOrThrow())
            .getJSONObject("response")

        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(WebAuthn.fromBase64Url(JSONObject(registration.responseJson).getJSONObject("response").getString("publicKey")))
        )
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(WebAuthn.fromBase64Url(response.getString("authenticatorData")))
            update(browserHash)
            verify(WebAuthn.fromBase64Url(response.getString("signature")))
        }
        assertTrue(verified)
    }

    @Test
    fun matchingFollowsTheSiteAndItsAllowList() {
        val registration = PasskeyAuthenticator.register(PasskeyAuthenticator.parseCreation(creationJson)!!, app, true).getOrThrow()
        val passkey = registration.passkey
        val padded = WebAuthn.base64Url(WebAuthn.fromBase64Url(passkey.credentialId)!!) + "="

        assertTrue(PasskeyAuthenticator.matches(PasskeyAuthenticator.parseAssertion("""{"rpId":"WebAuthn.io","challenge":"YQ"}""")!!, passkey))
        assertFalse(PasskeyAuthenticator.matches(PasskeyAuthenticator.parseAssertion("""{"rpId":"example.com","challenge":"YQ"}""")!!, passkey))
        assertFalse(
            PasskeyAuthenticator.matches(
                PasskeyAuthenticator.parseAssertion("""{"rpId":"webauthn.io","challenge":"YQ","allowCredentials":[{"id":"b3RoZXI"}]}""")!!,
                passkey
            )
        )
        val excluding = PasskeyAuthenticator.parseCreation(creationJson.replace("\"excludeCredentials\":[]", "\"excludeCredentials\":[{\"id\":\"$padded\"}]"))!!
        assertTrue(PasskeyAuthenticator.isExcluded(excluding, listOf(passkey)))
    }

    @Test
    fun brokenRequestsAreRejected() {
        assertEquals(null, PasskeyAuthenticator.parseCreation("{}"))
        assertEquals(null, PasskeyAuthenticator.parseAssertion("not json"))
        assertNotNull(PasskeyAuthenticator.parseAssertion("""{"rpId":"a.b","challenge":"YQ"}"""))
        assertArrayEquals(WebAuthn.fromBase64Url("YQ"), "a".toByteArray())
    }
}
