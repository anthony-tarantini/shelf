package io.tarantini.shelf.catalog.podcast.domain

data class CreatePodcastCommand(
    val seriesId: io.tarantini.shelf.catalog.series.domain.SeriesId,
    val feedUrl: FeedUrl,
    val autoSanitize: Boolean = true,
    val autoFetch: Boolean = true,
    val fetchIntervalMinutes: Int = 60,
)

data class UpdatePodcastCommand(
    val id: PodcastId,
    val autoSanitize: Boolean? = null,
    val autoFetch: Boolean? = null,
    val fetchIntervalMinutes: Int? = null,
)
