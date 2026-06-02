package io.tarantini.shelf.processing.jobs

import arrow.core.raise.catch
import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.catalog.podcast.PodcastFeedFetchService
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

class PodcastCoverBackfillWorker(
    private val scope: CoroutineScope,
    private val feedFetchService: PodcastFeedFetchService,
    private val valkeyConnection: StatefulRedisConnection<String, String>? = null,
    private val inMemoryChannel: Channel<PodcastId>? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        scope.launch(dispatcher) {
            logger.info { "Starting PodcastCoverBackfillWorker..." }
            while (isActive) {
                val podcastId =
                    catch({
                        if (valkeyConnection != null) {
                            val commands = valkeyConnection.sync()
                            val result = commands.brpop(10, "jobs:backfill_covers")
                            result?.let { PodcastId.fromRaw(it.value) }
                        } else if (inMemoryChannel != null) {
                            inMemoryChannel.receive()
                        } else {
                            null
                        }
                    }) { error ->
                        if (error is CancellationException) throw error
                        logger.error(error) { "Podcast cover backfill worker iteration failed." }
                        null
                    }

                if (podcastId != null) {
                    processBackfillJob(podcastId)
                }
            }
        }
    }

    private suspend fun processBackfillJob(podcastId: PodcastId) {
        either { feedFetchService.backfillCovers(podcastId) }
            .mapLeft { err ->
                logger.warn { "Podcast cover backfill failed: ${err::class.simpleName}" }
            }
    }
}
