@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.processing.audiobook

import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.app.jsonParser
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

private val logger = KotlinLogging.logger {}

data class PodcastEpisodeProbe(
    val totalSeconds: Double?,
    val description: String?,
    val author: String?,
    val publishedAt: OffsetDateTime?,
)

suspend fun probePodcastEpisode(path: Path): PodcastEpisodeProbe? =
    withContext(Dispatchers.IO) {
        val process =
            runCatching { ffprobe(path).redirectErrorStream(false).start() }
                .onFailure { logger.warn(it) { "ffprobe start failed for $path" } }
                .getOrNull() ?: return@withContext null

        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            logger.warn { "ffprobe timed out for $path" }
            return@withContext null
        }
        if (process.exitValue() != 0) {
            logger.warn { "ffprobe non-zero exit ${process.exitValue()} for $path" }
            return@withContext null
        }

        val output =
            runCatching {
                    process.inputStream.use { jsonParser.decodeFromStream<FfprobeOutput>(it) }
                }
                .getOrNull() ?: return@withContext null

        val tags = output.format?.tags?.mapKeys { it.key.lowercase() } ?: emptyMap()
        val duration = output.format?.duration?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val description =
            (tags["comment"] ?: tags["description"] ?: tags["synopsis"])?.trim()?.takeIf {
                it.isNotBlank()
            }
        val author =
            (tags["artist"] ?: tags["album_artist"] ?: tags["author"])?.trim()?.takeIf {
                it.isNotBlank()
            }
        val publishedAt =
            (tags["date"] ?: tags["year"])?.trim()?.takeIf { it.isNotBlank() }?.let(::parseDate)

        PodcastEpisodeProbe(
            totalSeconds = duration,
            description = description,
            author = author,
            publishedAt = publishedAt,
        )
    }

private val DATE_FORMATS =
    listOf(
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy"),
    )

private fun parseDate(raw: String): OffsetDateTime? {
    for (fmt in DATE_FORMATS) {
        runCatching {
            return OffsetDateTime.parse(raw, fmt)
        }
        runCatching {
            return java.time.LocalDateTime.parse(raw, fmt).atOffset(ZoneOffset.UTC)
        }
        runCatching {
            return java.time.LocalDate.parse(raw, fmt).atStartOfDay().atOffset(ZoneOffset.UTC)
        }
    }
    val yearOnly = raw.take(4).toIntOrNull()?.takeIf { it in 1000..9999 }
    if (yearOnly != null) {
        return java.time.LocalDate.of(yearOnly, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC)
    }
    return null
}
