package com.erenium.snaplock.ui.screens.licenses

internal data class OpenSourceItem(
    val name: String,
    val license: String,
    val url: String,
    val groups: List<String> = emptyList()
)

internal val OpenSourceLibraries = listOf(
    OpenSourceItem(
        name = "AndroidX and Jetpack Compose",
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack",
        groups = listOf("androidx.")
    ),
    OpenSourceItem(
        name = "Kotlin and kotlinx",
        license = "Apache-2.0",
        url = "https://kotlinlang.org",
        groups = listOf("org.jetbrains")
    ),
    OpenSourceItem(
        name = "Dagger and Hilt",
        license = "Apache-2.0",
        url = "https://dagger.dev",
        groups = listOf("com.google.dagger")
    ),
    OpenSourceItem(
        name = "Kotpass",
        license = "MIT",
        url = "https://github.com/keemobile/kotpass",
        groups = listOf("app.keemobile")
    ),
    OpenSourceItem(
        name = "Okio",
        license = "Apache-2.0",
        url = "https://square.github.io/okio/",
        groups = listOf("com.squareup.okio")
    ),
    OpenSourceItem(
        name = "ZXing",
        license = "Apache-2.0",
        url = "https://github.com/zxing/zxing",
        groups = listOf("com.google.zxing")
    ),
    OpenSourceItem(
        name = "ZXing Android Embedded",
        license = "Apache-2.0",
        url = "https://github.com/journeyapps/zxing-android-embedded",
        groups = listOf("com.journeyapps")
    ),
    OpenSourceItem(
        name = "Android desugar JDK libs",
        license = "GPL-2.0 with Classpath Exception",
        url = "https://github.com/google/desugar_jdk_libs",
        groups = listOf("com.android.tools")
    )
)

internal val OpenSourceData = listOf(
    OpenSourceItem(
        name = "EFF large wordlist",
        license = "CC BY 3.0 US",
        url = "https://www.eff.org/dice"
    )
)
