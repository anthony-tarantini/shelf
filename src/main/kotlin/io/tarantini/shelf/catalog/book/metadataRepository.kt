package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookMetadataMutation
import io.tarantini.shelf.catalog.book.domain.BookMetadataSnapshot
import io.tarantini.shelf.catalog.book.domain.BookRelationshipsMutation
import io.tarantini.shelf.catalog.book.domain.BookSeriesMutation
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookRepository {
    context(_: RaiseContext)
    suspend fun loadMetadataSnapshot(id: BookId): BookMetadataSnapshot

    context(_: RaiseContext)
    suspend fun applyMetadataMutation(id: BookId, mutation: BookMetadataMutation)
}

fun bookRepository(
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
    metadataRepository: MetadataRepository,
): BookRepository =
    bookRepository(
        bookQueries = bookQueries,
        metadataWriter = SqlDelightBookMetadataWriter(bookQueries, metadataRepository),
        authorResolver =
            SqlDelightBookAuthorResolver(store = SqlDelightAuthorResolutionStore(authorQueries)),
        seriesResolver =
            SqlDelightBookSeriesResolver(
                store = SqlDelightSeriesResolutionStore(bookQueries, seriesQueries)
            ),
    )

internal fun bookRepository(
    bookQueries: BookQueries,
    metadataWriter: BookMetadataWriter,
    authorResolver: BookAuthorResolver,
    seriesResolver: BookSeriesResolver,
): BookRepository =
    object : BookRepository {
        context(_: RaiseContext)
        override suspend fun loadMetadataSnapshot(id: BookId): BookMetadataSnapshot =
            withContext(Dispatchers.IO) { BookMetadataSnapshot(bookQueries.getBookById(id)) }

        context(_: RaiseContext)
        override suspend fun applyMetadataMutation(id: BookId, mutation: BookMetadataMutation) {
            withContext(Dispatchers.IO) {
                bookQueries.transaction {
                    metadataWriter.applyBaseMutation(id, mutation)

                    when (val relationships = mutation.relationships) {
                        BookRelationshipsMutation.KeepExisting -> Unit
                        is BookRelationshipsMutation.Replace -> {
                            bookQueries.deleteBookAuthor(id)
                            val authorIds = authorResolver.resolveAuthorIds(relationships.authors)
                            authorIds.forEach { authorId ->
                                bookQueries.insertBookAuthor(id, authorId)
                            }

                            when (relationships.series) {
                                BookSeriesMutation.KeepExisting -> Unit
                                is BookSeriesMutation.Replace -> {
                                    bookQueries.deleteBookSeries(id)
                                    seriesResolver.applySeriesMutation(
                                        bookId = id,
                                        authorIds = authorIds,
                                        seriesMutation = relationships.series,
                                    )
                                }
                            }

                            authorResolver.deleteOrphanedAuthors()
                            seriesResolver.deleteOrphanedSeries()
                        }
                    }
                }
            }
        }
    }
