@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class PodcastPersistenceAdapterTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "createPodcast and getPodcastById should round-trip through adapters" {
            testWithDeps { deps ->
                val seriesId =
                    deps.database.seriesQueries.insert(unique("podcast-series")).executeAsOne()
                val feedUrl = FeedUrl.fromRaw("https://example.com/${unique("feed")}.xml")
                val token = FeedToken.generate()

                recover({
                    val id =
                        deps.database.podcastQueries.createPodcast(
                            seriesId = seriesId,
                            feedUrl = feedUrl,
                            feedToken = token,
                            autoSanitize = true,
                            autoFetch = true,
                            fetchIntervalMinutes = 45,
                        )
                    val root = deps.database.podcastQueries.getPodcastById(id)

                    root.seriesId shouldBe seriesId
                    root.feedUrl shouldBe feedUrl
                    root.feedToken shouldBe token
                    root.fetchIntervalMinutes shouldBe 45
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "findByFeedToken should honor token rotation and grace window" {
            testWithDeps { deps ->
                val seriesId =
                    deps.database.seriesQueries.insert(unique("podcast-series")).executeAsOne()
                val originalToken = FeedToken.generate()

                recover({
                    val podcastId =
                        deps.database.podcastQueries.createPodcast(
                            seriesId = seriesId,
                            feedUrl = FeedUrl.fromRaw("https://example.com/${unique("feed")}.xml"),
                            feedToken = originalToken,
                            autoSanitize = true,
                            autoFetch = true,
                            fetchIntervalMinutes = 60,
                        )
                    deps.database.podcastQueries.findByFeedToken(originalToken)?.id?.id shouldBe
                        podcastId

                    val rotated = FeedToken.generate()
                    deps.database.podcastQueries.rotateToken(
                        id = podcastId,
                        newToken = rotated,
                        graceExpiresAt =
                            Instant.fromEpochMilliseconds(System.currentTimeMillis()).plus(1.days),
                    )

                    deps.database.podcastQueries.findByFeedToken(originalToken)?.id?.id shouldBe
                        podcastId
                    deps.database.podcastQueries.findByFeedToken(rotated)?.id?.id shouldBe podcastId
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
