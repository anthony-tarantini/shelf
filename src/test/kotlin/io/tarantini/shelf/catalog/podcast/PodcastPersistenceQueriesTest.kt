@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.processing.storage.StoragePath
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi

class PodcastPersistenceQueriesTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        suspend fun createPodcast(
            deps: io.tarantini.shelf.app.Dependencies
        ): Pair<io.tarantini.shelf.catalog.podcast.domain.PodcastId, FeedToken> {
            val seriesId =
                deps.database.seriesQueries.insert(unique("podcast-series")).executeAsOne()
            val token = FeedToken.generate()
            val podcastId =
                deps.database.podcastQueries
                    .insert(
                        seriesId = seriesId,
                        feedUrl = FeedUrl.fromRaw("https://example.com/${unique("feed")}.xml"),
                        feedToken = token,
                        autoSanitize = true,
                        autoFetch = true,
                        fetchIntervalMinutes = 60,
                    )
                    .executeAsOne()
            return podcastId to token
        }

        "claimEpisodeGuid should be idempotent under repeated claims" {
            testWithDeps { deps ->
                val (podcastId, _) = createPodcast(deps)
                val guid = unique("guid")
                val firstEpisode =
                    deps.database.podcastQueries
                        .insertEpisode(
                            podcastId = podcastId,
                            title = unique("episode"),
                            coverPath = null,
                            audioPath = StoragePath.fromRaw("podcasts/tests/one.mp3"),
                            audioSize = 100,
                            totalTime = 10.0,
                            season = 1,
                            episode = 1,
                            publishedAt = OffsetDateTime.now(),
                        )
                        .executeAsOne()
                val secondEpisode =
                    deps.database.podcastQueries
                        .insertEpisode(
                            podcastId = podcastId,
                            title = unique("episode"),
                            coverPath = null,
                            audioPath = StoragePath.fromRaw("podcasts/tests/two.mp3"),
                            audioSize = 100,
                            totalTime = 10.0,
                            season = 1,
                            episode = 2,
                            publishedAt = OffsetDateTime.now(),
                        )
                        .executeAsOne()

                val firstClaim =
                    deps.database.podcastQueries
                        .claimEpisodeGuid(
                            podcastId = podcastId,
                            guid = guid,
                            episodeId = firstEpisode,
                        )
                        .executeAsOne()
                val secondClaim =
                    deps.database.podcastQueries
                        .claimEpisodeGuid(
                            podcastId = podcastId,
                            guid = guid,
                            episodeId = secondEpisode,
                        )
                        .executeAsOneOrNull()

                firstClaim shouldBe guid
                secondClaim shouldBe null
                deps.database.podcastQueries
                    .selectGuidByPodcastAndGuid(podcastId = podcastId, guid = guid)
                    .executeAsOne() shouldBe guid
            }
        }

        "selectByFeedTokenIncludingPrevious should include current and valid previous token only" {
            testWithDeps { deps ->
                val (podcastId, originalToken) = createPodcast(deps)

                deps.database.podcastQueries
                    .selectByFeedTokenIncludingPrevious(originalToken)
                    .executeAsList()
                    .size shouldBe 1

                val rotatedToken = FeedToken.generate()
                deps.database.podcastQueries.rotateToken(
                    graceExpiresAt = OffsetDateTime.now().plusDays(1),
                    newToken = rotatedToken,
                    id = podcastId,
                )

                deps.database.podcastQueries
                    .selectByFeedTokenIncludingPrevious(originalToken)
                    .executeAsList()
                    .size shouldBe 1
                deps.database.podcastQueries
                    .selectByFeedTokenIncludingPrevious(rotatedToken)
                    .executeAsList()
                    .size shouldBe 1

                val thirdToken = FeedToken.generate()
                deps.database.podcastQueries.rotateToken(
                    graceExpiresAt = OffsetDateTime.now().minusDays(1),
                    newToken = thirdToken,
                    id = podcastId,
                )

                deps.database.podcastQueries
                    .selectByFeedTokenIncludingPrevious(rotatedToken)
                    .executeAsList()
                    .size shouldBe 0
                deps.database.podcastQueries
                    .selectByFeedTokenIncludingPrevious(thirdToken)
                    .executeAsList()
                    .size shouldBe 1
            }
        }

        "selectDuePodcasts should return only auto-fetch podcasts that are due" {
            testWithDeps { deps ->
                val dueSeries =
                    deps.database.seriesQueries.insert(unique("due-series")).executeAsOne()
                val notDueSeries =
                    deps.database.seriesQueries.insert(unique("not-due-series")).executeAsOne()
                val disabledSeries =
                    deps.database.seriesQueries.insert(unique("disabled-series")).executeAsOne()

                val dueId =
                    deps.database.podcastQueries
                        .insert(
                            seriesId = dueSeries,
                            feedUrl = FeedUrl.fromRaw("https://example.com/${unique("due")}.xml"),
                            feedToken = FeedToken.generate(),
                            autoSanitize = true,
                            autoFetch = true,
                            fetchIntervalMinutes = 60,
                        )
                        .executeAsOne()

                val notDueId =
                    deps.database.podcastQueries
                        .insert(
                            seriesId = notDueSeries,
                            feedUrl =
                                FeedUrl.fromRaw("https://example.com/${unique("not-due")}.xml"),
                            feedToken = FeedToken.generate(),
                            autoSanitize = true,
                            autoFetch = true,
                            fetchIntervalMinutes = 60,
                        )
                        .executeAsOne()
                deps.database.podcastQueries.updateLastFetched(OffsetDateTime.now(), notDueId)

                deps.database.podcastQueries
                    .insert(
                        seriesId = disabledSeries,
                        feedUrl = FeedUrl.fromRaw("https://example.com/${unique("disabled")}.xml"),
                        feedToken = FeedToken.generate(),
                        autoSanitize = true,
                        autoFetch = false,
                        fetchIntervalMinutes = 60,
                    )
                    .executeAsOne()

                val due = deps.database.podcastQueries.selectDuePodcasts().executeAsList()
                due.any { it.id == dueId } shouldBe true
                due.any { it.id == notDueId } shouldBe false
                due.any { it.auto_fetch.not() } shouldBe false
            }
        }

        "credentials upsert and exists should round-trip by podcast id" {
            testWithDeps { deps ->
                val (podcastId, _) = createPodcast(deps)
                deps.database.credentialsQueries
                    .existsByPodcastId(podcastId)
                    .executeAsOne() shouldBe false

                deps.database.credentialsQueries.upsert(
                    podcastId = podcastId,
                    credentialType = "HTTP_BASIC",
                    encryptedValue = byteArrayOf(1, 2, 3),
                    iv = byteArrayOf(9, 8, 7),
                )

                deps.database.credentialsQueries
                    .existsByPodcastId(podcastId)
                    .executeAsOne() shouldBe true
                deps.database.credentialsQueries.selectByPodcastId(podcastId).executeAsOne().let {
                    it.credential_type shouldBe "HTTP_BASIC"
                    it.encrypted_value.contentEquals(byteArrayOf(1, 2, 3)) shouldBe true
                    it.iv.contentEquals(byteArrayOf(9, 8, 7)) shouldBe true
                }

                deps.database.credentialsQueries.deleteByPodcastId(podcastId)
                deps.database.credentialsQueries
                    .existsByPodcastId(podcastId)
                    .executeAsOne() shouldBe false
            }
        }

        "selectMaxEpisodeForSeason should return max episode for season" {
            testWithDeps { deps ->
                val (podcastId, _) = createPodcast(deps)
                val episodeA =
                    deps.database.podcastQueries
                        .insertEpisode(
                            podcastId = podcastId,
                            title = unique("episode-a"),
                            coverPath = null,
                            audioPath = StoragePath.fromRaw("podcasts/tests/a.mp3"),
                            audioSize = 100,
                            totalTime = 10.0,
                            season = 1,
                            episode = 4,
                            publishedAt = OffsetDateTime.now().minusDays(2),
                        )
                        .executeAsOne()
                val episodeB =
                    deps.database.podcastQueries
                        .insertEpisode(
                            podcastId = podcastId,
                            title = unique("episode-b"),
                            coverPath = null,
                            audioPath = StoragePath.fromRaw("podcasts/tests/b.mp3"),
                            audioSize = 100,
                            totalTime = 10.0,
                            season = 1,
                            episode = 9,
                            publishedAt = OffsetDateTime.now().minusDays(1),
                        )
                        .executeAsOne()

                deps.database.podcastQueries
                    .claimEpisodeGuid(
                        podcastId = podcastId,
                        guid = unique("guid-a"),
                        episodeId = episodeA,
                    )
                    .executeAsOne()
                deps.database.podcastQueries
                    .claimEpisodeGuid(
                        podcastId = podcastId,
                        guid = unique("guid-b"),
                        episodeId = episodeB,
                    )
                    .executeAsOne()

                deps.database.podcastQueries
                    .selectMaxEpisodeForSeason(podcastId, 1)
                    .executeAsOne() shouldBe 9
                deps.database.podcastQueries
                    .selectMaxEpisodeForSeason(podcastId, 2)
                    .executeAsOne() shouldBe 0
            }
        }
    })
