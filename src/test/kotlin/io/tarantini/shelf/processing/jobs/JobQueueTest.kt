@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.jobs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel

class JobQueueTest :
    StringSpec({
        "InMemoryJobQueue should send bookId to channel" {
            val metadataChannel = Channel<BookId>(1)
            val feedFetchChannel = Channel<PodcastId>(1)
            val queue = InMemoryJobQueue(metadataChannel, feedFetchChannel)
            val bookId = BookId.fromRaw(Uuid.random())

            queue.enqueueSyncMetadataJob(bookId)

            metadataChannel.receive() shouldBe bookId
        }

        "InMemoryJobQueue should send podcastId to feed-fetch channel" {
            val metadataChannel = Channel<BookId>(1)
            val feedFetchChannel = Channel<PodcastId>(1)
            val queue = InMemoryJobQueue(metadataChannel, feedFetchChannel)
            val podcastId = PodcastId.fromRaw(Uuid.random())

            queue.enqueueFeedFetchJob(podcastId)

            feedFetchChannel.receive() shouldBe podcastId
        }
    })
