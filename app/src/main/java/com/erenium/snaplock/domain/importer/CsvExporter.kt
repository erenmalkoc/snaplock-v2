package com.erenium.snaplock.domain.importer

/** One entry as it goes into a CSV export. */
data class ExportedEntry(
    /** Group path below the root, "/"-separated; empty for the root group. */
    val group: String,
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val otpUri: String?
)

/** Writes KeePassXC's CSV layout, which other managers import too. */
object CsvExporter {

    private val header = listOf("Group", "Title", "Username", "Password", "URL", "Notes", "TOTP")

    fun write(entries: List<ExportedEntry>): String = buildString {
        appendRow(header)
        entries.forEach { entry ->
            appendRow(
                listOf(
                    entry.group,
                    entry.title,
                    entry.username.orEmpty(),
                    entry.password.orEmpty(),
                    entry.url.orEmpty(),
                    entry.notes.orEmpty(),
                    entry.otpUri.orEmpty()
                )
            )
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        append("\r\n")
    }
}
