package io.tarantini.shelf.integration.podcast.audible

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.integration.podcast.feed.ParsedFeed
import kotlinx.serialization.Serializable
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client for talking to the Audible Sidecar container.
 */
interface AudibleSidecarClient {
    /**
     * Gets the login proxy URL from the sidecar.
     */
    context(_: RaiseContext)
    suspend fun getLoginUrl(): String

    /**
     * Finalizes the authentication with a callback URL.
     */
    context(_: RaiseContext)
    suspend fun finalizeAuth(callbackUrl: String)

    /**
     * Gets the connection status from the sidecar.
     */
    context(_: RaiseContext)
    suspend fun getAuthStatus(): AudibleAuthStatus

    /**
     * Fetches the user's Audible library from the sidecar.
     */
    context(_: RaiseContext)
    suspend fun fetchLibrary(): List<AudibleTitle>

    /**
     * Imports an Audible title as a podcast feed.
     */
    context(_: RaiseContext)
    suspend fun getPodcastFeed(asin: String): ParsedFeed
}

@Serializable
data class AudibleAuthStatus(
    val connected: Boolean,
    val username: String? = null
)

@Serializable
data class AudibleTitle(
    val asin: String,
    val title: String,
    val author: String,
    val type: String,
    val imageUrl: String? = null
)

fun audibleSidecarClient(baseUrl: String): AudibleSidecarClient =
    DefaultAudibleSidecarClient(
        baseUrl,
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    )

private class DefaultAudibleSidecarClient(
    private val baseUrl: String,
    private val httpClient: HttpClient
) : AudibleSidecarClient {
    private val json = Json { ignoreUnknownKeys = true }

    context(_: RaiseContext)
    override suspend fun getLoginUrl(): String {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/auth/login-url"))
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)
        
        return json.decodeFromString<LoginUrlResponse>(response.body()).url
    }

    context(_: RaiseContext)
    override suspend fun finalizeAuth(callbackUrl: String) {
        val payload = buildJsonObject {
            put("callbackUrl", callbackUrl)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/auth/finalize"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)
    }

    context(_: RaiseContext)
    override suspend fun getAuthStatus(): AudibleAuthStatus {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/auth/status"))
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)

        return json.decodeFromString(response.body())
    }

    context(_: RaiseContext)
    override suspend fun fetchLibrary(): List<AudibleTitle> {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/library"))
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)

        return json.decodeFromString(response.body())
    }

    context(_: RaiseContext)
    override suspend fun getPodcastFeed(asin: String): ParsedFeed {
        val request = HttpRequest.newBuilder()
            .uri(URI("$baseUrl/library/$asin/feed"))
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)

        return json.decodeFromString(response.body())
    }
}

@Serializable
private data class LoginUrlResponse(val url: String)
