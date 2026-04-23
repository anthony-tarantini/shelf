package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed
import io.tarantini.shelf.integration.podcast.feed.ParsedEpisode
import io.tarantini.shelf.integration.podcast.feed.ParsedFeed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    )

private class DefaultAudibleAdapter(private val httpClient: HttpClient) :
    AudibleAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    context(_: RaiseContext)
    override suspend fun fetchLibrary(credentials: AudibleCredentials): List<AudibleTitle> {
        val token = extractToken(credentials.cookies) ?: raise(AudibleAuthFailed)
        
        val request = HttpRequest.newBuilder()
            .uri(URI("https://api.audible.com/1.0/library?num_results=1000&usage_type=DirectPurchase&sort_by=-PurchaseDate"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)

        val libraryResponse = json.decodeFromString<AudibleLibraryResponse>(response.body())
        return libraryResponse.items.map { it.toDomain() }
    }

    context(_: RaiseContext)
    override suspend fun getPodcastFeed(asin: String, credentials: AudibleCredentials): ParsedFeed {
        val token = extractToken(credentials.cookies) ?: raise(AudibleAuthFailed)
        
        val request = HttpRequest.newBuilder()
            .uri(URI("https://api.audible.com/1.0/catalog/products/$asin?response_groups=product_desc,product_extended_attrs,product_plan_details,product_plans,product_relationships,review_attrs,contributors,media,sample,content,price,category_ladders,claim_code_attrs,is_orderable,product_attrs"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val response = catch({
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }) { raise(AudibleAuthFailed) }

        if (response.statusCode() != 200) raise(AudibleAuthFailed)

        val productResponse = json.decodeFromString<AudibleProductResponse>(response.body())
        return productResponse.product.toParsedFeed()
    }

    private fun extractToken(cookies: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("amzn-token=") }
            ?.substringAfter("amzn-token=")
    }

    context(_: RaiseContext)
    override suspend fun getLicenseVoucher(
        asin: String,
        credentials: AudibleCredentials,
    ): AudibleLicenseVoucher? {
        // TODO: Implement actual voucher fetching for AAXC
        return null
    }
}

@Serializable
private data class AudibleLibraryResponse(
    val items: List<AudibleLibraryItem>
)

@Serializable
private data class AudibleLibraryItem(
    val asin: String,
    val title: String,
    val authors: List<AudibleContributor>? = null,
    @SerialName("content_metadata") val contentMetadata: AudibleContentMetadata? = null,
    @SerialName("product_images") val images: Map<String, String>? = null,
) {
    fun toDomain() = AudibleTitle(
        asin = asin,
        title = title,
        author = authors?.firstOrNull()?.name ?: "Unknown",
        type = if (contentMetadata?.isPodcast == true) AudibleTitleType.PODCAST else AudibleTitleType.AUDIOBOOK,
        imageUrl = images?.values?.lastOrNull()
    )
}

@Serializable
private data class AudibleContentMetadata(
    @SerialName("is_podcast") val isPodcast: Boolean? = false
)

@Serializable
private data class AudibleContributor(val name: String)

@Serializable
private data class AudibleProductResponse(
    val product: AudibleProduct
)

@Serializable
private data class AudibleProduct(
    val asin: String,
    val title: String,
    @SerialName("product_images") val images: Map<String, String>? = null,
    val authors: List<AudibleContributor>? = null,
    val publisher_summary: String? = null,
) {
    fun toParsedFeed() = ParsedFeed(
        title = title,
        description = publisher_summary,
        imageUrl = images?.values?.lastOrNull(),
        episodes = listOf(
            ParsedEpisode(
                guid = asin,
                title = title,
                description = publisher_summary,
                audioUrl = "audible://$asin",
                duration = null,
                publishedAt = null,
                season = null,
                episode = null,
                imageUrl = images?.values?.lastOrNull()
            )
        )
    )
}
