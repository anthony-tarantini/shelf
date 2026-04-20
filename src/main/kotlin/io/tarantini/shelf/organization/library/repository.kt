@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.organization.library.domain.SavedLibraryRoot
import io.tarantini.shelf.organization.library.persistence.LibraryQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LibraryMutationRepository {
    context(_: RaiseContext)
    suspend fun getLibraryById(id: LibraryId): SavedLibraryRoot

    context(_: RaiseContext)
    suspend fun createLibrary(userId: UserId, title: String): SavedLibraryRoot

    context(_: RaiseContext)
    suspend fun updateLibrary(id: LibraryId, title: String): SavedLibraryRoot
}

fun libraryMutationRepository(libraryQueries: LibraryQueries): LibraryMutationRepository =
    SqlDelightLibraryMutationRepository(libraryQueries)

private class SqlDelightLibraryMutationRepository(private val libraryQueries: LibraryQueries) :
    LibraryMutationRepository {
    context(_: RaiseContext)
    override suspend fun getLibraryById(id: LibraryId) =
        withContext(Dispatchers.IO) { libraryQueries.getLibraryById(id) }

    context(_: RaiseContext)
    override suspend fun createLibrary(userId: UserId, title: String) =
        withContext(Dispatchers.IO) {
            libraryQueries.transactionWithResult {
                libraryQueries.createLibrary(userId, title).let {
                    libraryQueries.getLibraryById(it)
                }
            }
        }

    context(_: RaiseContext)
    override suspend fun updateLibrary(id: LibraryId, title: String) =
        withContext(Dispatchers.IO) {
            libraryQueries.transactionWithResult {
                libraryQueries.updateLibrary(id, title)
                libraryQueries.getLibraryById(id)
            }
        }
}
