package io.tarantini.shelf.processing.jobs

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.PodcastFeedFetchService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

class PodcastFeedScheduler(
    private val scope: CoroutineScope,
    private val feedFetchService: PodcastFeedFetchService,
    private val jobQueue: JobQueue,
) {
    fun start() {
        scope.launch(Dispatchers.IO) {
            logger.info { "Starting PodcastFeedScheduler..." }
            while (isActive) {
                runCatching {
                        either {
                                val due = feedFetchService.getDuePodcasts()
                                due.forEach { podcast ->
                                    jobQueue.enqueueFeedFetchJob(podcast.id.id)
                                }
                            }
                            .mapLeft { err ->
                                logger.warn {
                                    "Podcast feed scheduling failed: ${err::class.simpleName}"
                                }
                            }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        logger.error(error) { "Podcast feed scheduler iteration failed." }
                    }

                delay(60.seconds)
            }
        }
    }
}
