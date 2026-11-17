package com.erenium.snaplock.presentation.main

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppMessage(
    @param:StringRes val id: Int,
    val args: List<String> = emptyList(),
    val postedAt: Long = System.currentTimeMillis()
)

@Singleton
class AppMessages @Inject constructor() {
    private val channel = Channel<AppMessage>(Channel.BUFFERED)

    val messages = channel.receiveAsFlow()

    fun post(@StringRes message: Int, vararg args: String) {
        channel.trySend(AppMessage(message, args.toList()))
    }
}
