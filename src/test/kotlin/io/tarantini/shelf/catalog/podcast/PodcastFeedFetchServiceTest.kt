@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.recover
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.integration.podcast.feed.episodeAudioFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.feedFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.feedParser
import java.net.InetSocketAddress
import kotlin.uuid.ExperimentalUuidApi

class PodcastFeedFetchServiceTest :
    IntegrationSpec({
        suspend fun withServer(
            block: suspend (baseUrl: String) -> Unit,
            routes: HttpServer.() -> Unit,
        ) {
            val server = HttpServer.create(InetSocketAddress(0), 0)
            server.routes()
            server.start()
            try {
                val baseUrl = "http://127.0.0.1:${server.address.port}"
                block(baseUrl)
            } finally {
                server.stop(0)
            }
        }

        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "feed fetch service ingests episodes idempotently" {
            testWithDeps { deps ->
                withServer(
                    block = { baseUrl ->
                        val mutationRepo = podcastMutationRepository(deps.database.podcastQueries)
                        val readRepo = podcastReadRepository(deps.database.podcastQueries)
                        val feedService =
                            podcastFeedFetchService(
                                readRepository = readRepo,
                                mutationRepository = mutationRepo,
                                podcastQueries = deps.database.podcastQueries,
                                bookQueries = deps.database.bookQueries,
                                metadataQueries = deps.database.metadataQueries,
                                storageService = deps.storageService,
                                feedFetchAdapter = feedFetchAdapter(),
                                feedParser = feedParser(),
                                audioFetchAdapter = episodeAudioFetchAdapter(),
                            )

                        val seriesId =
                            deps.database.seriesQueries
                                .insert(unique("podcast-series"))
                                .executeAsOne()

                        val podcast =
                            recover({
                                mutationRepo.createPodcast(
                                    PodcastRoot.new(
                                        seriesId = seriesId,
                                        feedUrl = FeedUrl.fromRaw("$baseUrl/feed.xml"),
                                        feedToken = FeedToken.generate(),
                                    )
                                )
                            }) {
                                fail("Should not have failed when creating podcast: $it")
                            }

                        recover({
                            feedService.fetchPodcast(podcast.id.id)
                            feedService.fetchPodcast(podcast.id.id)
                        }) {
                            fail("Should not have failed: $it")
                        }

                        recover({
                            val summary = readRepo.getPodcastSummaryById(podcast.id.id)
                            summary.episodeCount shouldBe 2L
                            readRepo.guidExists(podcast.id.id, "guid-1") shouldBe true
                            readRepo.guidExists(podcast.id.id, "guid-2") shouldBe true
                            readRepo.getMaxEpisodeForSeason(podcast.id.id, 0) shouldBe 2
                        }) {
                            fail("Validation failed: $it")
                        }
                    },
                    routes = {
                        createContext("/feed.xml") { exchange ->
                            val payload =
                                """
                                <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                                  <channel>
                                    <title>Demo Feed</title>
                                    <item>
                                      <guid>guid-1</guid>
                                      <title>Episode One</title>
                                      <enclosure url="http://127.0.0.1:${address.port}/audio1.mp3" type="audio/mpeg" />
                                    </item>
                                    <item>
                                      <guid>guid-2</guid>
                                      <title>Episode Two</title>
                                      <itunes:episode>1</itunes:episode>
                                      <enclosure url="http://127.0.0.1:${address.port}/audio2.mp3" type="audio/mpeg" />
                                    </item>
                                  </channel>
                                </rss>
                                """
                                    .trimIndent()
                            val bytes = payload.toByteArray()
                            exchange.responseHeaders.add("Content-Type", "application/rss+xml")
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.use { it.write(bytes) }
                        }

                        createContext("/audio1.mp3") { exchange ->
                            val payload = byteArrayOf(1, 2, 3, 4)
                            exchange.responseHeaders.add("Content-Type", "audio/mpeg")
                            exchange.sendResponseHeaders(200, payload.size.toLong())
                            exchange.responseBody.use { it.write(payload) }
                        }

                        createContext("/audio2.mp3") { exchange ->
                            val payload = byteArrayOf(5, 6, 7, 8)
                            exchange.responseHeaders.add("Content-Type", "audio/mpeg")
                            exchange.sendResponseHeaders(200, payload.size.toLong())
                            exchange.responseBody.use { it.write(payload) }
                        }
                    },
                )
            }
        }
    })
