package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.recover
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.podcast.domain.FeedAuthRequired
import io.tarantini.shelf.catalog.podcast.domain.FeedRateLimited
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import java.net.InetSocketAddress
import java.util.Base64

class FeedFetchAdapterTest :
    StringSpec({
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

        "FeedFetchAdapter should fetch XML body on 200" {
            withServer(
                block = { baseUrl ->
                    val adapter = feedFetchAdapter()
                    recover({
                        val body = adapter.fetch(FeedUrl.fromRaw("$baseUrl/ok.xml"))
                        body shouldBe "<rss><channel><title>OK</title></channel></rss>"
                    }) {
                        fail("Should not have failed: $it")
                    }
                },
                routes = {
                    createContext("/ok.xml") { exchange ->
                        val payload = "<rss><channel><title>OK</title></channel></rss>"
                        exchange.sendResponseHeaders(200, payload.toByteArray().size.toLong())
                        exchange.responseBody.use { it.write(payload.toByteArray()) }
                    }
                },
            )
        }

        "FeedFetchAdapter should map 401 to FeedAuthRequired" {
            withServer(
                block = { baseUrl ->
                    val adapter = feedFetchAdapter()
                    recover({
                        adapter.fetch(FeedUrl.fromRaw("$baseUrl/auth"))
                        fail("Should have failed")
                    }) {
                        it shouldBe FeedAuthRequired
                    }
                },
                routes = {
                    createContext("/auth") { exchange ->
                        exchange.sendResponseHeaders(401, -1)
                        exchange.close()
                    }
                },
            )
        }

        "FeedFetchAdapter should map 429 to FeedRateLimited with retry-after" {
            withServer(
                block = { baseUrl ->
                    val adapter = feedFetchAdapter()
                    recover({
                        adapter.fetch(FeedUrl.fromRaw("$baseUrl/rate"))
                        fail("Should have failed")
                    }) {
                        it shouldBe FeedRateLimited(120)
                    }
                },
                routes = {
                    createContext("/rate") { exchange ->
                        exchange.responseHeaders.add("Retry-After", "120")
                        exchange.sendResponseHeaders(429, -1)
                        exchange.close()
                    }
                },
            )
        }

        "FeedFetchAdapter should send basic auth when provided" {
            withServer(
                block = { baseUrl ->
                    val adapter = feedFetchAdapter()
                    recover({
                        val body =
                            adapter.fetch(
                                feedUrl = FeedUrl.fromRaw("$baseUrl/basic"),
                                credentials = FeedFetchCredentials.Basic("user", "pass"),
                            )
                        body shouldBe "ok"
                    }) {
                        fail("Should not have failed: $it")
                    }
                },
                routes = {
                    createContext("/basic") { exchange ->
                        val expected =
                            "Basic ${Base64.getEncoder().encodeToString("user:pass".toByteArray())}"
                        val auth = exchange.requestHeaders.getFirst("Authorization")
                        if (auth == expected) {
                            val payload = "ok"
                            exchange.sendResponseHeaders(200, payload.toByteArray().size.toLong())
                            exchange.responseBody.use { it.write(payload.toByteArray()) }
                        } else {
                            exchange.sendResponseHeaders(401, -1)
                            exchange.close()
                        }
                    }
                },
            )
        }
    })
