package com.erenium.snaplock.presentation.main

/** Launcher shortcuts; each one starts MainActivity with its own intent action. */
enum class AppShortcut(val action: String) {
    SEARCH("com.erenium.snaplock.action.SEARCH"),
    NEW_ENTRY("com.erenium.snaplock.action.NEW_ENTRY"),
    GENERATOR("com.erenium.snaplock.action.GENERATE_PASSWORD"),
    LOCK("com.erenium.snaplock.action.LOCK");

    /** The id the launcher knows this shortcut by. */
    val id: String get() = name.lowercase()

    companion object {
        fun fromAction(action: String?): AppShortcut? = entries.firstOrNull { it.action == action }
    }
}
