@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

class PodcastRepositoryTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "podcast mutation/read repositories should create and update podcast settings" {
            testWithDeps { deps ->
                val seriesId =
                    deps.database.seriesQueries.insert(unique("podcast-series")).executeAsOne()
                val mutationRepo = podcastMutationRepository(deps.database.podcastQueries)
                val readRepo = podcastReadRepository(deps.database.podcastQueries)

                recover({
                    val created =
                        mutationRepo.createPodcast(
                            PodcastRoot.new(
                                seriesId = seriesId,
                                feedUrl =
                                    FeedUrl.fromRaw("https://example.com/${unique("feed")}.xml"),
                                feedToken = FeedToken.generate(),
                                autoSanitize = true,
                                autoFetch = true,
                                fetchIntervalMinutes = 60,
                            )
                        )
                    created.seriesId shouldBe seriesId

                    val updated =
                        mutationRepo.updateFeedSettings(
                            id = created.id.id,
                            autoSanitize = false,
                            autoFetch = true,
                            fetchIntervalMinutes = 30,
                        )
                    updated.autoSanitize shouldBe false
                    updated.fetchIntervalMinutes shouldBe 30

                    val fetched = readRepo.getPodcastById(created.id.id)
                    fetched.fetchIntervalMinutes shouldBe 30
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "podcast repositories should claim guid once and expose due podcasts" {
            testWithDeps { deps ->
                val seriesId =
                    deps.database.seriesQueries.insert(unique("podcast-series")).executeAsOne()
                val mutationRepo = podcastMutationRepository(deps.database.podcastQueries)
                val readRepo = podcastReadRepository(deps.database.podcastQueries)

                recover({
                    val created =
                        mutationRepo.createPodcast(
                            PodcastRoot.new(
                                seriesId = seriesId,
                                feedUrl =
                                    FeedUrl.fromRaw("https://example.com/${unique("feed")}.xml"),
                                feedToken = FeedToken.generate(),
                            )
                        )

                    val book =
                        deps.database.bookQueries.insert(unique("ep-book"), null).executeAsOne()
                    val guid = unique("guid")
                    mutationRepo.claimGuid(created.id.id, guid, book) shouldBe guid
                    mutationRepo.claimGuid(created.id.id, guid, book) shouldBe null
                    readRepo.guidExists(created.id.id, guid) shouldBe true

                    mutationRepo.markFetched(
                        id = created.id.id,
                        fetchedAt =
                            Instant.fromEpochMilliseconds(
                                System.currentTimeMillis() - 2.days.inWholeMilliseconds
                            ),
                    )
                    readRepo.getDuePodcasts().any { it.id.id == created.id.id } shouldBe true
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
