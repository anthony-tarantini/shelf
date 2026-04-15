@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog

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
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorRequest
import io.tarantini.shelf.catalog.author.domain.AuthorSummary
import io.tarantini.shelf.catalog.author.domain.SavedAuthorRoot
import io.tarantini.shelf.catalog.book.createBook
import io.tarantini.shelf.catalog.book.domain.SavedBookAggregate
import io.tarantini.shelf.catalog.book.linkSeries
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.domain.SavedSeriesRoot
import io.tarantini.shelf.catalog.series.domain.SeriesRequest
import io.tarantini.shelf.catalog.series.domain.SeriesSummary
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.activity.domain.ReadStatusRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CatalogApiTest :
    IntegrationSpec({
        "catalog authors should require authentication" {
            testApp { client ->
                client.get("/api/authors").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "list and update authors" {
            testApp { client ->
                val token = registerUser(client, "catalog-authors@example.com", "catalogauthors")
                // Seed an author via persistence since there is no POST /api/authors
                var authorIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val id = database.authorQueries.createAuthor("Original Author")
                            authorIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                // List authors
                val listResponse =
                    client.get("/api/authors") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                listResponse.status shouldBe HttpStatusCode.OK
                val authors = listResponse.body<Response<List<AuthorSummary>>>().data
                authors.any { it.name == "Original Author" } shouldBe true

                // Update author
                val updateRequest =
                    Request(AuthorRequest(id = authorIdString, name = "Updated Author"))
                val updateResponse =
                    client.put("/api/authors") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(updateRequest)
                    }
                updateResponse.status shouldBe HttpStatusCode.OK
                updateResponse.body<Response<SavedAuthorRoot>>().data.name shouldBe "Updated Author"
            }
        }

        "catalog series should require authentication" {
            testApp { client ->
                client.get("/api/series").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "CRUD series" {
            testApp { client ->
                val token = registerUser(client, "catalog-series@example.com", "catalogseries")
                val uniqueTitle = "Foundation ${Uuid.random()}"
                // Create
                val createRequest = Request(SeriesRequest(title = uniqueTitle))
                val createResponse =
                    client.post("/api/series") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                if (createResponse.status != HttpStatusCode.Created) {
                    println(
                        "CREATE FAILED: ${createResponse.status} ${createResponse.bodyAsText()}"
                    )
                }
                createResponse.status shouldBe HttpStatusCode.Created
                val savedSeries = createResponse.body<Response<SavedSeriesRoot>>().data
                savedSeries.name shouldBe uniqueTitle
                val seriesId = savedSeries.id.id

                // List
                val listResponse =
                    client.get("/api/series") { header(HttpHeaders.Authorization, "Bearer $token") }
                listResponse.status shouldBe HttpStatusCode.OK
                val seriesList = listResponse.body<Response<List<SeriesSummary>>>().data
                seriesList.any { it.name == uniqueTitle } shouldBe true

                // Update
                val updateRequest = Request(SeriesRequest(title = "$uniqueTitle Updated"))
                val updateResponse =
                    client.put("/api/series/${seriesId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(updateRequest)
                    }
                if (updateResponse.status != HttpStatusCode.OK) {
                    println(
                        "UPDATE FAILED: ${updateResponse.status} ${updateResponse.bodyAsText()}"
                    )
                }
                updateResponse.status shouldBe HttpStatusCode.OK
                updateResponse.body<Response<SavedSeriesRoot>>().data.name shouldBe
                    "$uniqueTitle Updated"

                // Delete
                val deleteResponse =
                    client.delete("/api/series/${seriesId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                if (deleteResponse.status.value != 204) {
                    fail(
                        "DELETE FAILED: status=${deleteResponse.status.value} body=${deleteResponse.bodyAsText()}"
                    )
                }

                // Verify deleted
                val getResponse =
                    client.get("/api/series/${seriesId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                if (getResponse.status != HttpStatusCode.NotFound) {
                    println("STILL EXISTS: ${getResponse.status} ${getResponse.bodyAsText()}")
                }
                getResponse.status shouldBe HttpStatusCode.NotFound
            }
        }

        "series should derive cover from earliest indexed covered book" {
            testApp { client ->
                val token =
                    registerUser(client, "catalog-series-cover@example.com", "catalogseriescover")
                var seriesIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps.database) {
                            val bookWithoutCover = bookQueries.createBook("Series Book 1", null)
                            val bookWithCover =
                                bookQueries.createBook(
                                    "Series Book 2",
                                    StoragePath.fromRaw("books/series-book-2/cover.jpg"),
                                )
                            val seriesId = seriesQueries.createSeries("Series With Cover")
                            bookQueries.linkSeries(bookWithoutCover, seriesId, 1.0)
                            bookQueries.linkSeries(bookWithCover, seriesId, 2.0)
                            seriesIdString = seriesId.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val listResponse =
                    client.get("/api/series") { header(HttpHeaders.Authorization, "Bearer $token") }
                listResponse.status shouldBe HttpStatusCode.OK
                val seriesList = listResponse.body<Response<List<SeriesSummary>>>().data
                val series = seriesList.first { it.id.value.toString() == seriesIdString }
                series.coverPath?.value shouldBe "books/series-book-2/cover.jpg"

                val detailResponse =
                    client.get("/api/series/$seriesIdString") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                detailResponse.status shouldBe HttpStatusCode.OK
                detailResponse.body<Response<SavedSeriesRoot>>().data.coverPath?.value shouldBe
                    "books/series-book-2/cover.jpg"
            }
        }

        "book read status should default to unread and enrich book detail" {
            testApp { client ->
                val token =
                    registerUser(client, "catalog-read-status@example.com", "catalogreadstatus")
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps.database) {
                            val id = bookQueries.createBook("Read Status Book", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val initialStatusResponse =
                    client.get("/api/books/$bookIdString/status") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                initialStatusResponse.status shouldBe HttpStatusCode.OK
                initialStatusResponse.body<Response<ReadStatus>>().data shouldBe ReadStatus.UNREAD

                val updateStatusResponse =
                    client.put("/api/books/$bookIdString/status") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ReadStatusRequest(ReadStatus.READING)))
                    }
                updateStatusResponse.status shouldBe HttpStatusCode.OK

                val updatedStatusResponse =
                    client.get("/api/books/$bookIdString/status") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                updatedStatusResponse.status shouldBe HttpStatusCode.OK
                updatedStatusResponse.body<Response<ReadStatus>>().data shouldBe ReadStatus.READING

                val detailResponse =
                    client.get("/api/books/$bookIdString/details") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                detailResponse.status shouldBe HttpStatusCode.OK
                detailResponse
                    .body<Response<SavedBookAggregate>>()
                    .data
                    .userState
                    ?.readStatus shouldBe ReadStatus.READING
            }
        }
    })
