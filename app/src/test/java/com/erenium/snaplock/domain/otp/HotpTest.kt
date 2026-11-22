package com.erenium.snaplock.domain.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotpTest {
    private val secret = "12345678901234567890".toByteArray()

    private fun hotp(counter: Long, digits: Int = 6) = OtpParameters(secret, digits = digits, counter = counter)

    @Test
    fun matchesTheRfc4226ReferenceValues() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")

        expected.forEachIndexed { counter, code -> assertEquals("counter $counter", code, Hotp.code(hotp(counter.toLong()))) }
    }

    @Test
    fun longerCodesAreTheSameValueWithMoreDigits() {
        assertEquals("84755224", Hotp.code(hotp(0, digits = 8)))
        assertEquals("94287082", Hotp.code(hotp(1, digits = 8)))
        assertEquals("1284755224", Hotp.code(hotp(0, digits = 10)))
    }

    @Test
    fun aCounterBasedCodeDoesNotDependOnTheClock() {
        val parameters = hotp(3)

        assertEquals(Otp.code(parameters, 0), Otp.code(parameters, 1_700_000_000_000))
        assertEquals("969429", Otp.code(parameters, 123_456))
    }

    @Test
    fun aTimeBasedCodeStillFollowsTheClock() {
        val parameters = OtpParameters(secret, digits = 8)

        assertEquals("94287082", Otp.code(parameters, 59_000))
        assertFalse(parameters.isHotp)
    }

    @Test
    fun theCounterTakesPartInEquality() {
        assertEquals(hotp(1), hotp(1))
        assertTrue(hotp(1) != hotp(2))
        assertTrue(hotp(1) != OtpParameters(secret))
    }
}
