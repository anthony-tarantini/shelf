package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.AuthorRelinkIntent
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookMetadataMutation
import io.tarantini.shelf.catalog.book.domain.BookSeriesMutation

internal interface BookMetadataWriter {
    context(_: RaiseContext)
    fun applyBaseMutation(id: BookId, mutation: BookMetadataMutation)
}

internal interface BookAuthorResolver {
    context(_: RaiseContext)
    fun resolveAuthorIds(authors: List<AuthorRelinkIntent>): List<AuthorId>

    context(_: RaiseContext)
    fun deleteOrphanedAuthors()
}

internal interface BookSeriesResolver {
    context(_: RaiseContext)
    fun applySeriesMutation(
        bookId: BookId,
        authorIds: List<AuthorId>,
        seriesMutation: BookSeriesMutation.Replace,
    )

    context(_: RaiseContext)
    fun deleteOrphanedSeries()
}
