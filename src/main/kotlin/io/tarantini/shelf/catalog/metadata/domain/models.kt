@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.metadata.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.processing.storage.StoragePath
import java.nio.file.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable data class ParsedSeries(val name: String, val index: Double? = null)

@Serializable
data class MetadataAggregate<S : PersistenceState>(
    val metadata: MetadataRoot<S>,
    val editions: List<EditionWithChapters<S>>,
)

typealias SavedMetadataAggregate = MetadataAggregate<PersistenceState.Persisted>

typealias NewMetadataAggregate = MetadataAggregate<PersistenceState.Unsaved>

@Serializable
data class EditionWithChapters<S : PersistenceState>(
    val edition: Edition<S>,
    val chapters: List<Chapter<S>> = emptyList(),
)

typealias SavedEditionWithChapters = EditionWithChapters<PersistenceState.Persisted>

typealias NewEditionWithChapters = EditionWithChapters<PersistenceState.Unsaved>

@Serializable
data class MetadataRoot<S : PersistenceState>(
    val id: Identity<S, MetadataId>,
    val bookId: BookId,
    val title: String,
    val description: String? = null,
    val publisher: String? = null,
    val published: Int? = null,
    val language: String? = null,
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
)

typealias SavedMetadataRoot = MetadataRoot<PersistenceState.Persisted>

typealias NewMetadataRoot = MetadataRoot<PersistenceState.Unsaved>

@Serializable
data class Edition<S : PersistenceState>(
    val id: Identity<S, EditionId>,
    val bookId: BookId,
    val format: BookFormat,
    val path: StoragePath,
    val fileHash: String? = null,
    val narrator: String? = null,
    val translator: String? = null,
    val isbn10: ISBN10? = null,
    val isbn13: ISBN13? = null,
    val asin: ASIN? = null,
    val pages: Long? = null,
    val totalTime: Double? = null,
    val size: Long,
)

typealias SavedEdition = Edition<PersistenceState.Persisted>

typealias NewEdition = Edition<PersistenceState.Unsaved>

@Serializable
data class Chapter<S : PersistenceState>(
    val id: Identity<S, ChapterId>,
    val editionId: Identity<S, EditionId>,
    val title: String,
    val startTime: Double? = null,
    val startPage: Int? = null,
    val endTime: Double? = null,
    val endPage: Int? = null,
    val index: Int? = null,
)

typealias SavedChapter = Chapter<PersistenceState.Persisted>

typealias NewChapter = Chapter<PersistenceState.Unsaved>

@Serializable
enum class BookFormat {
    EBOOK,
    AUDIOBOOK,
}

@Serializable
enum class MediaType {
    EBOOK,
    AUDIOBOOK,
}

private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

fun ebookMimeType(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.mobi8-ebook"
        "cbz" -> "application/x-cbz"
        "epub" -> "application/epub+zip"
        else -> {
            logger.warn { "Unknown ebook extension for path: $path. Defaulting to EPUB." }
            "application/epub+zip"
        }
    }

fun coverMimeType(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

@Serializable
data class ProcessedMetadata(
    val metadata: NewMetadataRoot,
    val edition: NewEdition,
    val chapters: List<NewChapter> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<ParsedSeries> = emptyList(),
    @Transient val coverImage: Path? = null,
)

/** Temporary bridge for parsers to return a complete metadata set. */
data class BookMetadata(
    val core: NewMetadataRoot,
    val edition: NewEdition,
    val chapters: List<NewChapter> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<ParsedSeries> = emptyList(),
)
