package com.erenium.snaplock.domain.share

enum class WifiSecurity(val code: String) {
    Wpa("WPA"),
    Wep("WEP"),
    Open("nopass")
}

/**
 * The `WIFI:` text that phone cameras turn into a "join this network" prompt.
 *
 * Fields: T security, S network name, P password, H hidden. The characters
 * backslash, semicolon, comma, colon and double quote are escaped with a
 * backslash. Values made only of hex digits are NOT wrapped in quotes even
 * though the ZXing notes mention it: numeric passwords are common and the
 * camera apps on both platforms take the text literally, so quotes would end
 * up inside the password.
 */
object WifiQr {
    fun payload(
        ssid: String,
        password: String,
        security: WifiSecurity = WifiSecurity.Wpa,
        hidden: Boolean = false
    ): String = buildString {
        append("WIFI:T:").append(security.code)
        append(";S:").append(escape(ssid))
        if (security != WifiSecurity.Open) append(";P:").append(escape(password))
        if (hidden) append(";H:true")
        append(";;")
    }

    private fun escape(value: String): String = buildString(value.length + 4) {
        for (char in value) {
            if (char == '\\' || char == ';' || char == ',' || char == ':' || char == '"') append('\\')
            append(char)
        }
    }
}
