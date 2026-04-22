@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PodcastModelsTest :
    StringSpec({
        "PodcastRoot.new should create an Unsaved PodcastRoot with defaults" {
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val token = FeedToken.generate()
            val root =
                PodcastRoot.new(seriesId, FeedUrl.fromRaw("https://example.com/feed.xml"), token)

            root.id shouldBe Identity.Unsaved
            root.seriesId shouldBe seriesId
            root.feedToken shouldBe token
            root.autoFetch shouldBe true
            root.autoSanitize shouldBe true
            root.version shouldBe 0
        }

        "PodcastRoot.fromRaw should create a Persisted PodcastRoot" {
            val podcastId = PodcastId.fromRaw(Uuid.random())
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val root =
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
                    fetchIntervalMinutes = 30,
                    version = 2,
                )

            root.id shouldBe Identity.Persisted(podcastId)
            root.fetchIntervalMinutes shouldBe 30
            root.version shouldBe 2
        }

        "PodcastRoot.isTokenValid should allow current and unexpired previous token" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val currentToken = FeedToken.generate()
            val previousToken = FeedToken.generate()
            val root =
                PodcastRoot.fromRaw(
                    id = PodcastId.fromRaw(Uuid.random()),
                    seriesId = SeriesId.fromRaw(Uuid.random()),
                    feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                    feedToken = currentToken,
                    feedTokenExpiresAt = now.plus(1.days),
                    feedTokenPrevious = previousToken,
                    feedTokenPreviousExpiresAt = now.plus(1.days),
                    autoSanitize = true,
                    autoFetch = true,
                    lastFetchedAt = null,
                    fetchIntervalMinutes = 60,
                    version = 0,
                )

            root.isTokenValid(currentToken, now) shouldBe true
            root.isTokenValid(previousToken, now) shouldBe true
            root.isTokenValid(FeedToken.generate(), now) shouldBe false
        }
    })
