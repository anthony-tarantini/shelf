@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book.domain

import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.author.domain.SavedAuthorRoot
import io.tarantini.shelf.catalog.metadata.domain.SavedMetadataAggregate
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.activity.domain.BookUserState
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@Serializable
data class BookSeriesEntry(
    val id: SeriesId,
    val name: String,
    val coverPath: StoragePath? = null,
    val index: Double? = null,
)

@Serializable
data class BookAggregate<S : PersistenceState>(
    val book: BookRoot<S>,
    val authors: List<SavedAuthorRoot> = emptyList(),
    val series: List<BookSeriesEntry> = emptyList(),
    val metadata: SavedMetadataAggregate? = null,
    val userState: BookUserState? = null,
)

typealias SavedBookAggregate = BookAggregate<PersistenceState.Persisted>

typealias NewBookAggregate = BookAggregate<PersistenceState.Unsaved>

@Serializable
@ConsistentCopyVisibility
data class BookRoot<S : PersistenceState>
private constructor(
    val id: Identity<S, BookId>,
    val title: String,
    val coverPath: StoragePath? = null,
) {
    companion object {
        fun deriveCoverPath(path: String): StoragePath? = StoragePath.fromRaw(path).resolveCover()

        context(_: RaiseContext)
        operator fun invoke(
            id: String?,
            title: String?,
            coverPath: String? = null,
        ): BookRoot<PersistenceState.Persisted> {
            return BookRoot(
                id = Identity.Persisted(BookId(id)),
                title = ensureNotNull(title) { EmptyBookTitle },
                coverPath = coverPath?.let { StoragePath.fromRaw(it) },
            )
        }

        fun fromRaw(id: UUID, title: String, coverPath: String? = null) =
            BookRoot<PersistenceState.Persisted>(
                id = Identity.Persisted(BookId.fromRaw(id.toKotlinUuid())),
                title,
                coverPath?.let { StoragePath.fromRaw(it) },
            )

        fun fromRaw(id: UUID, title: String, coverPath: StoragePath? = null) =
            BookRoot<PersistenceState.Persisted>(
                id = Identity.Persisted(BookId.fromRaw(id.toKotlinUuid())),
                title,
                coverPath,
            )

        fun fromRaw(id: BookId, title: String, coverPath: StoragePath? = null) =
            BookRoot<PersistenceState.Persisted>(Identity.Persisted(id), title, coverPath)

        fun new(title: String, coverPath: StoragePath? = null) =
            BookRoot<PersistenceState.Unsaved>(Identity.Unsaved, title, coverPath)
    }
}

typealias SavedBookRoot = BookRoot<PersistenceState.Persisted>

typealias NewBookRoot = BookRoot<PersistenceState.Unsaved>

@Serializable
@ConsistentCopyVisibility
data class UpdateBookRoot private constructor(val title: String, val coverPath: StoragePath?) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(title: String?, coverPath: String?) =
            UpdateBookRoot(
                title = ensureNotNull(title) { EmptyBookTitle },
                coverPath = coverPath?.let { StoragePath.fromRaw(it) },
            )
    }
}
