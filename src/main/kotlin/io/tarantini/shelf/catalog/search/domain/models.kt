package io.tarantini.shelf.catalog.search.domain

import io.tarantini.shelf.catalog.author.domain.AuthorSummary
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.series.domain.SeriesSummary
import kotlinx.serialization.Serializable

@Serializable
data class GlobalSearchResult(
    val books: List<BookSummary>,
    val authors: List<AuthorSummary>,
    val series: List<SeriesSummary>,
)
