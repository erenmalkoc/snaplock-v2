package com.erenium.snaplock.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> awaitReal(block: suspend () -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }
