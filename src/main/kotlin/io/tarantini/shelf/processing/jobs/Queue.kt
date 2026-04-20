package io.tarantini.shelf.processing.jobs

import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.catalog.book.domain.BookId
import kotlinx.coroutines.channels.Channel
import kotlin.uuid.ExperimentalUuidApi

interface JobQueue {
    suspend fun enqueueSyncMetadataJob(bookId: BookId)
}

class ValkeyJobQueue(private val connection: StatefulRedisConnection<String, String>) : JobQueue {
    private val commands = connection.async()
    
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enqueueSyncMetadataJob(bookId: BookId) {
        commands.lpush("jobs:sync_metadata", bookId.value.toString())
    }
}

class InMemoryJobQueue(private val channel: Channel<BookId>) : JobQueue {
    override suspend fun enqueueSyncMetadataJob(bookId: BookId) {
        channel.send(bookId)
    }
}
