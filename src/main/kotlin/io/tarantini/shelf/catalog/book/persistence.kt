@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookAlreadyExists
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookNotFound
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.book.domain.SavedBookRoot
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.book.persistence.Books
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForAuthors
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForLibrary
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForSeries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun BookQueries.getAllBooks(): List<SavedBookRoot> =
    selectAll().executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun BookQueries.getBookById(id: BookId): SavedBookRoot =
    selectById(id).executeAsOneOrNull()?.toDomain() ?: raise(BookNotFound)

context(_: RaiseContext)
fun BookQueries.getBooksByIds(ids: List<BookId>): List<SavedBookRoot> {
    if (ids.isEmpty()) return emptyList()
    return selectByIds(ids).executeAsList().map { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.linkSeries(id: BookId, seriesId: SeriesId, index: Double) =
    insertBookSeries(id, seriesId, index)

context(_: RaiseContext)
fun BookQueries.getBooksForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedBookRoot>> {
    if (authorIds.isEmpty()) return emptyMap()
    return selectBooksForAuthors(authorIds).executeAsList().groupBy({ it.author }) { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.getBooksForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedBookRoot>> {
    if (seriesIds.isEmpty()) return emptyMap()
    return selectBooksForSeries(seriesIds).executeAsList().groupBy({ it.series }) { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.getBooksForLibrary(
    libraryIds: List<LibraryId>
): Map<LibraryId, List<SavedBookRoot>> {
    if (libraryIds.isEmpty()) return emptyMap()
    return selectBooksForLibrary(libraryIds).executeAsList().groupBy({ it.library }) {
        it.toDomain()
    }
}

context(_: RaiseContext)
fun BookQueries.createBook(title: String, coverPath: StoragePath?): BookId {
    return insert(title, coverPath).executeAsOneOrNull() ?: raise(BookAlreadyExists)
}

private fun Books.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForAuthors.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForSeries.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForLibrary.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)
