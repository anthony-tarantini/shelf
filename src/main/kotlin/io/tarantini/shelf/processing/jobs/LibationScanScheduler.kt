package io.tarantini.shelf.processing.jobs

import arrow.core.raise.catch
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.catalog.podcast.PodcastLibationService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

class LibationScanScheduler(
    private val scope: CoroutineScope,
    private val libationService: PodcastLibationService,
    private val intervalSeconds: Long,
) {
    fun start() {
        if (intervalSeconds <= 0) {
            logger.info { "LibationScanScheduler disabled because interval is <= 0 seconds." }
            return
        }

        scope.launch(Dispatchers.IO) {
            logger.info { "Starting LibationScanScheduler..." }
            while (isActive) {
                catch({ libationService.scanNowBestEffort() }) { error ->
                    if (error is CancellationException) throw error
                    logger.warn(error) { "Libation scan scheduler iteration failed." }
                }

                delay(intervalSeconds.seconds)
            }
        }
    }
}
