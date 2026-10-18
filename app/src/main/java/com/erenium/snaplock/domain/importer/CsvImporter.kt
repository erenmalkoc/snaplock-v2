package com.erenium.snaplock.domain.importer

import com.erenium.snaplock.domain.otp.OtpParser

/** One login read from another password manager's export. */
data class ImportedEntry(
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    /** Two-factor settings as an otpauth:// URI, normalised from whatever the source wrote. */
    val otpUri: String?,
    /** Folder, vault or group in the source app; becomes a subgroup. */
    val folder: String?
)

data class CsvImport(
    /** Which app the columns look like it came from, for the confirmation message. */
    val source: String,
    val entries: List<ImportedEntry>
)

/**
 * Reads CSV exports by their header names, so column order and extra columns do not
 * matter: Bitwarden, Chrome and other Chromium browsers, Firefox, 1Password, Proton Pass,
 * LastPass, KeePassXC and similar files all map onto the same fields.
 */
object CsvImporter {

    private val titleColumns = listOf("title", "name")
    private val usernameColumns = listOf("username", "login_username", "user", "login", "user name")
    private val emailColumns = listOf("email", "e-mail")
    private val passwordColumns = listOf("password", "login_password", "pass")
    private val urlColumns = listOf("url", "login_uri", "uri", "website", "web site", "address", "urls")
    private val notesColumns = listOf("notes", "note", "extra", "comments", "comment")
    private val otpColumns = listOf("totp", "login_totp", "otpauth", "otp", "one-time password")
    private val folderColumns = listOf("folder", "group", "grouping", "vault", "category")

    /** Null when the file has no recognisable password column. */
    fun parse(text: String): CsvImport? {
        val rows = Csv.parse(text.removePrefix("\uFEFF")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.size < 2) return null
        val header = rows.first().map { it.trim().lowercase() }
        fun column(names: List<String>) = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }
        val password = column(passwordColumns) ?: return null
        val title = column(titleColumns)
        val username = column(usernameColumns)
        val email = column(emailColumns)
        val url = column(urlColumns)
        val notes = column(notesColumns)
        val otp = column(otpColumns)
        val folder = column(folderColumns)

        val entries = rows.drop(1).map { row ->
            fun cell(index: Int?) = index?.let(row::getOrNull)?.trim()?.takeIf { it.isNotEmpty() }
            val address = cell(url)
            val user = cell(username) ?: cell(email)
            ImportedEntry(
                title = cell(title) ?: address?.let(::hostOf) ?: user ?: "",
                username = user,
                password = index(row, password),
                url = address,
                notes = notes?.let(row::getOrNull)?.takeIf { it.isNotBlank() },
                otpUri = cell(otp)?.let { OtpParser.parse(it) }?.let(OtpParser::toUri),
                folder = cell(folder)?.takeUnless { it == "(none)" }
            )
        }.filter { it.title.isNotEmpty() || it.password != null }
        return CsvImport(sourceOf(header), entries)
    }

    /** Passwords keep their exact characters, including leading and trailing spaces. */
    private fun index(row: List<String>, column: Int): String? = row.getOrNull(column)?.takeIf { it.isNotEmpty() }

    private fun hostOf(url: String): String? =
        url.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.").takeIf { it.isNotBlank() }

    private fun sourceOf(header: List<String>): String = when {
        "login_uri" in header -> "Bitwarden"
        "httprealm" in header || "formactionorigin" in header -> "Firefox"
        "grouping" in header && "extra" in header -> "LastPass"
        "vault" in header && "createtime" in header -> "Proton Pass"
        "otpauth" in header && "archived" in header -> "1Password"
        "group" in header && "last modified" in header -> "KeePassXC"
        header.containsAll(listOf("name", "url", "username", "password")) -> "Chrome"
        else -> "CSV"
    }
}

/** RFC 4180 CSV: quoted fields may hold commas, quotes ("") and line breaks. */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' && (inQuotes || field.isEmpty()) -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
