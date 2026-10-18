package com.erenium.snaplock.domain.otp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TotpTest {

    // RFC 6238 appendix B: 8-digit codes for the reference secrets.
    private val sha1 = OtpParameters("12345678901234567890".toByteArray(), digits = 8)
    private val sha256 = OtpParameters(
        "12345678901234567890123456789012".toByteArray(), digits = 8, algorithm = OtpAlgorithm.SHA256
    )
    private val sha512 = OtpParameters(
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(),
        digits = 8,
        algorithm = OtpAlgorithm.SHA512
    )

    @Test
    fun matchesTheRfc6238ReferenceValues() {
        val expected = mapOf(
            59L to Triple("94287082", "46119246", "90693936"),
            1111111109L to Triple("07081804", "68084774", "25091201"),
            1111111111L to Triple("14050471", "67062674", "99943326"),
            1234567890L to Triple("89005924", "91819424", "93441116"),
            2000000000L to Triple("69279037", "90698825", "38618901"),
            20000000000L to Triple("65353130", "77737706", "47863826")
        )
        expected.forEach { (seconds, codes) ->
            val millis = seconds * 1000
            assertEquals("SHA1 @ $seconds", codes.first, Totp.code(sha1, millis))
            assertEquals("SHA256 @ $seconds", codes.second, Totp.code(sha256, millis))
            assertEquals("SHA512 @ $seconds", codes.third, Totp.code(sha512, millis))
        }
    }

    @Test
    fun sixDigitCodesKeepLeadingZeros() {
        assertEquals("081804", Totp.code(sha1.copy(digits = 6), 1111111109L * 1000))
    }

    @Test
    fun steamGuardUsesItsOwnAlphabet() {
        val steam = OtpParameters("12345678901234567890".toByteArray(), isSteam = true)

        assertEquals("PV9M4", Totp.code(steam, 59_000))
        assertEquals("VHHQY", Totp.code(steam, 1_234_567_890_000))
    }

    @Test
    fun secondsRemainingCountsDownWithinThePeriod() {
        assertEquals(30, Totp.secondsRemaining(sha1, 60_000))
        assertEquals(1, Totp.secondsRemaining(sha1, 89_999))
    }

    @Test
    fun base32FollowsRfc4648() {
        val vectors = mapOf("" to "", "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6", "foob" to "MZXW6YQ",
            "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI")
        vectors.forEach { (plain, encoded) ->
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            if (encoded.isNotEmpty()) assertArrayEquals(plain.toByteArray(), Base32.decode(encoded))
        }
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb oi======"))
        assertNull(Base32.decode("not base32!"))
    }

    @Test
    fun longSecretsRoundTrip() {
        val secret = ByteArray(64) { it.toByte() }

        assertArrayEquals(secret, Base32.decode(Base32.encode(secret)))
    }
}
