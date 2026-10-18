package com.erenium.snaplock.domain.otp

import okio.ByteString.Companion.decodeBase64
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Reads TOTP settings from the fields KeePass clients use and writes them back as an
 * otpauth:// URI in the "otp" field, which KeePassXC, KeePassDX and Keepass2Android read.
 */
object OtpParser {

    const val FIELD_OTP = "otp"

    // KeePass 2.47+ native fields.
    private const val TIME_OTP = "TimeOtp-"
    private const val TIME_OTP_BASE32 = "TimeOtp-Secret-Base32"
    private const val TIME_OTP_HEX = "TimeOtp-Secret-Hex"
    private const val TIME_OTP_BASE64 = "TimeOtp-Secret-Base64"
    private const val TIME_OTP_TEXT = "TimeOtp-Secret"
    private const val TIME_OTP_PERIOD = "TimeOtp-Period"
    private const val TIME_OTP_LENGTH = "TimeOtp-Length"
    private const val TIME_OTP_ALGORITHM = "TimeOtp-Algorithm"

    // KeeOtp / TrayTOTP, still written by older KeePassXC versions.
    private const val LEGACY_SEED = "TOTP Seed"
    private const val LEGACY_SETTINGS = "TOTP Settings"

    /** True for every field that holds TOTP settings, so the editor can leave them out. */
    fun isOtpField(name: String): Boolean =
        name == FIELD_OTP || name.startsWith(TIME_OTP) || name == LEGACY_SEED || name == LEGACY_SETTINGS

    fun fromFields(fields: Map<String, String>): OtpParameters? {
        fields[FIELD_OTP]?.let { value -> parse(value)?.let { return it } }
        timeOtp(fields)?.let { return it }
        return legacy(fields)
    }

    /** Accepts an otpauth:// or steam:// URI, KeePassXC's old "key=...&step=..." form, or a bare Base32 secret. */
    fun parse(value: String): OtpParameters? {
        val text = value.trim()
        return when {
            text.startsWith("otpauth://", ignoreCase = true) -> parseOtpAuth(text)
            text.startsWith("steam://", ignoreCase = true) ->
                Base32.decode(text.substringAfter("://"))?.let { OtpParameters(it, isSteam = true) }
            text.contains("key=") -> parseQuery(text).let { query ->
                Base32.decode(query["key"].orEmpty())?.let { secret ->
                    OtpParameters(
                        secret = secret,
                        period = query["step"]?.toIntOrNull() ?: OtpParameters.DEFAULT_PERIOD,
                        digits = query["size"]?.toIntOrNull() ?: OtpParameters.DEFAULT_DIGITS,
                        algorithm = algorithmOf(query["otpHashMode"]) ?: OtpAlgorithm.SHA1
                    )
                }
            }
            else -> Base32.decode(text)?.takeIf { it.isNotEmpty() }?.let { OtpParameters(it) }
        }?.takeIf { it.isValid() }
    }

    fun toUri(parameters: OtpParameters): String {
        val label = listOfNotNull(parameters.issuer, parameters.account).joinToString(":").ifEmpty { "Snaplock" }
        val query = buildList {
            add("secret=${Base32.encode(parameters.secret)}")
            parameters.issuer?.let { add("issuer=${encode(it)}") }
            add("period=${parameters.period}")
            add("digits=${if (parameters.isSteam) 5 else parameters.digits}")
            add("algorithm=${parameters.algorithm.uriName}")
            if (parameters.isSteam) add("encoder=steam")
        }
        return "otpauth://totp/${encode(label)}?${query.joinToString("&")}"
    }

    private fun parseOtpAuth(uri: String): OtpParameters? {
        val type = uri.substringAfter("://").substringBefore('/').lowercase()
        if (type != "totp") return null
        val label = decode(uri.substringAfter("://").substringAfter('/', "").substringBefore('?'))
        val query = parseQuery(uri.substringAfter('?', ""))
        val secret = Base32.decode(query["secret"].orEmpty()) ?: return null
        val isSteam = query["encoder"].equals("steam", ignoreCase = true)
        return OtpParameters(
            secret = secret,
            period = query["period"]?.toIntOrNull() ?: OtpParameters.DEFAULT_PERIOD,
            digits = if (isSteam) 5 else query["digits"]?.toIntOrNull() ?: OtpParameters.DEFAULT_DIGITS,
            algorithm = algorithmOf(query["algorithm"]) ?: OtpAlgorithm.SHA1,
            isSteam = isSteam,
            issuer = query["issuer"] ?: label.substringBefore(':', "").ifEmpty { null },
            account = label.substringAfter(':').ifEmpty { null }
        )
    }

    private fun timeOtp(fields: Map<String, String>): OtpParameters? {
        val secret = fields[TIME_OTP_BASE32]?.let(Base32::decode)
            ?: fields[TIME_OTP_HEX]?.let(::hexToBytes)
            ?: fields[TIME_OTP_BASE64]?.let { it.trim().decodeBase64()?.toByteArray() }
            ?: fields[TIME_OTP_TEXT]?.toByteArray(Charsets.UTF_8)
            ?: return null
        return OtpParameters(
            secret = secret,
            period = fields[TIME_OTP_PERIOD]?.trim()?.toIntOrNull() ?: OtpParameters.DEFAULT_PERIOD,
            digits = fields[TIME_OTP_LENGTH]?.trim()?.toIntOrNull() ?: OtpParameters.DEFAULT_DIGITS,
            algorithm = algorithmOf(fields[TIME_OTP_ALGORITHM]) ?: OtpAlgorithm.SHA1
        ).takeIf { it.isValid() }
    }

    private fun legacy(fields: Map<String, String>): OtpParameters? {
        val secret = fields[LEGACY_SEED]?.let(Base32::decode) ?: return null
        val settings = fields[LEGACY_SETTINGS].orEmpty().split(';')
        val period = settings.getOrNull(0)?.trim()?.toIntOrNull() ?: OtpParameters.DEFAULT_PERIOD
        val size = settings.getOrNull(1)?.trim()
        return OtpParameters(
            secret = secret,
            period = period,
            digits = size?.toIntOrNull() ?: OtpParameters.DEFAULT_DIGITS,
            isSteam = size.equals("S", ignoreCase = true)
        ).takeIf { it.isValid() }
    }

    /** "SHA1", "sha256", "HMAC-SHA-512" and similar spellings. */
    private fun algorithmOf(value: String?): OtpAlgorithm? {
        val normalized = value?.uppercase()?.filter { it.isLetterOrDigit() }?.removePrefix("HMAC") ?: return null
        return OtpAlgorithm.entries.firstOrNull { it.uriName == normalized }
    }

    private fun OtpParameters.isValid(): Boolean =
        secret.isNotEmpty() && period in 1..300 && (isSteam || digits in 6..10)

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to decode(pair.substringAfter('='))
        }.toMap()

    private fun hexToBytes(hex: String): ByteArray? {
        val cleaned = hex.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        return runCatching { ByteArray(cleaned.length / 2) { cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
            .getOrNull()
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
