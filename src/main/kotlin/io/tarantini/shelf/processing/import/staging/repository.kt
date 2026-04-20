@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.getAuthorById
import io.tarantini.shelf.catalog.author.linkBook
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.linkSeries
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataAggregate
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface StagedBookPromotionRepository {
    context(_: RaiseContext)
    suspend fun promoteToBook(
        title: String,
        coverPath: StoragePath?,
        authors: List<String>,
        selectedAuthorIds: Map<String, AuthorId?>,
        series: List<io.tarantini.shelf.processing.import.domain.StagedSeries>,
        metadata: NewMetadataAggregate,
        targetBookId: BookId? = null,
    ): BookId
}

fun stagedBookPromotionRepository(
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
    metadataRepository: MetadataRepository,
): StagedBookPromotionRepository =
    SqlDelightStagedBookPromotionRepository(
        bookQueries,
        authorQueries,
        seriesQueries,
        metadataRepository,
    )

private class SqlDelightStagedBookPromotionRepository(
    private val bookQueries: BookQueries,
    private val authorQueries: AuthorQueries,
    private val seriesQueries: SeriesQueries,
    private val metadataRepository: MetadataRepository,
) : StagedBookPromotionRepository {
    context(_: RaiseContext)
    override suspend fun promoteToBook(
        title: String,
        coverPath: StoragePath?,
        authors: List<String>,
        selectedAuthorIds: Map<String, AuthorId?>,
        series: List<io.tarantini.shelf.processing.import.domain.StagedSeries>,
        metadata: NewMetadataAggregate,
        targetBookId: BookId?,
    ): BookId =
        withContext(Dispatchers.IO) {
            bookQueries.transactionWithResult {
                // 1. Resolve Book
                val bookId = targetBookId ?: bookQueries.insert(title, coverPath).executeAsOne()

                // 2. Link Authors
                val authorIds =
                    authors.map { authorName ->
                        val selectedId = selectedAuthorIds[authorName]
                        val author =
                            if (selectedId != null) {
                                authorQueries.getAuthorById(selectedId).id.id
                            } else {
                                authorQueries.createAuthor(authorName.trim())
                            }
                        authorQueries.linkBook(author, bookId)
                        author
                    }

                // 3. Link Series
                series.forEach { stagedSeries ->
                    val seriesId = seriesQueries.createSeries(stagedSeries.name)
                    bookQueries.linkSeries(bookId, seriesId, stagedSeries.index ?: 0.0)
                    authorIds.forEach { authorId ->
                        seriesQueries.insertSeriesAuthor(seriesId, authorId)
                    }
                }

                // 4. Save Metadata
                val updatedMetadata =
                    metadata.copy(
                        metadata = metadata.metadata.copy(bookId = bookId),
                        editions =
                            metadata.editions.map {
                                it.copy(edition = it.edition.copy(bookId = bookId))
                            },
                    )
                metadataRepository.saveAggregate(updatedMetadata)

                bookId
            }
        }
}
