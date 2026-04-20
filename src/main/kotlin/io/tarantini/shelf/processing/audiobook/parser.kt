@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.audiobook

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import arrow.fx.coroutines.ResourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.jsonParser
import io.tarantini.shelf.app.processResource
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.toYearOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

private val logger = KotlinLogging.logger {}

interface AudiobookParser {
    context(_: RaiseContext)
    suspend fun parse(
        scope: ResourceScope,
        path: Path,
        extension: String,
        fileName: String,
        bookId: BookId,
    ): Pair<BookMetadata, Path?>
}

fun audiobookParser() =
    object : AudiobookParser {
        context(_: RaiseContext)
        override suspend fun parse(
            scope: ResourceScope,
            path: Path,
            extension: String,
            fileName: String,
            bookId: BookId,
        ): Pair<BookMetadata, Path?> {
            return withContext(Dispatchers.IO) {
                val data = runFfprobe(scope, path)
                val coverImage = extractCover(scope, data, path)
                val metadata = extractMetadata(data, path, fileName, bookId)

                metadata to coverImage
            }
        }
    }

context(_: RaiseContext)
private suspend fun runFfprobe(scope: ResourceScope, path: Path): FfprobeOutput =
    catch({
        val process = scope.processResource(ffprobe(path))
        if (process.waitFor(10, TimeUnit.SECONDS)) {
            if (process.exitValue() != 0) {
                logger.error { "ffprobe failed with exit code ${process.exitValue()}" }
                raise(InvalidAudioFile)
            }
        } else {
            logger.error { "ffprobe timed out after 10s" }
            process.destroyForcibly()
            raise(InvalidAudioFile)
        }

        process.inputStream.use { stream -> jsonParser.decodeFromStream<FfprobeOutput>(stream) }
    }) { e ->
        logger.error(e) { "Failed to parse ffprobe output" }
        raise(InvalidAudioFile)
    }

private suspend fun extractCover(scope: ResourceScope, data: FfprobeOutput, path: Path) =
    withContext(Dispatchers.IO) {
        val hasCover =
            data.streams.any { stream ->
                stream.disposition?.get("attached_pic") == 1 || stream.codecType == "video"
            }

        if (!hasCover) return@withContext null

        val tempCover =
            scope.install({ Files.createTempFile("shelf-audio-cover-", ".jpg") }) { p, _ ->
                p.deleteIfExists()
            }

        logger.debug { "Attempting to extract cover image with ffmpeg" }
        val ffmpegProcess = catch({ scope.processResource(ffmpeg(path, tempCover)) }) { null }

        if (ffmpegProcess != null) {
            if (ffmpegProcess.waitFor(30, TimeUnit.SECONDS)) {
                if (ffmpegProcess.exitValue() == 0) {
                    logger.debug { "Cover image extracted successfully" }
                    return@withContext tempCover
                } else {
                    logger.warn {
                        "ffmpeg failed to extract cover, exit code: ${ffmpegProcess.exitValue()}"
                    }
                }
            } else {
                logger.error { "ffmpeg cover extraction timed out after 30s" }
                ffmpegProcess.destroyForcibly()
            }
        }
        return@withContext null
    }

context(_: RaiseContext)
private fun extractMetadata(
    data: FfprobeOutput,
    path: Path,
    fileName: String,
    bookId: BookId,
): BookMetadata {
    val tags = data.format?.tags?.mapKeys { it.key.lowercase() } ?: emptyMap()

    val title = tags["title"]?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast(".")
    val description = tags["comment"]?.takeIf { it.isNotBlank() }
    val publisher =
        tags["publisher"]?.takeIf { it.isNotBlank() }
            ?: tags["record_label"]?.takeIf { it.isNotBlank() }
    val publishDate =
        tags["date"]?.takeIf { it.isNotBlank() } ?: tags["year"]?.takeIf { it.isNotBlank() }
    val narrator =
        tags["narrator"]?.takeIf { it.isNotBlank() }
            ?: tags["composer"]?.takeIf { it.isNotBlank() }
            ?: tags["arranger"]?.takeIf { it.isNotBlank() }

    val genres =
        tags["genre"]
            ?.split("/")
            ?.flatMap { it.split(";") }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()

    val authors =
        (tags["artist"] ?: tags["album_artist"])
            ?.split("/")
            ?.flatMap { it.split(";") }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()

    val core =
        NewMetadataRoot(
            id = Identity.Unsaved,
            bookId = bookId,
            title = title,
            description = description,
            publisher = publisher,
            published = publishDate?.toYearOrNull(),
            language = tags["language"]?.takeIf { it.isNotBlank() },
            genres = genres,
            moods = emptyList(),
        )

    val edition =
        NewEdition(
            id = Identity.Unsaved,
            bookId = bookId,
            format = BookFormat.AUDIOBOOK,
            narrator = narrator,
            path = StoragePath.fromRaw("imports/audio").resolve(StoragePath.safeSegment(fileName)),
            totalTime = data.format?.duration?.toDoubleOrNull() ?: 0.0,
            size = Files.size(path),
        )

    val chaptersList =
        data.chapters.map { ffChapter ->
            NewChapter(
                id = Identity.Unsaved,
                editionId = Identity.Unsaved,
                title = ffChapter.title,
                startTime = ffChapter.startTime,
                endTime = ffChapter.endTime,
                index = ffChapter.id.toInt(),
            )
        }

    return BookMetadata(
        core = core,
        edition = edition,
        chapters = chaptersList,
        authors = authors,
        series = emptyList(),
    )
}
