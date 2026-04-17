package io.tarantini.shelf.catalog.book.domain

import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.activity.domain.BookUserState
import kotlinx.serialization.Serializable

@Serializable
data class BookSummary(
    val id: BookId,
    val title: String,
    val coverPath: StoragePath? = null,
    val authorNames: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val userState: BookUserState? = null,
)

@Serializable
data class BookPage(
    val items: List<SavedBookAggregate>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)
