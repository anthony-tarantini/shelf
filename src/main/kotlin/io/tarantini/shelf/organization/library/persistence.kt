@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.organization.library.domain.*
import io.tarantini.shelf.organization.library.persistence.LibraryQueries
import io.tarantini.shelf.organization.library.persistence.LibrarySummaries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun LibraryQueries.getLibrariesForUser(userId: UserId): List<LibrarySummary> =
    selectAllForUser(userId).executeAsList().map { it.toSummary() }

context(_: RaiseContext)
fun LibraryQueries.getLibraryById(libraryId: LibraryId): SavedLibraryRoot =
    selectById(libraryId).executeAsOneOrNull()?.toRoot() ?: raise(LibraryNotFound)

context(_: RaiseContext)
fun LibraryQueries.createLibrary(userId: UserId, title: String): LibraryId =
    insert(userId, title).executeAsOneOrNull() ?: raise(LibraryAlreadyExists)

context(_: RaiseContext)
fun LibraryQueries.updateLibrary(libraryId: LibraryId, title: String): LibraryId =
    update(libraryId = libraryId, title = title).executeAsOneOrNull() ?: raise(LibraryNotFound)

context(_: RaiseContext)
fun LibraryQueries.deleteLibrary(libraryId: LibraryId): LibraryId =
    deleteById(libraryId).executeAsOneOrNull() ?: raise(LibraryNotFound)

context(_: RaiseContext)
fun LibraryQueries.addBookToLibrary(libraryId: LibraryId, bookId: BookId) =
    insertLibraryBook(libraryId, bookId)

context(_: RaiseContext)
fun LibraryQueries.removeBookFromLibrary(libraryId: LibraryId, bookId: BookId) =
    deleteLibraryBook(libraryId, bookId)

private fun LibrarySummaries.toSummary() = LibrarySummary(id, userId, title, bookCount.toInt())

private fun LibrarySummaries.toRoot() = LibraryRoot.fromRaw(id, userId, title)
