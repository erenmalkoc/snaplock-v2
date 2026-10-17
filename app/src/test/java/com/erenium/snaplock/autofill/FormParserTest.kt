package com.erenium.snaplock.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormParserTest {

    @Test
    fun nativeFormWithAutofillHints() {
        val form = FormParser.parse(
            container(
                field("user", hints = listOf("username")),
                field("pass", hints = listOf("password")),
                field("remember")
            )
        )

        assertEquals(listOf("user"), form.usernameIds)
        assertEquals(listOf("pass"), form.passwordIds)
    }

    @Test
    fun nativeFormDetectedFromInputTypes() {
        val form = FormParser.parse(
            container(
                field("email", inputType = TEXT or EMAIL_VARIATION),
                field("pin", inputType = NUMBER or NUMBER_PASSWORD_VARIATION)
            )
        )

        assertEquals(listOf("email"), form.usernameIds)
        assertEquals(listOf("pin"), form.passwordIds)
    }

    @Test
    fun webFormInChromeKeepsTheDomain() {
        val form = FormParser.parse(
            container(
                web("q", type = "search", name = "q"),
                web("login", type = "text", name = "login"),
                web("pw", type = "password", name = "password"),
                domain = "GitHub.com"
            )
        )

        assertEquals(listOf("login"), form.usernameIds)
        assertEquals(listOf("pw"), form.passwordIds)
        assertEquals("github.com", form.webDomain)
    }

    @Test
    fun unlabeledFieldBeforeThePasswordIsTheUsername() {
        val form = FormParser.parse(
            container(
                field("search", idEntry = "search_box"),
                field("first"),
                field("secret", inputType = TEXT or PASSWORD_VARIATION)
            )
        )

        assertEquals(listOf("first"), form.usernameIds)
        assertEquals(listOf("secret"), form.passwordIds)
    }

    @Test
    fun turkishLabelsAreRecognised() {
        val form = FormParser.parse(
            container(
                field("tc", hint = "TC Kimlik No"),
                field("sifre", hint = "Şifre")
            )
        )

        assertEquals(listOf("tc"), form.usernameIds)
        assertEquals(listOf("sifre"), form.passwordIds)
    }

    @Test
    fun searchOnlyScreenHasNoLoginForm() {
        val form = FormParser.parse(container(field("search", idEntry = "search_src_text")))

        assertTrue(form.isEmpty)
    }

    @Test
    fun passportFieldIsNotAPassword() {
        val form = FormParser.parse(container(field("passport", idEntry = "passport_number")))

        assertTrue(form.passwordIds.isEmpty())
    }

    @Test
    fun hiddenFieldsAreSkipped() {
        val form = FormParser.parse(
            container(
                field("user", hints = listOf("username"), visible = false),
                field("pass", hints = listOf("password"))
            )
        )

        assertTrue(form.usernameIds.isEmpty())
        assertEquals(listOf("pass"), form.passwordIds)
    }

    @Test
    fun readsTypedValuesForSaving() {
        val form = FormParser.parse(
            container(
                field("user", hints = listOf("username"), text = "alice"),
                field("pass", hints = listOf("password"), text = "hunter2")
            )
        )

        assertEquals("alice", form.username)
        assertEquals("hunter2", form.password)
    }

    @Test
    fun noDomainForNativeApps() {
        assertNull(FormParser.parse(container(field("pass", hints = listOf("password")))).webDomain)
    }

    private data class Node(
        override val autofillId: String?,
        override val autofillHints: List<String> = emptyList(),
        override val htmlTag: String? = null,
        override val htmlAttributes: Map<String, String> = emptyMap(),
        override val inputType: Int = TEXT,
        override val idEntry: String? = null,
        override val hint: String? = null,
        override val isTextField: Boolean = true,
        override val isVisible: Boolean = true,
        override val isFocused: Boolean = false,
        override val webDomain: String? = null,
        override val text: String? = null,
        override val children: List<FormNode<String>> = emptyList()
    ) : FormNode<String>

    private fun container(vararg children: Node, domain: String? = null) =
        Node(autofillId = null, isTextField = false, webDomain = domain, children = children.toList())

    private fun field(
        id: String,
        hints: List<String> = emptyList(),
        inputType: Int = TEXT,
        idEntry: String? = null,
        hint: String? = null,
        visible: Boolean = true,
        text: String? = null
    ) = Node(
        autofillId = id,
        autofillHints = hints,
        inputType = inputType,
        idEntry = idEntry,
        hint = hint,
        isVisible = visible,
        text = text
    )

    private fun web(id: String, type: String, name: String) =
        Node(autofillId = id, htmlTag = "input", htmlAttributes = mapOf("type" to type, "name" to name))

    private companion object {
        const val TEXT = 0x1
        const val NUMBER = 0x2
        const val EMAIL_VARIATION = 0x20
        const val PASSWORD_VARIATION = 0x80
        const val NUMBER_PASSWORD_VARIATION = 0x10
    }
}
