package io.tarantini.shelf.organization.library.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.serialization.Serializable

@Serializable
data class LibraryAggregate<S : PersistenceState>(
    val library: LibraryRoot<S>,
    val books: List<BookRoot<S>> = emptyList(),
)

typealias SavedLibraryAggregate = LibraryAggregate<PersistenceState.Persisted>

typealias NewLibraryAggregate = LibraryAggregate<PersistenceState.Unsaved>

@Serializable
data class LibraryRoot<S : PersistenceState>(
    val id: Identity<S, LibraryId>,
    val userId: UserId,
    val title: String,
) {
    companion object {
        fun fromRaw(id: LibraryId, userId: UserId, title: String) =
            LibraryRoot<PersistenceState.Persisted>(Identity.Persisted(id), userId, title)

        fun new(userId: UserId, title: String) =
            LibraryRoot<PersistenceState.Unsaved>(Identity.Unsaved, userId, title)
    }
}

typealias SavedLibraryRoot = LibraryRoot<PersistenceState.Persisted>

typealias NewLibraryRoot = LibraryRoot<PersistenceState.Unsaved>
