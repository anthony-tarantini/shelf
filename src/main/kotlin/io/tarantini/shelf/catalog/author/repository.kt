@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.SavedAuthorRoot
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AuthorMutationRepository {
    context(_: RaiseContext)
    suspend fun getAuthorById(id: AuthorId): SavedAuthorRoot

    context(_: RaiseContext)
    suspend fun createAuthor(name: String): SavedAuthorRoot

    context(_: RaiseContext)
    suspend fun updateAuthor(id: AuthorId, name: String): SavedAuthorRoot
}

fun authorMutationRepository(authorQueries: AuthorQueries): AuthorMutationRepository =
    SqlDelightAuthorMutationRepository(authorQueries)

private class SqlDelightAuthorMutationRepository(private val authorQueries: AuthorQueries) :
    AuthorMutationRepository {
    context(_: RaiseContext)
    override suspend fun getAuthorById(id: AuthorId) =
        withContext(Dispatchers.IO) { authorQueries.getAuthorById(id) }

    context(_: RaiseContext)
    override suspend fun createAuthor(name: String) =
        withContext(Dispatchers.IO) {
            authorQueries.transactionWithResult {
                authorQueries.createAuthor(name).let { authorQueries.getAuthorById(it) }
            }
        }

    context(_: RaiseContext)
    override suspend fun updateAuthor(id: AuthorId, name: String) =
        withContext(Dispatchers.IO) {
            authorQueries.transactionWithResult {
                authorQueries.updateAuthor(id, name)
                authorQueries.getAuthorById(id)
            }
        }
}
