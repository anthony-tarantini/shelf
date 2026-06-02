package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.FeedFetchFailed
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadedEpisodeImage(val path: Path, val extension: String, val size: Long)

interface EpisodeImageFetchAdapter {
    context(_: RaiseContext)
    suspend fun fetch(imageUrl: String): DownloadedEpisodeImage
}

fun episodeImageFetchAdapter(
    httpClient: HttpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build()
): EpisodeImageFetchAdapter = JavaNetEpisodeImageFetchAdapter(httpClient)

private class JavaNetEpisodeImageFetchAdapter(private val httpClient: HttpClient) :
    EpisodeImageFetchAdapter {
    context(_: RaiseContext)
    override suspend fun fetch(imageUrl: String): DownloadedEpisodeImage =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest.newBuilder(URI(imageUrl))
                    .GET()
                    .timeout(java.time.Duration.ofMinutes(2))
                    .header("Accept", "image/*")
                    .header("User-Agent", "ShelfPodcastBot/1.0")
                    .build()

            val response =
                catch({ httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream()) }) {
                    raise(FeedFetchFailed)
                }

            if (response.statusCode() !in 200..299) {
                response.body().close()
                raise(FeedFetchFailed)
            }

            val extension =
                guessImageExtension(
                    imageUrl = imageUrl,
                    contentType = response.headers().firstValue("Content-Type").orElse(null),
                )
            val tempFile = Files.createTempFile("shelf-podcast-cover-", ".tmp")
            response.body().use { input ->
                catch({
                    Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }) {
                    raise(FeedFetchFailed)
                }
            }
            val size = catch({ Files.size(tempFile) }) { raise(FeedFetchFailed) }
            DownloadedEpisodeImage(path = tempFile, extension = extension, size = size)
        }
}

private val IMAGE_EXTENSION_BY_MIME =
    mapOf(
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/pjpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
    )

private val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

private fun guessImageExtension(imageUrl: String, contentType: String?): String {
    val byType =
        contentType?.lowercase()?.substringBefore(';')?.trim()?.let { IMAGE_EXTENSION_BY_MIME[it] }
    if (byType != null) return byType

    val candidate =
        URI(imageUrl)
            .path
            ?.substringAfterLast('/', "")
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()

    return if (candidate in ALLOWED_IMAGE_EXTENSIONS) {
        if (candidate == "jpeg") "jpg" else candidate
    } else "jpg"
}
