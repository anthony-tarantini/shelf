package io.tarantini.shelf.catalog.podcast.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlinx.serialization.Serializable

@Serializable
data class PodcastRequest(
    val seriesId: String? = null,
    val feedUrl: String? = null,
    val autoSanitize: Boolean? = null,
    val autoFetch: Boolean? = null,
    val fetchIntervalMinutes: Int? = null,
)

context(_: RaiseContext)
fun PodcastRequest.toCreateCommand(): CreatePodcastCommand {
    val interval = fetchIntervalMinutes ?: 60
    ensure(interval in 1..10_080) { InvalidFetchInterval }
    return CreatePodcastCommand(
        seriesId = SeriesId(seriesId),
        feedUrl = FeedUrl(feedUrl),
        autoSanitize = autoSanitize ?: true,
        autoFetch = autoFetch ?: true,
        fetchIntervalMinutes = interval,
    )
}

context(_: RaiseContext)
fun PodcastRequest.toUpdateCommand(id: String): UpdatePodcastCommand {
    fetchIntervalMinutes?.let { ensure(it in 1..10_080) { InvalidFetchInterval } }
    return UpdatePodcastCommand(
        id = PodcastId(id),
        autoSanitize = autoSanitize,
        autoFetch = autoFetch,
        fetchIntervalMinutes = fetchIntervalMinutes,
    )
}
