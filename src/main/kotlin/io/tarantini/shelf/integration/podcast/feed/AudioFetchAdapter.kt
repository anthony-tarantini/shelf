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

data class DownloadedEpisodeAudio(val path: Path, val extension: String, val size: Long)

interface EpisodeAudioFetchAdapter {
    context(_: RaiseContext)
    suspend fun fetch(audioUrl: String): DownloadedEpisodeAudio
}

fun episodeAudioFetchAdapter(
    httpClient: HttpClient =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
): EpisodeAudioFetchAdapter = JavaNetEpisodeAudioFetchAdapter(httpClient)

private class JavaNetEpisodeAudioFetchAdapter(private val httpClient: HttpClient) :
    EpisodeAudioFetchAdapter {
    context(_: RaiseContext)
    override suspend fun fetch(audioUrl: String): DownloadedEpisodeAudio =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest.newBuilder(URI(audioUrl))
                    .GET()
                    .header("Accept", "audio/*,application/octet-stream;q=0.9")
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
                guessExtension(
                    audioUrl = audioUrl,
                    contentType = response.headers().firstValue("Content-Type").orElse(null),
                )
            val tempFile = Files.createTempFile("shelf-podcast-episode-", ".tmp")
            response.body().use { input ->
                catch({
                    Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }) {
                    raise(FeedFetchFailed)
                }
            }
            val size = catch({ Files.size(tempFile) }) { raise(FeedFetchFailed) }
            DownloadedEpisodeAudio(path = tempFile, extension = extension, size = size)
        }
}

private fun guessExtension(audioUrl: String, contentType: String?): String {
    val byType =
        contentType?.lowercase()?.substringBefore(';')?.trim()?.let {
            when (it) {
                "audio/mpeg",
                "audio/mp3" -> "mp3"
                "audio/mp4",
                "audio/x-m4a" -> "m4a"
                "audio/x-m4b" -> "m4b"
                "audio/aac" -> "aac"
                "audio/ogg" -> "ogg"
                "audio/flac" -> "flac"
                "audio/wav",
                "audio/x-wav" -> "wav"
                else -> null
            }
        }
    if (byType != null) return byType

    val candidate =
        URI(audioUrl)
            .path
            ?.substringAfterLast('/', "")
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()

    return if (candidate.matches(Regex("[a-z0-9]{2,5}"))) candidate else "m4b"
}
