package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.FeedAuthRequired
import io.tarantini.shelf.catalog.podcast.domain.FeedFetchFailed
import io.tarantini.shelf.catalog.podcast.domain.FeedRateLimited
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface FeedFetchCredentials {
    data class Basic(val username: String, val password: String) : FeedFetchCredentials

    data class Bearer(val token: String) : FeedFetchCredentials

    data class Headers(val values: Map<String, String>) : FeedFetchCredentials
}

interface FeedFetchAdapter {
    context(_: RaiseContext)
    suspend fun fetch(feedUrl: FeedUrl, credentials: FeedFetchCredentials? = null): String
}

fun feedFetchAdapter(
    httpClient: HttpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
): FeedFetchAdapter = JavaNetFeedFetchAdapter(httpClient)

private class JavaNetFeedFetchAdapter(private val httpClient: HttpClient) : FeedFetchAdapter {
    context(_: RaiseContext)
    override suspend fun fetch(feedUrl: FeedUrl, credentials: FeedFetchCredentials?): String =
        withContext(Dispatchers.IO) {
            val requestBuilder =
                HttpRequest.newBuilder(URI(feedUrl.value))
                    .GET()
                    .header(
                        "Accept",
                        "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9",
                    )
                    .header("User-Agent", "ShelfPodcastBot/1.0")

            when (credentials) {
                is FeedFetchCredentials.Basic -> {
                    val encoded =
                        java.util.Base64.getEncoder()
                            .encodeToString(
                                "${credentials.username}:${credentials.password}".toByteArray(UTF_8)
                            )
                    requestBuilder.header("Authorization", "Basic $encoded")
                }
                is FeedFetchCredentials.Bearer ->
                    requestBuilder.header("Authorization", "Bearer ${credentials.token}")
                is FeedFetchCredentials.Headers ->
                    credentials.values.forEach { (k, v) -> requestBuilder.header(k, v) }
                null -> {}
            }

            val response =
                catch({
                    httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString(UTF_8),
                    )
                }) {
                    raise(FeedFetchFailed)
                }

            when (response.statusCode()) {
                in 200..299 -> response.body()
                401,
                403 -> raise(FeedAuthRequired)
                429 -> {
                    val retryAfter =
                        response.headers().firstValue("Retry-After").orElse(null)?.toIntOrNull()
                    raise(FeedRateLimited(retryAfter))
                }
                else -> raise(FeedFetchFailed)
            }
        }
}
