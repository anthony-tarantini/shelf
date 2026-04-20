@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.search

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.book.createBook
import io.tarantini.shelf.catalog.search.domain.GlobalSearchResult
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.activity.domain.ReadStatusRequest
import kotlin.uuid.ExperimentalUuidApi

class SearchApiTest :
    IntegrationSpec({
        "global search should return books, authors and series" {
            testApp { client ->
                val token = registerUser(client, "search-api@example.com", "searchapi")
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            database.bookQueries.createBook("Searchable Book", null)
                            database.authorQueries.createAuthor("Searchable Author")
                            database.seriesQueries.createSeries("Searchable Series")
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val response =
                    client.get("/api/search?q=Searchable") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                response.status shouldBe HttpStatusCode.OK
                val results = response.body<Response<GlobalSearchResult>>().data

                results.books.any { it.title == "Searchable Book" } shouldBe true
                results.authors.any { it.name == "Searchable Author" } shouldBe true
                results.series.any { it.name == "Searchable Series" } shouldBe true
            }
        }

        "global search should require authentication" {
            testApp { client ->
                val response = client.get("/api/search?q=Searchable")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "global search should include the current user's read status for books" {
            testApp { client ->
                val token = registerUser(client, "search-status@example.com", "searchstatus")
                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(deps.database) {
                            val id = bookQueries.createBook("Search Status Book", null)
                            bookIdString = id.value.toString()
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val updateResponse =
                    client.put("/api/books/$bookIdString/status") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ReadStatusRequest(ReadStatus.QUEUED)))
                    }
                updateResponse.status shouldBe HttpStatusCode.OK

                val response =
                    client.get("/api/search?q=Search%20Status") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                response.status shouldBe HttpStatusCode.OK
                val results = response.body<Response<GlobalSearchResult>>().data

                results.books
                    .first { it.id.value.toString() == bookIdString }
                    .userState
                    ?.readStatus shouldBe ReadStatus.QUEUED
            }
        }
    })
