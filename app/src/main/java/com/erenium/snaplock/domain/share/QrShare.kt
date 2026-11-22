package com.erenium.snaplock.domain.share

import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.otp.OtpParser

enum class QrShareKind { Password, Wifi, Username, Url, OneTimeCode, Field }

data class QrShareOption(val kind: QrShareKind, val fieldName: String? = null) {
    /** Stable text id, so the choice can be kept over a rotation without holding any secret. */
    val key: String get() = if (fieldName == null) kind.name else "${kind.name}:$fieldName"
}

data class WifiSettings(
    val ssid: String,
    val security: WifiSecurity = WifiSecurity.Wpa,
    val hidden: Boolean = false
)

/** What an entry can show as a QR code and the exact text that goes into it. */
object QrShare {
    private val wifiTitle = Regex("wi[\\s-]?fi|wlan|ssid|kablosuz", RegexOption.IGNORE_CASE)

    fun options(entry: EntryDetail): List<QrShareOption> = buildList {
        if (!entry.password.isNullOrEmpty()) {
            add(QrShareOption(QrShareKind.Password))
            add(QrShareOption(QrShareKind.Wifi))
        }
        if (!entry.username.isNullOrBlank()) add(QrShareOption(QrShareKind.Username))
        if (!entry.url.isNullOrBlank()) add(QrShareOption(QrShareKind.Url))
        if (entry.otp != null) add(QrShareOption(QrShareKind.OneTimeCode))
        entry.customFields
            .filter { it.value.isNotEmpty() }
            .forEach { add(QrShareOption(QrShareKind.Field, it.name)) }
    }

    /** The Wi-Fi choice leads for entries that are plainly about a network, otherwise the first one does. */
    fun defaultOption(entry: EntryDetail, options: List<QrShareOption> = options(entry)): QrShareOption? {
        val wifi = options.firstOrNull { it.kind == QrShareKind.Wifi }
        if (wifi != null && wifiTitle.containsMatchIn(entry.title.replace('İ', 'i'))) return wifi
        return options.firstOrNull()
    }

    /** Many people keep the network name as the user name, the title is the next best guess. */
    fun defaultSsid(entry: EntryDetail): String = entry.username?.takeIf { it.isNotBlank() } ?: entry.title

    /** Null when there is nothing to show, for instance a Wi-Fi code without a network name. */
    fun payload(entry: EntryDetail, option: QrShareOption, wifi: WifiSettings): String? = when (option.kind) {
        QrShareKind.Password -> entry.password
        QrShareKind.Username -> entry.username
        QrShareKind.Url -> entry.url?.let { webAddress(it) }
        QrShareKind.OneTimeCode -> entry.otp?.let { OtpParser.toUri(it) }
        QrShareKind.Field -> entry.customFields.firstOrNull { it.name == option.fieldName }?.value
        QrShareKind.Wifi -> wifiPayload(entry.password.orEmpty(), wifi)
    }?.takeIf { it.isNotEmpty() }

    private fun wifiPayload(password: String, wifi: WifiSettings): String? {
        if (wifi.ssid.isBlank()) return null
        if (password.isEmpty() && wifi.security != WifiSecurity.Open) return null
        return WifiQr.payload(wifi.ssid, password, wifi.security, wifi.hidden)
    }

    private fun webAddress(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }
}
