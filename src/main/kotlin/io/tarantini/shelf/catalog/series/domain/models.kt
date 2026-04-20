@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@Serializable
data class SeriesAggregate<S : PersistenceState>(
    val series: SeriesRoot<S>,
    val books: List<BookRoot<S>> = emptyList(),
)

typealias SavedSeriesAggregate = SeriesAggregate<PersistenceState.Persisted>

typealias NewSeriesAggregate = SeriesAggregate<PersistenceState.Unsaved>

@Serializable
@ConsistentCopyVisibility
data class SeriesRoot<S : PersistenceState>
private constructor(
    val id: Identity<S, SeriesId>,
    val name: String,
    val coverPath: StoragePath? = null,
) {
    companion object {
        fun fromRaw(id: SeriesId, name: String, coverPath: StoragePath? = null) =
            SeriesRoot(Identity.Persisted(id), name, coverPath)

        fun new(name: String, coverPath: StoragePath? = null) =
            SeriesRoot(Identity.Unsaved, name, coverPath)
    }
}

typealias SavedSeriesRoot = SeriesRoot<PersistenceState.Persisted>

typealias NewSeriesRoot = SeriesRoot<PersistenceState.Unsaved>
