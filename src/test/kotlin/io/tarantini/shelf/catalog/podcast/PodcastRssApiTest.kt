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
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
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
                var bookId = ""

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

                        val b =
                            deps.database.bookQueries.insert("RSS Episode 1", null).executeAsOne()
                        bookId = b.value.toString()

                        deps.database.metadataQueries
                            .insertMetadata(
                                bookId = b,
                                title = "RSS Episode 1",
                                description = "Episode description",
                                publisher = null,
                                published = 2026,
                                language = null,
                            )
                            .executeAsOne()
                        deps.database.metadataQueries
                            .insertEdition(
                                bookId = b,
                                format = BookFormat.AUDIOBOOK,
                                path = audioPath,
                                fileHash = null,
                                narrator = null,
                                translator = null,
                                isbn10 = null,
                                isbn13 = null,
                                asin = null,
                                pages = null,
                                totalTime = 60.0,
                                size = 4,
                            )
                            .executeAsOne()
                        deps.database.podcastQueries.insertEpisodeOrdering(
                            podcastId = podcastId,
                            bookId = b,
                            season = 1,
                            episode = 1,
                            publishedAt = java.time.OffsetDateTime.now(),
                        )
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
                feedBody.contains("/rss/podcasts/${token.value}/episodes/$bookId/audio") shouldBe
                    true

                val audioResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$bookId/audio")
                audioResponse.status shouldBe HttpStatusCode.OK
                audioResponse.headers[HttpHeaders.ContentType] shouldBe "audio/mpeg"

                val rangedResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$bookId/audio") {
                        header(HttpHeaders.Range, "bytes=1-2")
                    }
                rangedResponse.status shouldBe HttpStatusCode.PartialContent
                rangedResponse.headers[HttpHeaders.ContentRange] shouldBe "bytes 1-2/4"
                rangedResponse.headers[HttpHeaders.AcceptRanges] shouldBe "bytes"
                rangedResponse.body<ByteArray>().toList() shouldContainExactly listOf(2, 3)

                val invalidRangeResponse =
                    client.get("/rss/podcasts/${token.value}/episodes/$bookId/audio") {
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
    })
