@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.either
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookSeriesMutation
import io.tarantini.shelf.catalog.book.domain.SeriesName
import io.tarantini.shelf.catalog.book.domain.SeriesRelinkIntent
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlin.uuid.Uuid

class BookSeriesResolverTest :
    StringSpec({
        "applySeriesMutation reuses scoped series when there is one canonical match" {
            val bookId = BookId.fromRaw(Uuid.random())
            val authorId = AuthorId.fromRaw(Uuid.random())
            val existingSeriesId = SeriesId.fromRaw(Uuid.random())
            val store =
                FakeSeriesResolutionStore(
                    scopedSeries =
                        mapOf(authorId to listOf(ScopedSeriesEntry(existingSeriesId, "  dune  ")))
                )
            val resolver = SqlDelightBookSeriesResolver(store)
            val mutation =
                BookSeriesMutation.Replace(
                    values =
                        listOf(
                            SeriesRelinkIntent.AuthorScopedUpsertByName(
                                name = SeriesName.fromRaw("Dune"),
                                index = 2.0,
                            )
                        )
                )

            either { resolver.applySeriesMutation(bookId, listOf(authorId), mutation) }

            store.createdSeries shouldBe emptyList()
            store.bookLinks shouldBe listOf(Triple(bookId, existingSeriesId, 2.0))
            store.authorLinks shouldBe listOf(existingSeriesId to authorId)
        }

        "applySeriesMutation creates series when scoped canonical matches are ambiguous" {
            val bookId = BookId.fromRaw(Uuid.random())
            val authorId = AuthorId.fromRaw(Uuid.random())
            val createdSeriesId = SeriesId.fromRaw(Uuid.random())
            val store =
                FakeSeriesResolutionStore(
                    scopedSeries =
                        mapOf(
                            authorId to
                                listOf(
                                    ScopedSeriesEntry(SeriesId.fromRaw(Uuid.random()), "Dune"),
                                    ScopedSeriesEntry(SeriesId.fromRaw(Uuid.random()), " dune "),
                                )
                        ),
                    createSeriesId = createdSeriesId,
                )
            val resolver = SqlDelightBookSeriesResolver(store)
            val mutation =
                BookSeriesMutation.Replace(
                    values =
                        listOf(
                            SeriesRelinkIntent.AuthorScopedUpsertByName(
                                name = SeriesName.fromRaw("Dune"),
                                index = null,
                            )
                        )
                )

            either { resolver.applySeriesMutation(bookId, listOf(authorId), mutation) }

            store.createdSeries shouldBe listOf("Dune")
            store.bookLinks shouldBe listOf(Triple(bookId, createdSeriesId, 0.0))
            store.authorLinks shouldBe listOf(createdSeriesId to authorId)
        }

        "deleteOrphanedSeries delegates to store" {
            val store = FakeSeriesResolutionStore(emptyMap())
            val resolver = SqlDelightBookSeriesResolver(store)

            either { resolver.deleteOrphanedSeries() }

            store.deletedOrphans shouldBe true
        }
    })

private class FakeSeriesResolutionStore(
    private val scopedSeries: Map<AuthorId, List<ScopedSeriesEntry>>,
    private val createSeriesId: SeriesId = SeriesId.fromRaw(Uuid.random()),
) : SeriesResolutionStore {
    val createdSeries = mutableListOf<String>()
    val bookLinks = mutableListOf<Triple<BookId, SeriesId, Double>>()
    val authorLinks = mutableListOf<Pair<SeriesId, AuthorId>>()
    var deletedOrphans: Boolean = false

    context(_: RaiseContext)
    override fun getScopedSeries(
        authorIds: List<AuthorId>
    ): Map<AuthorId, List<ScopedSeriesEntry>> = scopedSeries.filterKeys { authorIds.contains(it) }

    context(_: RaiseContext)
    override fun createSeries(name: String): SeriesId {
        createdSeries += name
        return createSeriesId
    }

    context(_: RaiseContext)
    override fun linkSeriesToBook(bookId: BookId, seriesId: SeriesId, index: Double) {
        bookLinks += Triple(bookId, seriesId, index)
    }

    context(_: RaiseContext)
    override fun linkSeriesToAuthor(seriesId: SeriesId, authorId: AuthorId) {
        authorLinks += seriesId to authorId
    }

    context(_: RaiseContext)
    override fun deleteOrphanedSeries() {
        deletedOrphans = true
    }
}
