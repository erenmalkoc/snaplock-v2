package com.erenium.snaplock.domain.passkeys

import java.security.Signature
import java.security.interfaces.ECPublicKey
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAuthnTest {

    private fun hex(bytes: ByteArray) = bytes.toByteString().hex()

    @Test
    fun cborMatchesTheRfc8949Examples() {
        assertEquals("00", hex(Cbor.encode(0)))
        assertEquals("17", hex(Cbor.encode(23)))
        assertEquals("1818", hex(Cbor.encode(24)))
        assertEquals("1903e8", hex(Cbor.encode(1000)))
        assertEquals("1a000f4240", hex(Cbor.encode(1_000_000)))
        assertEquals("20", hex(Cbor.encode(-1)))
        assertEquals("3863", hex(Cbor.encode(-100)))
        assertEquals("390100", hex(Cbor.encode(-257)))
        assertEquals("4401020304", hex(Cbor.encode(byteArrayOf(1, 2, 3, 4))))
        assertEquals("6449455446", hex(Cbor.encode("IETF")))
        assertEquals("a0", hex(Cbor.encode(emptyMap<Int, Int>())))
        assertEquals("a201020304", hex(Cbor.encode(linkedMapOf(1 to 2, 3 to 4))))
        assertEquals("8301820203820405", hex(Cbor.encode(listOf(1, listOf(2, 3), listOf(4, 5)))))
    }

    @Test
    fun assertionDataHasTheRpHashFlagsAndZeroCounter() {
        val data = WebAuthn.authenticatorData("example.com", WebAuthn.FLAG_USER_PRESENT or WebAuthn.FLAG_USER_VERIFIED)

        assertEquals(37, data.size)
        assertArrayEquals("example.com".encodeUtf8().sha256().toByteArray(), data.copyOfRange(0, 32))
        assertEquals(0x05, data[32].toInt())
        assertArrayEquals(ByteArray(4), data.copyOfRange(33, 37))
    }

    @Test
    fun registrationDataCarriesTheCredentialAndItsCoseKey() {
        val keys = WebAuthn.newKeyPair()
        val id = WebAuthn.newCredentialId()
        val publicKey = keys.public as ECPublicKey

        val data = WebAuthn.authenticatorData("example.com", 0x5D, AttestedCredential(id, publicKey))

        assertEquals(0x5D, data[32].toInt())
        assertArrayEquals(ByteArray(16), data.copyOfRange(37, 53))
        assertEquals(id.size, (data[53].toInt() shl 8) or data[54].toInt())
        assertArrayEquals(id, data.copyOfRange(55, 55 + id.size))
        val cose = data.copyOfRange(55 + id.size, data.size)
        assertArrayEquals(WebAuthn.coseKey(publicKey), cose)
        // kty 2 (EC2), alg -7 (ES256), crv 1 (P-256), then x and y as 32-byte strings.
        assertEquals("a5010203262001215820", hex(cose.copyOfRange(0, 10)))
        assertEquals(77, cose.size)
    }

    @Test
    fun noneAttestationIsTheCanonicalThreeKeyMap() {
        val attestation = WebAuthn.attestationObject(byteArrayOf(9))

        assertEquals("a363666d74646e6f6e656761747453746d74a06861757468446174614109", hex(attestation))
    }

    @Test
    fun signaturesVerifyWithThePublicKeyAfterAPemRoundTrip() {
        val keys = WebAuthn.newKeyPair()
        val pem = WebAuthn.toPem(keys.private)
        val authData = WebAuthn.authenticatorData("example.com", 0x05)
        val clientDataHash = WebAuthn.sha256("{\"type\":\"webauthn.get\"}".toByteArray())

        val signature = WebAuthn.sign(WebAuthn.privateKeyFromPem(pem), authData, clientDataHash)

        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----\n"))
        assertTrue(pem.lines().drop(1).dropLast(1).all { it.length <= 64 })
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(keys.public)
            update(authData)
            update(clientDataHash)
            verify(signature)
        }
        assertTrue(verified)
    }

    @Test
    fun base64UrlHasNoPaddingAndReadsBothAlphabets() {
        val bytes = "fbff".decodeHex().toByteArray()

        assertEquals("-_8", WebAuthn.base64Url(bytes))
        assertArrayEquals(bytes, WebAuthn.fromBase64Url("-_8"))
        assertArrayEquals(bytes, WebAuthn.fromBase64Url("+/8="))
    }

    @Test
    fun flagsFollowTheBackupState() {
        val synced = Passkey("id", "pem", "example.com", "me", "handle")

        assertEquals(0x1D, WebAuthn.flags(userVerified = true, passkey = synced))
        assertEquals(0x5D, WebAuthn.flags(userVerified = true, passkey = synced, attested = true))
        assertEquals(0x01, WebAuthn.flags(userVerified = false, passkey = synced.copy(backupEligible = false, backupState = false)))
    }
}
