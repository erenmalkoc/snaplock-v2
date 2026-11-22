package com.erenium.snaplock.domain.otp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpParserTest {
    private val secret = "JBSWY3DPEHPK3PXP"
    private val secretBytes = Base32.decode(secret)!!

    @Test
    fun readsAnOtpAuthUri() {
        val otp = OtpParser.parse(
            "otpauth://totp/GitHub:octocat?secret=$secret&issuer=GitHub&period=60&digits=8&algorithm=SHA256"
        )!!

        assertArrayEquals(secretBytes, otp.secret)
        assertEquals(60, otp.period)
        assertEquals(8, otp.digits)
        assertEquals(OtpAlgorithm.SHA256, otp.algorithm)
        assertEquals("GitHub", otp.issuer)
        assertEquals("octocat", otp.account)
    }

    @Test
    fun defaultsWhenTheUriOnlyHasASecret() {
        val otp = OtpParser.parse("otpauth://totp/Example?secret=${secret.lowercase()}")!!

        assertEquals(30, otp.period)
        assertEquals(6, otp.digits)
        assertEquals(OtpAlgorithm.SHA1, otp.algorithm)
    }

    @Test
    fun readsSteamBareSecretsAndKeePassXcLegacyFormat() {
        assertTrue(OtpParser.parse("otpauth://totp/Steam:me?secret=$secret&encoder=steam")!!.isSteam)
        assertTrue(OtpParser.parse("steam://$secret")!!.isSteam)
        assertArrayEquals(secretBytes, OtpParser.parse(" jbsw y3dp ehpk 3pxp ")!!.secret)
        val legacy = OtpParser.parse("key=$secret&step=45&size=7&otpHashMode=sha512")!!
        assertEquals(45, legacy.period)
        assertEquals(7, legacy.digits)
        assertEquals(OtpAlgorithm.SHA512, legacy.algorithm)
    }

    @Test
    fun rejectsUnknownTypesAndGarbage() {
        assertNull(OtpParser.parse("otpauth://other/Example?secret=$secret&counter=1"))
        assertNull(OtpParser.parse("https://example.com"))
        assertNull(OtpParser.parse("otpauth://totp/Example?secret=$secret&digits=3"))
        assertNull(OtpParser.parse("otpauth://hotp/Example?secret=$secret&digits=3"))
        assertNull(OtpParser.parse("otpauth://hotp/Example?secret="))
    }

    @Test
    fun readsKeePassNativeFields() {
        val otp = OtpParser.fromFields(
            mapOf(
                "TimeOtp-Secret-Hex" to "48656c6c6f21deadbeef",
                "TimeOtp-Period" to "60",
                "TimeOtp-Length" to "8",
                "TimeOtp-Algorithm" to "HMAC-SHA-256"
            )
        )!!

        assertArrayEquals(byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x21, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()), otp.secret)
        assertEquals(60, otp.period)
        assertEquals(8, otp.digits)
        assertEquals(OtpAlgorithm.SHA256, otp.algorithm)
        assertArrayEquals(secretBytes, OtpParser.fromFields(mapOf("TimeOtp-Secret-Base32" to secret))!!.secret)
        assertArrayEquals("raw".toByteArray(), OtpParser.fromFields(mapOf("TimeOtp-Secret-Base64" to "cmF3"))!!.secret)
    }

    @Test
    fun readsLegacyTrayTotpFields() {
        val otp = OtpParser.fromFields(mapOf("TOTP Seed" to secret, "TOTP Settings" to "30;S"))!!

        assertTrue(otp.isSteam)
    }

    @Test
    fun otpFieldWinsOverOthers() {
        val otp = OtpParser.fromFields(
            mapOf("otp" to "otpauth://totp/A?secret=$secret&digits=8", "TimeOtp-Secret-Base32" to "MZXW6YTBOI")
        )!!

        assertEquals(8, otp.digits)
    }

    @Test
    fun writtenUriReadsBackTheSame() {
        val original = OtpParameters(secretBytes, 60, 8, OtpAlgorithm.SHA512, issuer = "Bank & Co", account = "me")

        assertEquals(original, OtpParser.parse(OtpParser.toUri(original)))
    }

    @Test
    fun recognisesEveryOtpField() {
        listOf(
            "otp", "TimeOtp-Secret-Base32", "TimeOtp-Period", "HmacOtp-Secret-Base32", "HmacOtp-Counter", "TOTP Seed", "TOTP Settings"
        ).forEach {
            assertTrue(it, OtpParser.isOtpField(it))
        }
        assertFalse(OtpParser.isOtpField("PIN"))
    }

    @Test
    fun readsACounterBasedUri() {
        val otp = OtpParser.parse("otpauth://hotp/Example:alice?secret=$secret&issuer=Example&counter=5&digits=8")!!

        assertTrue(otp.isHotp)
        assertEquals(5L, otp.counter)
        assertEquals(8, otp.digits)
        assertEquals("Example", otp.issuer)
        assertEquals("alice", otp.account)
    }

    @Test
    fun aCounterBasedUriWithoutACounterStartsAtZero() {
        assertEquals(0L, OtpParser.parse("otpauth://hotp/Example?secret=$secret")!!.counter)
        assertEquals(0L, OtpParser.parse("otpauth://hotp/Example?secret=$secret&counter=-4")!!.counter)
    }

    @Test
    fun aTimeBasedUriHasNoCounter() {
        assertFalse(OtpParser.parse("otpauth://totp/Example?secret=$secret&counter=9")!!.isHotp)
    }

    @Test
    fun writtenCounterBasedUriReadsBackTheSame() {
        val original = OtpParameters(secretBytes, digits = 8, algorithm = OtpAlgorithm.SHA256, issuer = "Bank", account = "me", counter = 41)
        val uri = OtpParser.toUri(original)

        assertTrue(uri, uri.startsWith("otpauth://hotp/"))
        assertTrue(uri, "counter=41" in uri && "period=" !in uri)
        assertEquals(original, OtpParser.parse(uri))
    }

    @Test
    fun readsKeePassHmacOtpFields() {
        val otp = OtpParser.fromFields(mapOf("HmacOtp-Secret-Base32" to secret, "HmacOtp-Counter" to " 7 "))!!

        assertArrayEquals(secretBytes, otp.secret)
        assertEquals(7L, otp.counter)
        assertEquals(0L, OtpParser.fromFields(mapOf("HmacOtp-Secret-Base32" to secret))!!.counter)
    }

    @Test
    fun readsTheOtherKeePassHmacOtpSecretForms() {
        assertArrayEquals("foobar".toByteArray(), OtpParser.fromFields(mapOf("HmacOtp-Secret" to "foobar"))!!.secret)
        assertArrayEquals("foobar".toByteArray(), OtpParser.fromFields(mapOf("HmacOtp-Secret-Hex" to "666f6f626172"))!!.secret)
        assertArrayEquals("foobar".toByteArray(), OtpParser.fromFields(mapOf("HmacOtp-Secret-Base64" to "Zm9vYmFy"))!!.secret)
    }

    @Test
    fun advancingAUriKeepsEveryOtherParameter() {
        val uri = "otpauth://hotp/Example:alice?secret=$secret&issuer=Example&counter=5&image=https%3A%2F%2Fexample.com%2Fa.png"
        val advance = OtpParser.advanceCounter(mapOf("otp" to uri))!!

        assertEquals("otp", advance.field)
        assertEquals(6L, advance.counter)
        assertEquals(
            "otpauth://hotp/Example:alice?secret=$secret&issuer=Example&image=https%3A%2F%2Fexample.com%2Fa.png&counter=6",
            advance.value
        )
        assertEquals(6L, OtpParser.parse(advance.value)!!.counter)
    }

    @Test
    fun advancingAUriWithoutACounterAddsOne() {
        val advance = OtpParser.advanceCounter(mapOf("otp" to "otpauth://hotp/Example?secret=$secret"))!!

        assertEquals(1L, advance.counter)
        assertTrue(advance.value, advance.value.endsWith("&counter=1") || advance.value.endsWith("?counter=1"))
    }

    @Test
    fun advancingKeePassHmacOtpFieldsWritesTheCounterField() {
        val advance = OtpParser.advanceCounter(mapOf("HmacOtp-Secret-Base32" to secret, "HmacOtp-Counter" to "7"))!!

        assertEquals("HmacOtp-Counter", advance.field)
        assertEquals("8", advance.value)
        assertEquals(8L, advance.counter)
    }

    @Test
    fun advancingOnlyAppliesToCounterBasedCodes() {
        assertNull(OtpParser.advanceCounter(mapOf("otp" to "otpauth://totp/Example?secret=$secret")))
        assertNull(OtpParser.advanceCounter(mapOf("TimeOtp-Secret-Base32" to secret)))
        assertNull(OtpParser.advanceCounter(emptyMap()))
    }

    @Test
    fun aTimeBasedUriWinsOverKeePassHmacOtpFields() {
        val fields = mapOf("otp" to "otpauth://totp/Example?secret=$secret", "HmacOtp-Secret-Base32" to secret)

        assertFalse(OtpParser.fromFields(fields)!!.isHotp)
        assertNull(OtpParser.advanceCounter(fields))
    }
}
