package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.FeedAuthRequired
import io.tarantini.shelf.catalog.podcast.domain.FeedFetchFailed
import io.tarantini.shelf.catalog.podcast.domain.FeedRateLimited
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FEED_BYTES = 10L * 1024 * 1024 // 10 MB

sealed interface FeedFetchCredentials {
    data class Basic(val username: String, val password: String) : FeedFetchCredentials

    data class Bearer(val token: String) : FeedFetchCredentials

    data class Headers(val values: Map<String, String>) : FeedFetchCredentials

    data class AudibleCookie(val cookie: String) : FeedFetchCredentials

    data class AudibleActivationBytes(val bytes: String) : FeedFetchCredentials
}

sealed interface FeedFetchResponse {
    data object NotModified : FeedFetchResponse

    data class Body(val xml: String, val etag: String?, val lastModified: String?) :
        FeedFetchResponse
}

interface FeedFetchAdapter {
    context(_: RaiseContext)
    suspend fun fetch(feedUrl: FeedUrl, credentials: FeedFetchCredentials? = null): String

    context(_: RaiseContext)
    suspend fun fetchConditional(
        feedUrl: FeedUrl,
        ifNoneMatch: String? = null,
        ifModifiedSince: String? = null,
        credentials: FeedFetchCredentials? = null,
    ): FeedFetchResponse
}

fun feedFetchAdapter(
    httpClient: HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
): FeedFetchAdapter = JavaNetFeedFetchAdapter(httpClient)

private class JavaNetFeedFetchAdapter(private val httpClient: HttpClient) : FeedFetchAdapter {
    context(_: RaiseContext)
    override suspend fun fetch(feedUrl: FeedUrl, credentials: FeedFetchCredentials?): String =
        when (val r = fetchConditional(feedUrl, null, null, credentials)) {
            is FeedFetchResponse.Body -> r.xml
            FeedFetchResponse.NotModified -> raise(FeedFetchFailed)
        }

    context(_: RaiseContext)
    override suspend fun fetchConditional(
        feedUrl: FeedUrl,
        ifNoneMatch: String?,
        ifModifiedSince: String?,
        credentials: FeedFetchCredentials?,
    ): FeedFetchResponse =
        withContext(Dispatchers.IO) {
            val requestBuilder =
                HttpRequest.newBuilder(URI(feedUrl.value))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .header(
                        "Accept",
                        "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9",
                    )
                    .header("User-Agent", "ShelfPodcastBot/1.0")

            if (!ifNoneMatch.isNullOrBlank()) requestBuilder.header("If-None-Match", ifNoneMatch)
            if (!ifModifiedSince.isNullOrBlank())
                requestBuilder.header("If-Modified-Since", ifModifiedSince)

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
                is FeedFetchCredentials.AudibleCookie,
                is FeedFetchCredentials.AudibleActivationBytes,
                null -> {}
            }

            val response =
                catch({
                    httpClient.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofInputStream(),
                    )
                }) {
                    raise(FeedFetchFailed)
                }

            val contentLength =
                response.headers().firstValueAsLong("Content-Length").let {
                    if (it.isPresent) it.asLong else null
                }
            if (contentLength != null && contentLength > MAX_FEED_BYTES) {
                response.body().close()
                raise(FeedFetchFailed)
            }

            when (response.statusCode()) {
                304 -> {
                    response.body().close()
                    FeedFetchResponse.NotModified
                }
                in 200..299 -> {
                    val xml = readBounded(response)
                    val etag = response.headers().firstValue("ETag").orElse(null)
                    val lastModified = response.headers().firstValue("Last-Modified").orElse(null)
                    FeedFetchResponse.Body(xml = xml, etag = etag, lastModified = lastModified)
                }
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

    context(_: RaiseContext)
    private fun readBounded(response: HttpResponse<java.io.InputStream>): String {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        var totalRead = 0L
        response.body().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                totalRead += n
                if (totalRead > MAX_FEED_BYTES) raise(FeedFetchFailed)
                output.write(buffer, 0, n)
            }
        }
        return output.toString(UTF_8)
    }
}
