package io.tarantini.shelf.processing.jobs

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

class PodcastFeedWorker(
    private val scope: CoroutineScope,
    private val feedFetchService: PodcastFeedFetchService,
    private val valkeyConnection: StatefulRedisConnection<String, String>? = null,
    private val inMemoryChannel: Channel<PodcastId>? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        scope.launch(dispatcher) {
            logger.info { "Starting PodcastFeedWorker..." }
            while (isActive) {
                val podcastId =
                    runCatching {
                            if (valkeyConnection != null) {
                                val commands = valkeyConnection.sync()
                                val result = commands.brpop(10, "jobs:feed_fetch")
                                result?.let { PodcastId.fromRaw(it.value) }
                            } else if (inMemoryChannel != null) {
                                inMemoryChannel.receive()
                            } else {
                                null
                            }
                        }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            logger.error(error) { "Podcast feed worker iteration failed." }
                        }
                        .getOrNull()

                if (podcastId != null) {
                    processFetchJob(podcastId)
                }
            }
        }
    }

    private suspend fun processFetchJob(podcastId: PodcastId) {
        either { feedFetchService.fetchPodcast(podcastId) }
            .mapLeft { err ->
                logger.warn { "Podcast feed fetch failed: ${err::class.simpleName}" }
            }
    }
}
