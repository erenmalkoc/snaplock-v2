package com.erenium.snaplock.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DeletedEntry(val uuid: UUID, val title: String)

@Singleton
class RecentlyDeleted @Inject constructor() {
    private val channel = Channel<DeletedEntry>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    fun moved(entry: DeletedEntry) {
        channel.trySend(entry)
    }
}
