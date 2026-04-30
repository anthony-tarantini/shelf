@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.processing.storage.FileBytes
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class PodcastRssApiTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "rss endpoints should serve feed xml and episode audio by token" {
            testApp { client ->
                val token = FeedToken.generate()
                val audioPath = StoragePath.fromRaw("podcasts/rss-test/episode-1.mp3")
                var episodeId = ""

                testWithDeps { deps ->
                    recover({
                        val seriesId =
                            deps.database.seriesQueries.insert(unique("rss-series")).executeAsOne()
                        val podcastId =
                            deps.database.podcastQueries
                                .insert(
                                    seriesId = seriesId,
                                    feedUrl = FeedUrl.fromRaw("https://example.com/rss.xml"),
                                    feedToken = token,
                                    autoSanitize = false,
                                    autoFetch = false,
                                    fetchIntervalMinutes = 60,
                                )
                                .executeAsOne()

                        val insertedEpisodeId =
                            deps.database.podcastQueries
                                .insertEpisode(
                                    podcastId = podcastId,
                                    title = "RSS Episode 1",
                                    coverPath = null,
                                    audioPath = audioPath,
                                    audioSize = 4,
                                    totalTime = 60.0,
                                    season = 1,
                                    episode = 1,
                                    publishedAt = java.time.OffsetDateTime.now(),
                                )
                                .executeAsOne()
                        episodeId = insertedEpisodeId.value.toString()
                        deps.database.podcastQueries
                            .claimEpisodeGuid(
                                podcastId = podcastId,
                                guid = "rss-guid-1",
                                episodeId = insertedEpisodeId,
                            )
                            .executeAsOne()
                        deps.database.podcastQueries
                            .selectRssEpisodesByPodcastId(podcastId)
                            .executeAsList()
                            .size shouldBe 1

                        deps.storageService.save(audioPath, FileBytes(byteArrayOf(1, 2, 3, 4)))
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val feedResponse = client.get("/rss/podcasts/${token.value}")
                feedResponse.status shouldBe HttpStatusCode.OK
                val etag = feedResponse.headers[HttpHeaders.ETag]
                (etag != null) shouldBe true
                val feedBody = feedResponse.bodyAsText()
                feedBody.contains("<rss version=\"2.0\"") shouldBe true
                feedBody.contains("<item>") shouldBe true
                feedBody.contains("/rss/podcasts/${token.value}/episodes/$episodeId/audio") shouldBe
                    true

                val audioResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio")
                audioResponse.status shouldBe HttpStatusCode.OK
                audioResponse.headers[HttpHeaders.ContentType] shouldBe "audio/mpeg"

                val rangedResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio") {
                        header(HttpHeaders.Range, "bytes=1-2")
                    }
                rangedResponse.status shouldBe HttpStatusCode.PartialContent
                rangedResponse.headers[HttpHeaders.ContentRange] shouldBe "bytes 1-2/4"
                rangedResponse.headers[HttpHeaders.AcceptRanges] shouldBe "bytes"
                rangedResponse.body<ByteArray>().toList() shouldContainExactly listOf(2, 3)

                val invalidRangeResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio") {
                        header(HttpHeaders.Range, "bytes=99-100")
                    }
                invalidRangeResponse.status shouldBe HttpStatusCode.RequestedRangeNotSatisfiable
                invalidRangeResponse.headers[HttpHeaders.ContentRange] shouldBe "bytes */4"
            }
        }

        "rss feed should return 304 when ETag matches" {
            testApp { client ->
                val token = FeedToken.generate()

                testWithDeps { deps ->
                    val seriesId =
                        deps.database.seriesQueries.insert(unique("rss-etag-series")).executeAsOne()
                    deps.database.podcastQueries
                        .insert(
                            seriesId = seriesId,
                            feedUrl = FeedUrl.fromRaw("https://example.com/rss.xml"),
                            feedToken = token,
                            autoSanitize = false,
                            autoFetch = false,
                            fetchIntervalMinutes = 60,
                        )
                        .executeAsOne()
                }

                val first = client.get("/rss/podcasts/${token.value}")
                first.status shouldBe HttpStatusCode.OK
                val etag = first.headers[HttpHeaders.ETag] ?: fail("Missing ETag")

                val second =
                    client.get("/rss/podcasts/${token.value}") {
                        header(HttpHeaders.IfNoneMatch, etag)
                    }
                second.status shouldBe HttpStatusCode.NotModified
            }
        }

        "rss audio endpoint should support suffix ranges and cap oversized ranges" {
            testApp { client ->
                val token = FeedToken.generate()
                val audioPath = StoragePath.fromRaw("podcasts/rss-test/episode-range-large.mp3")
                var episodeId = ""

                testWithDeps { deps ->
                    recover({
                        val seriesId =
                            deps.database.seriesQueries
                                .insert(unique("rss-range-series"))
                                .executeAsOne()
                        val podcastId =
                            deps.database.podcastQueries
                                .insert(
                                    seriesId = seriesId,
                                    feedUrl = FeedUrl.fromRaw("https://example.com/rss.xml"),
                                    feedToken = token,
                                    autoSanitize = false,
                                    autoFetch = false,
                                    fetchIntervalMinutes = 60,
                                )
                                .executeAsOne()

                        val insertedEpisodeId =
                            deps.database.podcastQueries
                                .insertEpisode(
                                    podcastId = podcastId,
                                    title = "RSS Range Episode",
                                    coverPath = null,
                                    audioPath = audioPath,
                                    audioSize = 11L * 1024 * 1024,
                                    totalTime = 120.0,
                                    season = 1,
                                    episode = 1,
                                    publishedAt = java.time.OffsetDateTime.now(),
                                )
                                .executeAsOne()
                        episodeId = insertedEpisodeId.value.toString()
                        deps.database.podcastQueries
                            .claimEpisodeGuid(
                                podcastId = podcastId,
                                guid = "rss-guid-range",
                                episodeId = insertedEpisodeId,
                            )
                            .executeAsOne()

                        val bytes = ByteArray(11 * 1024 * 1024) { (it % 251).toByte() }
                        deps.storageService.save(audioPath, FileBytes(bytes))
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val suffix =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio") {
                        header(HttpHeaders.Range, "bytes=-3")
                    }
                suffix.status shouldBe HttpStatusCode.PartialContent
                suffix.headers[HttpHeaders.ContentRange] shouldBe "bytes 11534333-11534335/11534336"
                suffix.body<ByteArray>().size shouldBe 3

                val openEnded =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio") {
                        header(HttpHeaders.Range, "bytes=1024-")
                    }
                openEnded.status shouldBe HttpStatusCode.PartialContent
                openEnded.headers[HttpHeaders.ContentRange] shouldBe "bytes 1024-10486783/11534336"
                openEnded.body<ByteArray>().size shouldBe 10 * 1024 * 1024

                val invalidSyntax =
                    client.get("/rss/podcasts/${token.value}/episodes/$episodeId/audio") {
                        header(HttpHeaders.Range, "bytes=0-1,3-4")
                    }
                invalidSyntax.status shouldBe HttpStatusCode.RequestedRangeNotSatisfiable
                invalidSyntax.headers[HttpHeaders.ContentRange] shouldBe "bytes */11534336"
            }
        }
    })
