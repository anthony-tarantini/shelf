@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@Serializable
data class AuthorAggregate<S : PersistenceState>(
    val author: AuthorRoot<S>,
    val books: List<BookRoot<S>> = emptyList(),
)

typealias SavedAuthorAggregate = AuthorAggregate<PersistenceState.Persisted>

typealias NewAuthorAggregate = AuthorAggregate<PersistenceState.Unsaved>

@Serializable
@ConsistentCopyVisibility
data class AuthorRoot<S : PersistenceState>
private constructor(
    val id: Identity<S, AuthorId>,
    val name: String,
    val imagePath: StoragePath? = null,
) {
    companion object {
        fun fromRaw(id: AuthorId, name: String, imagePath: StoragePath? = null) =
            AuthorRoot(Identity.Persisted(id), name, imagePath)

        context(_: RaiseContext)
        fun new(name: String?): AuthorRoot<PersistenceState.Unsaved> {
            ensure(!name.isNullOrEmpty()) { EmptyAuthorFirstName }
            return AuthorRoot(Identity.Unsaved, name)
        }

        context(_: RaiseContext)
        fun update(id: String?, name: String?): AuthorRoot<PersistenceState.Persisted> {
            ensure(!name.isNullOrEmpty()) { EmptyAuthorFirstName }
            ensure(!id.isNullOrEmpty()) { EmptyAuthorId }
            return AuthorRoot(Identity.Persisted(AuthorId(id)), name)
        }
    }
}

typealias SavedAuthorRoot = AuthorRoot<PersistenceState.Persisted>

typealias NewAuthorRoot = AuthorRoot<PersistenceState.Unsaved>

typealias AuthorRootUpdate = AuthorRoot<PersistenceState.Persisted>
