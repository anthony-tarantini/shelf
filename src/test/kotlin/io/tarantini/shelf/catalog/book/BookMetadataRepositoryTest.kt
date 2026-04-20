@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.getAllAuthors
import io.tarantini.shelf.catalog.book.domain.AuthorName
import io.tarantini.shelf.catalog.book.domain.AuthorRelinkIntent
import io.tarantini.shelf.catalog.book.domain.BookMetadataMutation
import io.tarantini.shelf.catalog.book.domain.BookRelationshipsMutation
import io.tarantini.shelf.catalog.book.domain.BookSeriesMutation
import io.tarantini.shelf.catalog.book.domain.SeriesName
import io.tarantini.shelf.catalog.book.domain.SeriesRelinkIntent
import io.tarantini.shelf.catalog.metadata.metadataRepository
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.getSeriesForAuthors

class BookMetadataRepositoryTest :
    IntegrationSpec({
        "applyMetadataMutation reuses existing author by canonical name" {
            testWithDeps { deps ->
                recover({
                    with(deps) {
                        val bookId = database.bookQueries.createBook("Foundation", null)
                        val existingAuthorId = database.authorQueries.createAuthor("Isaac Asimov")

                        val repository =
                            bookRepository(
                                bookQueries = database.bookQueries,
                                authorQueries = database.authorQueries,
                                seriesQueries = database.seriesQueries,
                                metadataRepository = metadataRepository(database.metadataQueries),
                            )

                        val mutation =
                            baseMutation(
                                title = "Foundation",
                                relationships =
                                    BookRelationshipsMutation.Replace(
                                        authors =
                                            listOf(
                                                AuthorRelinkIntent.UpsertByName(
                                                    AuthorName.fromRaw("  isaac   asimov ")
                                                )
                                            ),
                                        series = BookSeriesMutation.KeepExisting,
                                    ),
                            )

                        repository.applyMetadataMutation(bookId, mutation)

                        val authors = database.authorQueries.getAllAuthors()
                        authors.map { it.id } shouldContain existingAuthorId
                        authors.count { it.name == "Isaac Asimov" } shouldBe 1
                        val booksByAuthor =
                            database.bookQueries.getBooksForAuthors(listOf(existingAuthorId))
                        booksByAuthor.getOrDefault(existingAuthorId, emptyList()).map {
                            it.id.id
                        } shouldContain bookId
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "applyMetadataMutation reuses scoped series when exactly one canonical match exists" {
            testWithDeps { deps ->
                recover({
                    with(deps) {
                        val bookId = database.bookQueries.createBook("Foundation and Empire", null)
                        val authorId = database.authorQueries.createAuthor("Isaac Asimov")
                        val existingSeriesId = database.seriesQueries.createSeries("Foundation")
                        database.seriesQueries.insertSeriesAuthor(existingSeriesId, authorId)

                        val repository =
                            bookRepository(
                                bookQueries = database.bookQueries,
                                authorQueries = database.authorQueries,
                                seriesQueries = database.seriesQueries,
                                metadataRepository = metadataRepository(database.metadataQueries),
                            )

                        val mutation =
                            baseMutation(
                                title = "Foundation and Empire",
                                relationships =
                                    BookRelationshipsMutation.Replace(
                                        authors = listOf(AuthorRelinkIntent.UseExisting(authorId)),
                                        series =
                                            BookSeriesMutation.Replace(
                                                listOf(
                                                    SeriesRelinkIntent.AuthorScopedUpsertByName(
                                                        name = SeriesName.fromRaw("Foundation"),
                                                        index = 2.0,
                                                    )
                                                )
                                            ),
                                    ),
                            )

                        repository.applyMetadataMutation(bookId, mutation)

                        val booksInSeries =
                            database.bookQueries.getBooksForSeries(listOf(existingSeriesId))
                        booksInSeries.getOrDefault(existingSeriesId, emptyList()).map {
                            it.id.id
                        } shouldContain bookId
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "applyMetadataMutation creates new series when same title exists outside author scope" {
            testWithDeps { deps ->
                recover({
                    with(deps) {
                        val bookId = database.bookQueries.createBook("Dune Messiah", null)
                        val scopedAuthorId = database.authorQueries.createAuthor("Frank Herbert")
                        val otherAuthorId = database.authorQueries.createAuthor("Brian Herbert")
                        val outsideScopeSeriesId = database.seriesQueries.createSeries("Dune")
                        database.seriesQueries.insertSeriesAuthor(
                            outsideScopeSeriesId,
                            otherAuthorId,
                        )

                        val repository =
                            bookRepository(
                                bookQueries = database.bookQueries,
                                authorQueries = database.authorQueries,
                                seriesQueries = database.seriesQueries,
                                metadataRepository = metadataRepository(database.metadataQueries),
                            )

                        val mutation =
                            baseMutation(
                                title = "Dune Messiah",
                                relationships =
                                    BookRelationshipsMutation.Replace(
                                        authors =
                                            listOf(AuthorRelinkIntent.UseExisting(scopedAuthorId)),
                                        series =
                                            BookSeriesMutation.Replace(
                                                listOf(
                                                    SeriesRelinkIntent.AuthorScopedUpsertByName(
                                                        name = SeriesName.fromRaw("Dune"),
                                                        index = 2.0,
                                                    )
                                                )
                                            ),
                                    ),
                            )

                        repository.applyMetadataMutation(bookId, mutation)

                        val scopedSeries =
                            database.seriesQueries
                                .getSeriesForAuthors(listOf(scopedAuthorId))
                                .getOrDefault(scopedAuthorId, emptyList())
                        val duneInScope =
                            scopedSeries.filter { it.name.equals("Dune", ignoreCase = true) }
                        duneInScope.size shouldBe 1
                        val newSeriesId = duneInScope.first().id.id
                        (newSeriesId == outsideScopeSeriesId) shouldBe false
                        val booksInOldSeries =
                            database.bookQueries.getBooksForSeries(listOf(outsideScopeSeriesId))
                        booksInOldSeries.getOrDefault(outsideScopeSeriesId, emptyList()).any {
                            it.id.id == bookId
                        } shouldBe false
                        val booksInNewSeries =
                            database.bookQueries.getBooksForSeries(listOf(newSeriesId))
                        booksInNewSeries.getOrDefault(newSeriesId, emptyList()).map {
                            it.id.id
                        } shouldContain bookId
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })

private fun baseMutation(
    title: String,
    relationships: BookRelationshipsMutation,
): BookMetadataMutation =
    BookMetadataMutation(
        title = title,
        bookRecord = null,
        description = null,
        publisher = null,
        publishYear = null,
        genres = emptyList(),
        moods = emptyList(),
        ebookMetadata = null,
        audiobookMetadata = null,
        relationships = relationships,
    )
