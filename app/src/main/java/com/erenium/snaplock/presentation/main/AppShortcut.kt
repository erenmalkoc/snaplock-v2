package com.erenium.snaplock.presentation.main

enum class AppShortcut(val action: String) {
    SEARCH("com.erenium.snaplock.action.SEARCH"),
    NEW_ENTRY("com.erenium.snaplock.action.NEW_ENTRY"),
    GENERATOR("com.erenium.snaplock.action.GENERATE_PASSWORD"),
    LOCK("com.erenium.snaplock.action.LOCK");

    val id: String get() = name.lowercase()

    companion object {
        fun fromAction(action: String?): AppShortcut? = entries.firstOrNull { it.action == action }
    }
}
