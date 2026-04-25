package io.tarantini.shelf.integration.podcast.sanitization

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AppError
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Client for talking to the MinusPod sidecar container. */
interface MinusPodAdapter {
    /** Registers a new feed with MinusPod. */
    context(_: RaiseContext)
    suspend fun registerFeed(feedUrl: String): MinusPodFeed

    /** Lists episodes and their processing status from MinusPod. */
    context(_: RaiseContext)
    suspend fun listEpisodes(slug: String): List<MinusPodEpisode>

    /** Triggers a manual re-processing of a feed. */
    context(_: RaiseContext)
    suspend fun triggerReprocess(slug: String)
}

@Serializable
data class MinusPodFeed(
    val slug: String,
    val title: String,
    val originalUrl: String,
    val proxyUrl: String,
)

@Serializable
data class MinusPodEpisode(
    val guid: String,
    val title: String,
    val status: String, // PROCESSING, FINISHED, FAILED
    val originalUrl: String,
    val proxyUrl: String? = null,
)

fun minusPodAdapter(baseUrl: String, adminPassword: String?): MinusPodAdapter =
    DefaultMinusPodAdapter(
        baseUrl,
        adminPassword,
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
    )

private class DefaultMinusPodAdapter(
    private val baseUrl: String,
    private val adminPassword: String?,
    private val httpClient: HttpClient,
) : MinusPodAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    context(_: RaiseContext)
    override suspend fun registerFeed(feedUrl: String): MinusPodFeed {
        val payload = buildJsonObject { put("url", feedUrl) }

        val builder =
            HttpRequest.newBuilder()
                .uri(URI("$baseUrl/api/v1/feeds"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
        adminPassword?.let { builder.header("X-Admin-Password", it) }
        val request = builder.build()

        val response =
            withContext(Dispatchers.IO) {
                catch({ httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }) {
                    raise(MinusPodError("Network error"))
                }
            }

        if (response.statusCode() != 201 && response.statusCode() != 200)
            raise(MinusPodError("API error: ${response.statusCode()}"))

        return json.decodeFromString(response.body())
    }

    context(_: RaiseContext)
    override suspend fun listEpisodes(slug: String): List<MinusPodEpisode> {
        val builder =
            HttpRequest.newBuilder().uri(URI("$baseUrl/api/v1/feeds/$slug/episodes")).GET()
        adminPassword?.let { builder.header("X-Admin-Password", it) }
        val request = builder.build()

        val response =
            withContext(Dispatchers.IO) {
                catch({ httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }) {
                    raise(MinusPodError("Network error"))
                }
            }

        if (response.statusCode() != 200)
            raise(MinusPodError("API error: ${response.statusCode()}"))

        return json.decodeFromString(response.body())
    }

    context(_: RaiseContext)
    override suspend fun triggerReprocess(slug: String) {
        val builder =
            HttpRequest.newBuilder()
                .uri(URI("$baseUrl/api/v1/feeds/$slug/reprocess-all"))
                .POST(HttpRequest.BodyPublishers.noBody())
        adminPassword?.let { builder.header("X-Admin-Password", it) }
        val request = builder.build()

        val response =
            withContext(Dispatchers.IO) {
                catch({ httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }) {
                    raise(MinusPodError("Network error"))
                }
            }

        if (response.statusCode() != 204 && response.statusCode() != 200)
            raise(MinusPodError("API error: ${response.statusCode()}"))
    }
}

class MinusPodError(val msg: String) : AppError
