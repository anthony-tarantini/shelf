package io.tarantini.shelf.catalog.metadata

import arrow.core.raise.context.raise
import arrow.fx.coroutines.ResourceScope
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.ProcessedMetadata
import io.tarantini.shelf.processing.audiobook.AudiobookParser
import io.tarantini.shelf.processing.epub.EpubParser
import io.tarantini.shelf.processing.import.domain.UnsupportedFormat
import java.nio.file.Path

interface MetadataProcessor {
    context(_: RaiseContext)
    suspend fun process(
        scope: ResourceScope,
        sourcePath: Path,
        fileName: String,
        bookId: BookId,
    ): ProcessedMetadata
}

fun metadataProcessor(epubParser: EpubParser, audiobookParser: AudiobookParser) =
    object : MetadataProcessor {
        context(_: RaiseContext)
        override suspend fun process(
            scope: ResourceScope,
            sourcePath: Path,
            fileName: String,
            bookId: BookId,
        ): ProcessedMetadata {
            val extension = fileName.substringAfterLast(".").lowercase()
            val (result, coverImage) =
                when (extension) {
                    "epub" -> {
                        epubParser.parse(scope, sourcePath, bookId)
                    }

                    "mp3",
                    "m4b",
                    "m4a" -> {
                        audiobookParser.parse(scope, sourcePath, extension, fileName, bookId)
                    }

                    else -> raise(UnsupportedFormat)
                }

            return ProcessedMetadata(
                metadata = result.core,
                edition = result.edition,
                chapters = result.chapters,
                authors = result.authors,
                series = result.series,
                coverImage = coverImage,
            )
        }
    }
