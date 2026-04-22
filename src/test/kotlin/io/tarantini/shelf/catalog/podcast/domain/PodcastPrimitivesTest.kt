@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PodcastPrimitivesTest :
    StringSpec({
        "PodcastId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val podcastId = PodcastId(uuid.toString())
                podcastId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "PodcastId should fail for empty string" {
            recover({
                PodcastId("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyPodcastId
            }
        }

        "PodcastId should fail for invalid UUID string" {
            recover({
                PodcastId("not-a-uuid")
                fail("Should have failed")
            }) {
                it shouldBe InvalidPodcastId
            }
        }

        "FeedUrl should accept valid https url" {
            recover({
                val feedUrl = FeedUrl("https://example.com/feed.xml")
                feedUrl.value shouldBe "https://example.com/feed.xml"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "FeedUrl should fail for unsupported scheme" {
            recover({
                FeedUrl("ftp://example.com/feed.xml")
                fail("Should have failed")
            }) {
                it shouldBe InvalidFeedUrl
            }
        }

        "FeedToken should accept valid UUID token" {
            recover({
                val token = Uuid.random().toString()
                FeedToken(token).value shouldBe token
            }) {
                fail("Should not have failed: $it")
            }
        }

        "FeedToken should fail for invalid token string" {
            recover({
                FeedToken("not-a-token")
                fail("Should have failed")
            }) {
                it shouldBe InvalidFeedToken
            }
        }

        "Season should default null to zero" {
            recover({ Season(null).value shouldBe 0 }) { fail("Should not have failed: $it") }
        }

        "Season should fail when out of range" {
            recover({
                Season(1000)
                fail("Should have failed")
            }) {
                it shouldBe InvalidEpisodeIndex
            }
        }

        "EpisodeNumber should be valid in range" {
            recover({ EpisodeNumber(42).value shouldBe 42 }) { fail("Should not have failed: $it") }
        }

        "EpisodeNumber should fail for null input" {
            recover({
                EpisodeNumber(null)
                fail("Should have failed")
            }) {
                it shouldBe InvalidEpisodeIndex
            }
        }
    })
