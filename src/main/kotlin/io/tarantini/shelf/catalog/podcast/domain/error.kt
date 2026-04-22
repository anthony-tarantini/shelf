package io.tarantini.shelf.catalog.podcast.domain

import io.tarantini.shelf.app.AppError

sealed interface PodcastError : AppError

sealed interface PodcastPersistenceError : PodcastError

object PodcastNotFound : PodcastPersistenceError

object PodcastAlreadyExists : PodcastPersistenceError

object PodcastFeedAlreadySubscribed : PodcastPersistenceError

sealed interface PodcastValidationError : PodcastError

object EmptyPodcastId : PodcastValidationError

object InvalidPodcastId : PodcastValidationError

object EmptyFeedUrl : PodcastValidationError

object InvalidFeedUrl : PodcastValidationError

object InvalidFeedToken : PodcastValidationError

object InvalidEpisodeIndex : PodcastValidationError

sealed interface PodcastIntegrationError : PodcastError

object FeedFetchFailed : PodcastIntegrationError

object FeedParseFailed : PodcastIntegrationError

object FeedAuthRequired : PodcastIntegrationError

data class FeedRateLimited(val retryAfterSeconds: Int?) : PodcastIntegrationError
