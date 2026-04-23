@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.CreatePodcastCommand
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpdatePodcastCommand
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlin.uuid.Uuid

class PodcastServiceTest :
    StringSpec({
        "createPodcast orchestrates command to repository" {
            val readRepository = mockk<PodcastReadRepository>()
            val mutationRepository = mockk<PodcastMutationRepository>()
            val libationService = mockk<PodcastLibationService>()
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val saved =
                PodcastRoot.fromRaw(
                    id = PodcastId.fromRaw(Uuid.random()),
                    seriesId = seriesId,
                    feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                    feedToken = FeedToken.generate(),
                    feedTokenExpiresAt = null,
                    feedTokenPrevious = null,
                    feedTokenPreviousExpiresAt = null,
                    autoSanitize = true,
                    autoFetch = true,
                    lastFetchedAt = null,
                    fetchIntervalMinutes = 60,
                    version = 0,
                )

            val calls = mutableListOf<String>()
            var persistedFeedToken = ""
            val capturedRoot = slot<io.tarantini.shelf.catalog.podcast.domain.NewPodcastRoot>()
            coEvery {
                with(any<RaiseContext>()) {
                    mutationRepository.createPodcast(capture(capturedRoot))
                }
            } coAnswers
                {
                    calls += "persist"
                    val root = capturedRoot.captured
                    persistedFeedToken = root.feedToken.value
                    root.seriesId shouldBe seriesId
                    root.feedUrl.value shouldBe "https://example.com/feed.xml"
                    root.autoSanitize shouldBe true
                    root.autoFetch shouldBe false
                    root.fetchIntervalMinutes shouldBe 45
                    saved
                }

            val service =
                podcastService(readRepository, mutationRepository, mockk(), libationService)
            val command =
                CreatePodcastCommand(
                    seriesId = seriesId,
                    feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                    autoSanitize = true,
                    autoFetch = false,
                    fetchIntervalMinutes = 45,
                )

            val result = either { service.createPodcast(command) }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe saved })
            calls shouldBe listOf("persist")
            persistedFeedToken.shouldNotBeBlank()
        }

        "updatePodcast loads existing and preserves missing fields" {
            val readRepository = mockk<PodcastReadRepository>()
            val mutationRepository = mockk<PodcastMutationRepository>()
            val libationService = mockk<PodcastLibationService>()
            val podcastId = PodcastId.fromRaw(Uuid.random())
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val existing =
                PodcastRoot.fromRaw(
                    id = podcastId,
                    seriesId = seriesId,
                    feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                    feedToken = FeedToken.generate(),
                    feedTokenExpiresAt = null,
                    feedTokenPrevious = null,
                    feedTokenPreviousExpiresAt = null,
                    autoSanitize = true,
                    autoFetch = true,
                    lastFetchedAt = null,
                    fetchIntervalMinutes = 60,
                    version = 0,
                )
            val updated =
                PodcastRoot.fromRaw(
                    id = podcastId,
                    seriesId = seriesId,
                    feedUrl = existing.feedUrl,
                    feedToken = existing.feedToken,
                    feedTokenExpiresAt = existing.feedTokenExpiresAt,
                    feedTokenPrevious = existing.feedTokenPrevious,
                    feedTokenPreviousExpiresAt = existing.feedTokenPreviousExpiresAt,
                    autoSanitize = true,
                    autoFetch = false,
                    lastFetchedAt = existing.lastFetchedAt,
                    fetchIntervalMinutes = 30,
                    version = existing.version,
                )

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { mutationRepository.getPodcastById(podcastId) }
            } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) {
                    mutationRepository.updateFeedSettings(
                        id = podcastId,
                        autoSanitize = true,
                        autoFetch = false,
                        fetchIntervalMinutes = 30,
                    )
                }
            } coAnswers
                {
                    calls += "persist"
                    updated
                }

            val service =
                podcastService(readRepository, mutationRepository, mockk(), libationService)
            val result = either {
                service.updatePodcast(
                    UpdatePodcastCommand(
                        id = podcastId,
                        autoSanitize = null,
                        autoFetch = false,
                        fetchIntervalMinutes = 30,
                    )
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe updated })
            calls shouldBe listOf("load", "persist")
        }

        "deletePodcast loads first then deletes" {
            val readRepository = mockk<PodcastReadRepository>()
            val mutationRepository = mockk<PodcastMutationRepository>()
            val libationService = mockk<PodcastLibationService>()
            val podcastId = PodcastId.fromRaw(Uuid.random())
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val existing =
                PodcastRoot.fromRaw(
                    id = podcastId,
                    seriesId = seriesId,
                    feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                    feedToken = FeedToken.generate(),
                    feedTokenExpiresAt = null,
                    feedTokenPrevious = null,
                    feedTokenPreviousExpiresAt = null,
                    autoSanitize = true,
                    autoFetch = true,
                    lastFetchedAt = null,
                    fetchIntervalMinutes = 60,
                    version = 0,
                )

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { mutationRepository.getPodcastById(podcastId) }
            } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) { mutationRepository.deletePodcast(podcastId) }
            } coAnswers { calls += "delete" }

            val service =
                podcastService(readRepository, mutationRepository, mockk(), libationService)
            val result = either { service.deletePodcast(podcastId) }

            result.fold({ fail("Should not have failed: $it") }, {})
            calls shouldBe listOf("load", "delete")
        }
    })
