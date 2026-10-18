package com.erenium.snaplock.domain.otp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpAlgorithm(val macName: String, val uriName: String) {
    SHA1("HmacSHA1", "SHA1"),
    SHA256("HmacSHA256", "SHA256"),
    SHA512("HmacSHA512", "SHA512")
}

/** A time-based one-time password generator (RFC 6238), or its Steam Guard variant. */
data class OtpParameters(
    val secret: ByteArray,
    val period: Int = DEFAULT_PERIOD,
    val digits: Int = DEFAULT_DIGITS,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    /** Steam Guard codes: five characters from Steam's own alphabet. */
    val isSteam: Boolean = false,
    val issuer: String? = null,
    val account: String? = null
) {
    override fun equals(other: Any?): Boolean =
        other is OtpParameters && secret.contentEquals(other.secret) && period == other.period &&
            digits == other.digits && algorithm == other.algorithm && isSteam == other.isSteam &&
            issuer == other.issuer && account == other.account

    override fun hashCode(): Int =
        listOf(secret.contentHashCode(), period, digits, algorithm, isSteam, issuer, account).hashCode()

    companion object {
        const val DEFAULT_PERIOD = 30
        const val DEFAULT_DIGITS = 6
    }
}

object Totp {

    private const val STEAM_ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"
    private const val STEAM_DIGITS = 5

    fun code(parameters: OtpParameters, timeMillis: Long): String {
        val counter = (timeMillis / 1000) / parameters.period
        val mac = Mac.getInstance(parameters.algorithm.macName).apply {
            init(SecretKeySpec(parameters.secret, parameters.algorithm.macName))
        }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return if (parameters.isSteam) {
            buildString {
                var value = binary
                repeat(STEAM_DIGITS) {
                    append(STEAM_ALPHABET[value % STEAM_ALPHABET.length])
                    value /= STEAM_ALPHABET.length
                }
            }
        } else {
            var modulus = 1
            repeat(parameters.digits) { modulus *= 10 }
            (binary % modulus).toString().padStart(parameters.digits, '0')
        }
    }

    /** Seconds until the code shown at [timeMillis] changes. */
    fun secondsRemaining(parameters: OtpParameters, timeMillis: Long): Int =
        parameters.period - ((timeMillis / 1000) % parameters.period).toInt()
}

/** RFC 4648 Base32, tolerant of spaces, lower case and missing padding. */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray? {
        val cleaned = input.uppercase().filterNot { it.isWhitespace() || it == '-' }.trimEnd('=')
        if (cleaned.isEmpty() || cleaned.any { it !in ALPHABET }) return null
        val output = ArrayList<Byte>(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (char in cleaned) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(char)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output += ((buffer shr bits) and 0xff).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return output.toByteArray()
    }

    fun encode(data: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                append(ALPHABET[(buffer shr bits) and 0x1f])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
    }
}
