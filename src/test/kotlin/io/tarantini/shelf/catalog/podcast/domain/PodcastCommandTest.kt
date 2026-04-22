@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

class PodcastCommandTest :
    StringSpec({
        "toCreateCommand maps request and applies defaults" {
            recover({
                val seriesId = Uuid.random().toString()
                val command =
                    PodcastRequest(
                            seriesId = seriesId,
                            feedUrl = "  https://example.com/feed.xml  ",
                            autoSanitize = null,
                            autoFetch = null,
                            fetchIntervalMinutes = null,
                        )
                        .toCreateCommand()

                command.seriesId.value.toString() shouldBe seriesId
                command.feedUrl.value shouldBe "https://example.com/feed.xml"
                command.autoSanitize shouldBe true
                command.autoFetch shouldBe true
                command.fetchIntervalMinutes shouldBe 60
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toUpdateCommand maps id and partial updates" {
            recover({
                val id = Uuid.random().toString()
                val command =
                    PodcastRequest(
                            autoSanitize = false,
                            autoFetch = true,
                            fetchIntervalMinutes = 30,
                        )
                        .toUpdateCommand(id)

                command.id.value.toString() shouldBe id
                command.autoSanitize shouldBe false
                command.autoFetch shouldBe true
                command.fetchIntervalMinutes shouldBe 30
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toCreateCommand rejects invalid fetch interval" {
            recover({
                PodcastRequest(
                        seriesId = Uuid.random().toString(),
                        feedUrl = "https://example.com/feed.xml",
                        fetchIntervalMinutes = 0,
                    )
                    .toCreateCommand()
                fail("Should have failed")
            }) {
                it shouldBe InvalidFetchInterval
            }
        }
    })
