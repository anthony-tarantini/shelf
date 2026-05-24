package io.tarantini.shelf.processing.jobs

import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

interface JobQueue {
    suspend fun enqueueSyncMetadataJob(bookId: BookId)

    suspend fun enqueueFeedFetchJob(podcastId: PodcastId)
}

class ValkeyJobQueue(private val connection: StatefulRedisConnection<String, String>) : JobQueue {
    private val commands = connection.async()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enqueueSyncMetadataJob(bookId: BookId) {
        withContext(Dispatchers.IO) {
            commands.lpush("jobs:sync_metadata", bookId.value.toString()).get()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun enqueueFeedFetchJob(podcastId: PodcastId) {
        withContext(Dispatchers.IO) {
            commands.lpush("jobs:feed_fetch", podcastId.value.toString()).get()
        }
    }
}

class InMemoryJobQueue(
    private val syncMetadataChannel: Channel<BookId>,
    private val feedFetchChannel: Channel<PodcastId>,
) : JobQueue {
    override suspend fun enqueueSyncMetadataJob(bookId: BookId) {
        syncMetadataChannel.send(bookId)
    }

    override suspend fun enqueueFeedFetchJob(podcastId: PodcastId) {
        feedFetchChannel.send(podcastId)
    }
}
