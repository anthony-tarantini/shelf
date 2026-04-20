@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.ErrorResponse
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.getAllAuthors
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.domain.SavedBookAggregate
import io.tarantini.shelf.catalog.book.domain.SavedBookRoot
import io.tarantini.shelf.catalog.book.domain.UpdateBookMetadataRequest
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.getSeriesForAuthors
import io.tarantini.shelf.processing.import.domain.StagedSeries
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.activity.domain.ReadingProgressKind
import kotlin.uuid.ExperimentalUuidApi

class BookApiTest :
    IntegrationSpec({
        data class AuthenticatedBookContext(val token: String, val bookId: String)

        suspend fun seedBookId(title: String): String {
            var bookIdString = ""
            testWithDeps { deps ->
                recover({
                    with(deps) {
                        val bookId = database.bookQueries.createBook(title, null)
                        bookIdString = bookId.value.toString()
                    }
                }) {
                    fail("Seeding failed: $it")
                }
            }
            return bookIdString
        }

        suspend fun registerAndSeedBook(
            client: io.ktor.client.HttpClient,
            email: String,
            username: String,
            title: String,
        ): AuthenticatedBookContext {
            val token = registerUser(client, email, username)
            val bookId = seedBookId(title)
            return AuthenticatedBookContext(token = token, bookId = bookId)
        }

        suspend fun patchMetadata(
            client: io.ktor.client.HttpClient,
            token: String,
            bookId: String,
            request: UpdateBookMetadataRequest,
        ): HttpResponse =
            client.patch("/api/books/$bookId/metadata") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(Request(request))
            }

        suspend fun assertMetadataPatchError(
            client: io.ktor.client.HttpClient,
            token: String,
            bookId: String,
            request: UpdateBookMetadataRequest,
            errorType: String,
            message: String,
        ) {
            val patchResponse = patchMetadata(client, token, bookId, request)
            patchResponse.status shouldBe HttpStatusCode.BadRequest
            val error = patchResponse.body<ErrorResponse>()
            error.type shouldBe errorType
            error.message shouldBe message
        }

        "list and get books" {
            testApp { client ->
                val token = registerUser(client, "book-api@example.com", "bookapi")
                val bookIdString = seedBookId("Api Test Book")

                // List
                val listResponse =
                    client.get("/api/books") { header(HttpHeaders.Authorization, "Bearer $token") }
                listResponse.status shouldBe HttpStatusCode.OK
                val books = listResponse.body<Response<List<BookSummary>>>().data
                books.any { it.id.value.toString() == bookIdString } shouldBe true

                // Get by ID
                val getResponse =
                    client.get("/api/books/$bookIdString") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                getResponse.body<Response<SavedBookRoot>>().data.id.id.value.toString() shouldBe
                    bookIdString

                // Get Details
                val detailsResponse =
                    client.get("/api/books/$bookIdString/details") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                detailsResponse.status shouldBe HttpStatusCode.OK
                detailsResponse
                    .body<Response<SavedBookAggregate>>()
                    .data
                    .book
                    .id
                    .id
                    .value
                    .toString() shouldBe bookIdString
            }
        }

        "delete a book" {
            testApp { client ->
                val token = registerUser(client, "book-delete@example.com", "bookdelete")
                val bookIdString = seedBookId("To Delete")

                val deleteResponse =
                    client.delete("/api/books/$bookIdString") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                deleteResponse.status shouldBe HttpStatusCode.OK

                val getResponse =
                    client.get("/api/books/$bookIdString") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.NotFound
            }
        }

        "book catalog should require authentication" {
            testApp { client ->
                val response = client.get("/api/books")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "save and retrieve ebook progress" {
            testApp { client ->
                val token =
                    registerUser(client, "book-progress-ebook@example.com", "bookprogressebook")
                val bookIdString = seedBookId("Progress Ebook")

                val saveResponse =
                    client.put("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ReadingProgress(cfi = "epubcfi(/6/2[chapter]!/4/1:0)")))
                    }
                saveResponse.status shouldBe HttpStatusCode.OK

                val getResponse =
                    client.get("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val progress = getResponse.body<Response<ReadingProgress>>().data
                progress.cfi shouldBe "epubcfi(/6/2[chapter]!/4/1:0)"
                progress.positionSeconds shouldBe null
            }
        }

        "save and retrieve audiobook progress" {
            testApp { client ->
                val token =
                    registerUser(client, "book-progress-audio@example.com", "bookprogressaudio")
                val bookIdString = seedBookId("Progress Audio")

                val saveResponse =
                    client.put("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                ReadingProgress(
                                    kind = ReadingProgressKind.AUDIOBOOK,
                                    positionSeconds = 123.5,
                                    durationSeconds = 3600.0,
                                    progressPercent = 123.5 / 3600.0,
                                )
                            )
                        )
                    }
                saveResponse.status shouldBe HttpStatusCode.OK

                val getResponse =
                    client.get("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val progress = getResponse.body<Response<ReadingProgress>>().data
                progress.positionSeconds shouldBe 123.5
                progress.durationSeconds shouldBe 3600.0
                progress.progressPercent shouldBe 123.5 / 3600.0
                progress.cfi shouldBe null
            }
        }

        "preserve ebook and audiobook progress together" {
            testApp { client ->
                val token =
                    registerUser(client, "book-progress-both@example.com", "bookprogressboth")
                val bookIdString = seedBookId("Progress Both")

                client
                    .put("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ReadingProgress(cfi = "epubcfi(/6/8!/4/2:0)")))
                    }
                    .status shouldBe HttpStatusCode.OK

                client
                    .put("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                ReadingProgress(
                                    kind = ReadingProgressKind.AUDIOBOOK,
                                    positionSeconds = 42.0,
                                    durationSeconds = 420.0,
                                    progressPercent = 0.1,
                                )
                            )
                        )
                    }
                    .status shouldBe HttpStatusCode.OK

                val getResponse =
                    client.get("/api/books/$bookIdString/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val progress = getResponse.body<Response<ReadingProgress>>().data
                progress.cfi shouldBe "epubcfi(/6/8!/4/2:0)"
                progress.positionSeconds shouldBe 42.0
                progress.durationSeconds shouldBe 420.0
                progress.progressPercent shouldBe 0.1
            }
        }

        "patch metadata applies mixed author intents and creates scoped series" {
            testApp { client ->
                val token =
                    registerUser(client, "book-metadata-mixed@example.com", "bookmetadatamixed")

                var bookIdString = ""
                var existingAuthorIdString = ""
                var outsideScopeSeriesIdString = ""
                var scopedAuthorIdString = ""

                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val bookId = database.bookQueries.createBook("Metadata API Book", null)
                            val existingAuthorId =
                                database.authorQueries.createAuthor("Isaac Asimov")
                            val outsideScopeAuthorId =
                                database.authorQueries.createAuthor("Brian Herbert")
                            val outsideScopeSeriesId = database.seriesQueries.createSeries("Dune")
                            database.seriesQueries.insertSeriesAuthor(
                                outsideScopeSeriesId,
                                outsideScopeAuthorId,
                            )

                            bookIdString = bookId.value.toString()
                            existingAuthorIdString = existingAuthorId.value.toString()
                            outsideScopeSeriesIdString = outsideScopeSeriesId.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val patchResponse =
                    client.patch("/api/books/$bookIdString/metadata") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UpdateBookMetadataRequest(
                                    title = "Metadata API Book Updated",
                                    authors = listOf(" Isaac Asimov ", " Frank Herbert "),
                                    selectedAuthorIds =
                                        mapOf(" Isaac Asimov " to existingAuthorIdString),
                                    series = listOf(StagedSeries(name = " Dune ", index = 2.0)),
                                )
                            )
                        )
                    }
                patchResponse.status shouldBe HttpStatusCode.OK

                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val bookId = io.tarantini.shelf.catalog.book.domain.BookId(bookIdString)
                            val existingAuthorId =
                                io.tarantini.shelf.catalog.author.domain.AuthorId(
                                    existingAuthorIdString
                                )
                            val outsideScopeSeriesId =
                                io.tarantini.shelf.catalog.series.domain.SeriesId(
                                    outsideScopeSeriesIdString
                                )

                            val allAuthors = database.authorQueries.getAllAuthors()
                            val scopedAuthorId = allAuthors.first { it.name == "Frank Herbert" }.id
                            scopedAuthorIdString = scopedAuthorId.value.toString()

                            val authorsForBook =
                                database.bookQueries.getBooksForAuthors(
                                    listOf(existingAuthorId, scopedAuthorId)
                                )
                            authorsForBook.getOrDefault(existingAuthorId, emptyList()).any {
                                it.id.id == bookId
                            } shouldBe true
                            authorsForBook.getOrDefault(scopedAuthorId, emptyList()).any {
                                it.id.id == bookId
                            } shouldBe true

                            val scopedSeries =
                                database.seriesQueries
                                    .getSeriesForAuthors(listOf(scopedAuthorId))
                                    .getOrDefault(scopedAuthorId, emptyList())
                            val duneScoped =
                                scopedSeries.first { it.name.equals("Dune", ignoreCase = true) }

                            val booksInScopedSeries =
                                database.bookQueries.getBooksForSeries(listOf(duneScoped.id.id))
                            booksInScopedSeries.getOrDefault(duneScoped.id.id, emptyList()).any {
                                it.id.id == bookId
                            } shouldBe true

                            val booksInOutsideScopeSeries =
                                database.bookQueries.getBooksForSeries(listOf(outsideScopeSeriesId))
                            booksInOutsideScopeSeries
                                .getOrDefault(outsideScopeSeriesId, emptyList())
                                .any { it.id.id == bookId } shouldBe false
                        }
                    }) {
                        fail("Verification failed: $it")
                    }
                }

                scopedAuthorIdString.isNotEmpty() shouldBe true
            }
        }

        "patch metadata rejects blank author names" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-author@example.com",
                        "bookmetaauthor",
                        "Metadata Invalid Author",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request = UpdateBookMetadataRequest(authors = listOf("   ")),
                    errorType = "EmptyBookAuthorName",
                    message = "Author name is required",
                )
            }
        }

        "patch metadata rejects non-https cover URL" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-cover@example.com",
                        "bookmetacover",
                        "Metadata Invalid Cover",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(coverUrl = "http://hardcover.app/cover.jpg"),
                    errorType = "InvalidBookCoverUrl",
                    message = "Invalid cover URL",
                )
            }
        }

        "patch metadata rejects malformed selectedAuthorIds values" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-author-id@example.com",
                        "bookmetaid",
                        "Metadata Invalid AuthorId",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf("Isaac Asimov"),
                            selectedAuthorIds = mapOf("Isaac Asimov" to "not-a-uuid"),
                        ),
                    errorType = "InvalidAuthorId",
                    message = "Invalid author id",
                )
            }
        }

        "patch metadata rejects blank series names" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-series@example.com",
                        "bookmetaseries",
                        "Metadata Invalid Series",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf("Frank Herbert"),
                            series = listOf(StagedSeries(name = "   ", index = 1.0)),
                        ),
                    errorType = "EmptyBookSeriesName",
                    message = "Series name is required",
                )
            }
        }

        "patch metadata rejects invalid publish year" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-year@example.com",
                        "bookmetayear",
                        "Metadata Invalid Year",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request = UpdateBookMetadataRequest(publishYear = 0),
                    errorType = "InvalidBookPublishDate",
                    message = "Invalid book publish date",
                )
            }
        }

        "patch metadata rejects blank publisher" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-invalid-publisher@example.com",
                        "bookmetapublisher",
                        "Metadata Invalid Publisher",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request = UpdateBookMetadataRequest(publisher = "   "),
                    errorType = "EmptyBookPublisher",
                    message = "Publisher is required",
                )
            }
        }

        "patch metadata rejects series updates when authors are missing" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-series-without-authors@example.com",
                        "bookmetascope",
                        "Metadata Series Without Authors",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            series = listOf(StagedSeries(name = "Dune", index = 1.0))
                        ),
                    errorType = "SeriesRequiresAuthors",
                    message = "Series updates require authors",
                )
            }
        }

        "patch metadata rejects selectedAuthorIds keys not present in authors list" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-unknown-selected-author@example.com",
                        "bookmetakeycheck",
                        "Metadata Unknown Selected Author Key",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf("Isaac Asimov"),
                            selectedAuthorIds = mapOf("Frank Herbert" to "not-a-uuid"),
                        ),
                    errorType = "UnknownSelectedAuthorMapping",
                    message = "selectedAuthorIds contains unknown author keys",
                )
            }
        }

        "patch metadata rejects selectedAuthorIds duplicate values across keys" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-duplicate-selected-id@example.com",
                        "bookmetadupid",
                        "Metadata Duplicate Selected Id",
                    )

                val duplicatedId = java.util.UUID.randomUUID().toString()
                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf("Isaac Asimov", "Frank Herbert"),
                            selectedAuthorIds =
                                mapOf(
                                    "Isaac Asimov" to duplicatedId,
                                    "Frank Herbert" to duplicatedId,
                                ),
                        ),
                    errorType = "DuplicateSelectedAuthorIdMapping",
                    message = "selectedAuthorIds contains duplicate author id values",
                )
            }
        }

        "patch metadata rejects duplicate author names after canonicalization" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-duplicate-authors@example.com",
                        "bookmetadup",
                        "Metadata Duplicate Authors",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf(" Frank  Herbert ", "frank herbert")
                        ),
                    errorType = "DuplicateBookAuthors",
                    message = "Duplicate authors are not allowed",
                )
            }
        }

        "patch metadata rejects duplicate series names after canonicalization" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-duplicate-series@example.com",
                        "bookmetadupseries",
                        "Metadata Duplicate Series",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(
                            authors = listOf("Frank Herbert"),
                            series =
                                listOf(
                                    StagedSeries(name = " Dune ", index = 1.0),
                                    StagedSeries(name = "dune", index = 2.0),
                                ),
                        ),
                    errorType = "DuplicateBookSeries",
                    message = "Duplicate series are not allowed",
                )
            }
        }

        "patch metadata rejects duplicate genres after canonicalization" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-duplicate-genres@example.com",
                        "bookmetagenres",
                        "Metadata Duplicate Genres",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request = UpdateBookMetadataRequest(genres = listOf(" Sci Fi ", "sci   fi")),
                    errorType = "DuplicateBookGenres",
                    message = "Duplicate genres are not allowed",
                )
            }
        }

        "patch metadata rejects duplicate moods after canonicalization" {
            testApp { client ->
                val context =
                    registerAndSeedBook(
                        client,
                        "book-metadata-duplicate-moods@example.com",
                        "bookmetamoods",
                        "Metadata Duplicate Moods",
                    )

                assertMetadataPatchError(
                    client = client,
                    token = context.token,
                    bookId = context.bookId,
                    request =
                        UpdateBookMetadataRequest(moods = listOf(" Space Opera ", "space opera")),
                    errorType = "DuplicateBookMoods",
                    message = "Duplicate moods are not allowed",
                )
            }
        }
    })
