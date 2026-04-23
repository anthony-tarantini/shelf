package io.tarantini.shelf.integration.podcast.audible

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.integration.podcast.feed.ParsedFeed

/**
 * Interface for interacting with Audible's proprietary API.
 */
interface AudibleAdapter {
    /**
     * Fetch the user's entire Audible library (Audiobooks and Podcasts).
     */
    context(_: RaiseContext)
    suspend fun fetchLibrary(credentials: AudibleCredentials): List<AudibleTitle>

    /**
     * Fetch metadata for a specific Audible title and map it to our internal feed format.
     */
    context(_: RaiseContext)
    suspend fun getPodcastFeed(asin: String, credentials: AudibleCredentials): ParsedFeed

    /**
     * Get the license voucher for AAXC decryption if applicable.
     */
    context(_: RaiseContext)
    suspend fun getLicenseVoucher(asin: String, credentials: AudibleCredentials): AudibleLicenseVoucher?
}

data class AudibleCredentials(
    val cookies: String,
    val activationBytes: String? = null,
)

data class AudibleTitle(
    val asin: String,
    val title: String,
    val author: String,
    val type: AudibleTitleType,
    val imageUrl: String?,
)

enum class AudibleTitleType {
    PODCAST,
    AUDIOBOOK,
}

data class AudibleLicenseVoucher(
    val key: String,
    val iv: String,
)

fun audibleAdapter(): AudibleAdapter =
    DefaultAudibleAdapter(
        java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build()
    )

private class DefaultAudibleAdapter(private val httpClient: java.net.http.HttpClient) :
    AudibleAdapter {
    context(_: RaiseContext)
    override suspend fun fetchLibrary(credentials: AudibleCredentials): List<AudibleTitle> {
        // TODO: Implement Audible Library API call
        return emptyList()
    }

    context(_: RaiseContext)
    override suspend fun getPodcastFeed(asin: String, credentials: AudibleCredentials): ParsedFeed {
        // TODO: Implement Audible Episode Metadata API call
        throw NotImplementedError("Audible getPodcastFeed not implemented")
    }

    context(_: RaiseContext)
    override suspend fun getLicenseVoucher(
        asin: String,
        credentials: AudibleCredentials,
    ): AudibleLicenseVoucher? {
        // TODO: Implement Audible License API call for AAXC
        return null
    }
}
