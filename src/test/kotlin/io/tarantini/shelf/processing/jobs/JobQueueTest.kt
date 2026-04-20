@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.jobs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel

class JobQueueTest :
    StringSpec({
        "InMemoryJobQueue should send bookId to channel" {
            val channel = Channel<BookId>(1)
            val queue = InMemoryJobQueue(channel)
            val bookId = BookId.fromRaw(Uuid.random())

            queue.enqueueSyncMetadataJob(bookId)

            channel.receive() shouldBe bookId
        }
    })
