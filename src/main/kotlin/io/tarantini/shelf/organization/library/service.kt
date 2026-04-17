@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.getBooksForLibrary
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.organization.library.domain.*
import io.tarantini.shelf.organization.library.persistence.LibraryQueries
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.requireOwnership
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LibraryProvider {
    context(_: RaiseContext, auth: JwtContext)
    suspend fun getLibrariesForUser(): List<LibrarySummary>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getLibraryById(id: LibraryId): SavedLibraryRoot

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getLibraryAggregate(id: LibraryId): SavedLibraryAggregate
}

interface LibraryModifier {
    context(_: RaiseContext, auth: JwtContext)
    suspend fun createLibrary(title: String): SavedLibraryRoot

    context(_: RaiseContext, auth: JwtContext)
    suspend fun updateLibrary(id: LibraryId, title: String?): SavedLibraryRoot

    context(_: RaiseContext, auth: JwtContext)
    suspend fun deleteLibrary(id: LibraryId)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun addBookToLibrary(libraryId: LibraryId, bookId: BookId)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun removeBookFromLibrary(libraryId: LibraryId, bookId: BookId)
}

interface LibraryService : LibraryProvider, LibraryModifier

fun libraryService(libraryQueries: LibraryQueries, bookQueries: BookQueries) =
    object : LibraryService {
        context(_: RaiseContext, auth: JwtContext)
        private fun requireOwnedLibrary(id: LibraryId): SavedLibraryRoot {
            val library = libraryQueries.getLibraryById(id)
            requireOwnership(library.userId)
            return library
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getLibrariesForUser() =
            withContext(Dispatchers.IO) { libraryQueries.getLibrariesForUser(auth.userId) }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getLibraryById(id: LibraryId) =
            withContext(Dispatchers.IO) { requireOwnedLibrary(id) }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getLibraryAggregate(id: LibraryId) =
            withContext(Dispatchers.IO) {
                val library = requireOwnedLibrary(id)
                LibraryAggregate(
                    library = library,
                    books = bookQueries.getBooksForLibrary(listOf(id)).getOrDefault(id, emptyList()),
                )
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun createLibrary(title: String) =
            withContext(Dispatchers.IO) {
                libraryQueries.transactionWithResult {
                    libraryQueries.createLibrary(auth.userId, title).let {
                        libraryQueries.getLibraryById(it)
                    }
                }
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun updateLibrary(id: LibraryId, title: String?) =
            withContext(Dispatchers.IO) {
                libraryQueries.transactionWithResult {
                    val existing = requireOwnedLibrary(id)
                    libraryQueries.updateLibrary(id, title ?: existing.title)
                    requireOwnedLibrary(id)
                }
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun deleteLibrary(id: LibraryId) {
            withContext(Dispatchers.IO) {
                requireOwnedLibrary(id)
                libraryQueries.deleteLibrary(id)
            }
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun addBookToLibrary(libraryId: LibraryId, bookId: BookId) {
            withContext(Dispatchers.IO) {
                requireOwnedLibrary(libraryId)
                libraryQueries.addBookToLibrary(libraryId, bookId)
            }
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun removeBookFromLibrary(libraryId: LibraryId, bookId: BookId) {
            withContext(Dispatchers.IO) {
                requireOwnedLibrary(libraryId)
                libraryQueries.removeBookFromLibrary(libraryId, bookId)
            }
        }
    }
