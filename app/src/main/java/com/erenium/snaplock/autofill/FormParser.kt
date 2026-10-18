package com.erenium.snaplock.autofill

/**
 * One view of the screen that asks for autofill, independent of the Android classes so the
 * detection rules can be unit tested. [ID] is the platform's AutofillId in production.
 */
interface FormNode<ID> {
    val autofillId: ID?
    val autofillHints: List<String>
    /** HTML tag and attributes when the view is a web form element. */
    val htmlTag: String?
    val htmlAttributes: Map<String, String>
    val inputType: Int
    val idEntry: String?
    val hint: String?
    val isTextField: Boolean
    val isVisible: Boolean
    val isFocused: Boolean
    val webDomain: String?
    /** Current text, used when the system asks to save what the user typed. */
    val text: String?
    val children: List<FormNode<ID>>
}

/** The login form found on a screen. */
data class LoginForm<ID>(
    val usernameIds: List<ID>,
    val passwordIds: List<ID>,
    val webDomain: String?,
    val username: String?,
    val password: String?,
    /** Fields asking for a two-factor code, often on a second login page. */
    val otpIds: List<ID> = emptyList()
) {
    val allIds: List<ID> get() = usernameIds + passwordIds + otpIds
    val isEmpty: Boolean get() = usernameIds.isEmpty() && passwordIds.isEmpty() && otpIds.isEmpty()
}

object FormParser {

    private val usernameKeywords = listOf(
        "user", "login", "email", "e-mail", "mail", "account", "phone", "identifier",
        "kullanici", "kullanıcı", "eposta", "e-posta", "telefon", "tckn", "tc kimlik"
    )
    // No bare "pass": it also matches "passport" and "compass".
    private val passwordKeywords = listOf("password", "passwd", "pwd", "parola", "şifre", "sifre")
    private val ignoredKeywords = listOf("search", "captcha", "otp", "2fa", "arama", "ara", "promo")
    // Matched as whole words: "otp" must not hit "hotpot".
    private val otpWords = listOf("otp", "totp", "2fa", "mfa", "authenticator")
    private val otpPhrases = listOf(
        "one-time", "one_time", "onetime", "verification code", "verification_code", "verificationcode",
        "security code", "auth code", "authentication code", "doğrulama kodu", "dogrulama kodu",
        "tek kullanımlık", "onay kodu"
    )

    fun <ID> parse(root: FormNode<ID>): LoginForm<ID> = parse(listOf(root))

    fun <ID> parse(roots: List<FormNode<ID>>): LoginForm<ID> {
        val fields = mutableListOf<FormNode<ID>>()
        var webDomain: String? = null
        fun walk(node: FormNode<ID>) {
            if (!node.isVisible) return
            if (webDomain == null && !node.webDomain.isNullOrBlank()) webDomain = node.webDomain
            if (node.isTextField && node.autofillId != null) fields += node
            node.children.forEach(::walk)
        }
        roots.forEach(::walk)

        val passwords = fields.filter { isPassword(it) }
        val otps = fields.filter { it !in passwords && isOneTimeCode(it) }
        val usernames = fields.filter { it !in passwords && it !in otps && isUsername(it) }.toMutableList()
        if (usernames.isEmpty() && passwords.isNotEmpty()) {
            // Many forms only mark the password; the username is then the text field before it.
            val firstPassword = fields.indexOf(passwords.first())
            fields.subList(0, firstPassword)
                .lastOrNull { it !in passwords && it !in otps && !isIgnored(it) }
                ?.let(usernames::add)
        }
        return LoginForm(
            usernameIds = usernames.mapNotNull { it.autofillId },
            passwordIds = passwords.mapNotNull { it.autofillId },
            webDomain = webDomain?.trim()?.lowercase(),
            username = usernames.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) },
            password = passwords.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) },
            otpIds = otps.mapNotNull { it.autofillId }
        )
    }

    internal fun isOneTimeCode(node: FormNode<*>): Boolean {
        // Android hints such as "smsOTPCode" and "2faAppOTPCode", optionally numbered per digit box.
        if (node.autofillHints.any { it.lowercase().contains("otp") }) return true
        if (node.htmlAttributes["autocomplete"].equals("one-time-code", ignoreCase = true)) return true
        return node.labels().any { label ->
            label.split(*SEPARATORS).any { it in otpWords } || otpPhrases.any { label.contains(it) }
        }
    }

    internal fun isPassword(node: FormNode<*>): Boolean {
        val hints = node.autofillHints.map { it.lowercase() }
        if (hints.any { it.contains("password") }) return true
        if (hints.isNotEmpty() && hints.none { it == "off" || it == "on" }) return false
        if (node.htmlTag.equals("input", ignoreCase = true)) {
            return node.htmlAttributes["type"].equals("password", ignoreCase = true)
        }
        if (isPasswordInputType(node.inputType)) return true
        return !isIgnored(node) && node.labels().any { label -> passwordKeywords.any { label.contains(it) } }
    }

    internal fun isUsername(node: FormNode<*>): Boolean {
        if (isIgnored(node)) return false
        val hints = node.autofillHints.map { it.lowercase() }
        if (hints.any { it.contains("username") || it.contains("email") || it == "phone" || it.contains("phonenumber") }) {
            return true
        }
        if (node.htmlTag.equals("input", ignoreCase = true)) {
            val type = node.htmlAttributes["type"]?.lowercase()
            if (type == "email" || type == "tel") return true
            if (type != null && type != "text") return false
        }
        if (isEmailInputType(node.inputType)) return true
        return node.labels().any { label -> usernameKeywords.any { label.contains(it) } }
    }

    private fun isIgnored(node: FormNode<*>): Boolean =
        node.labels().any { label -> ignoredKeywords.any { keyword -> label.split(*SEPARATORS).contains(keyword) } }

    private fun FormNode<*>.labels(): List<String> =
        listOfNotNull(
            idEntry,
            hint,
            htmlAttributes["name"],
            htmlAttributes["id"],
            htmlAttributes["autocomplete"],
            htmlAttributes["placeholder"],
            htmlAttributes["aria-label"]
        ).map { it.lowercase() }

    private val SEPARATORS = charArrayOf(' ', '_', '-', '.', ':', '[', ']')

    // android.text.InputType constants, inlined so the parser stays free of Android classes.
    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and TYPE_MASK_VARIATION
        return when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun isEmailInputType(inputType: Int): Boolean {
        val variation = inputType and TYPE_MASK_VARIATION
        return (inputType and TYPE_MASK_CLASS) == TYPE_CLASS_TEXT &&
            (variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
    }
}
