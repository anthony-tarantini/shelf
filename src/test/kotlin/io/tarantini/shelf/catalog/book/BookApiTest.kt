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
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.domain.SavedBookAggregate
import io.tarantini.shelf.catalog.book.domain.SavedBookRoot
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.activity.domain.ReadingProgressKind
import kotlin.uuid.ExperimentalUuidApi

class BookApiTest :
    IntegrationSpec({
        "list and get books" {
            testApp { client ->
                val token = registerUser(client, "book-api@example.com", "bookapi")
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.bookQueries.createBook("Api Test Book", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                // List
                val listResponse =
                    client.get("/api/books") { header(HttpHeaders.Authorization, "Bearer $token") }
                listResponse.status shouldBe HttpStatusCode.OK
                val books = listResponse.body<Response<List<BookSummary>>>().data
                books.any { it.title == "Api Test Book" } shouldBe true

                // Get by ID
                val getResponse =
                    client.get("/api/books/$bookIdString") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                getResponse.body<Response<SavedBookRoot>>().data.title shouldBe "Api Test Book"

                // Get Details
                val detailsResponse =
                    client.get("/api/books/$bookIdString/details") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                if (detailsResponse.status != HttpStatusCode.OK) {
                    fail(
                        "GET DETAILS FAILED: status=${detailsResponse.status} body=${detailsResponse.bodyAsText()}"
                    )
                }
                detailsResponse.status shouldBe HttpStatusCode.OK
                detailsResponse.body<Response<SavedBookAggregate>>().data.book.title shouldBe
                    "Api Test Book"
            }
        }

        "delete a book" {
            testApp { client ->
                val token = registerUser(client, "book-delete@example.com", "bookdelete")
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.bookQueries.createBook("To Delete", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

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
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.bookQueries.createBook("Progress Ebook", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

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
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.bookQueries.createBook("Progress Audio", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

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
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.bookQueries.createBook("Progress Both", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

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
    })
