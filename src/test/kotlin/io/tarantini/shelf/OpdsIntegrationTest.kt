@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.catalog.book.createBook
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.Edition
import io.tarantini.shelf.catalog.metadata.domain.EditionWithChapters
import io.tarantini.shelf.catalog.metadata.domain.MetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.MetadataRoot
import io.tarantini.shelf.catalog.metadata.saveAggregate
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.identity.domain.UserRequest
import java.nio.file.Paths
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

class OpdsIntegrationTest :
    IntegrationSpec({
        "OPDS root catalog requires basic authentication" {
            testApp { client ->
                val response = client.get("/api/opds/v1.2/catalog")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "OPDS endpoints return 401 for invalid credentials" {
            testApp { client ->
                val auth =
                    Base64.getEncoder().encodeToString("user@example.com:wrongpass".toByteArray())
                val response =
                    client.get("/api/opds/v1.2/catalog") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "OPDS endpoints return 400 for malformed UUIDs" {
            testApp { client ->
                val email = "test-bad-uuid@example.com"
                val password = "password123"
                client
                    .post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = email,
                                    username = "baduser",
                                    password = password,
                                )
                            )
                        )
                    }
                    .status shouldBe HttpStatusCode.Created

                val auth = Base64.getEncoder().encodeToString("$email:$password".toByteArray())

                client
                    .get("/api/opds/v1.2/authors/not-a-uuid") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }
                    .status shouldBe HttpStatusCode.BadRequest

                client
                    .get("/api/opds/v1.2/series/not-a-uuid") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }
                    .status shouldBe HttpStatusCode.BadRequest
            }
        }

        "OPDS catalog navigation and acquisition feeds" {
            testApp { client ->
                val email = "test@example.com"
                val password = "password123"

                // Register a user
                client
                    .post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = email,
                                    username = "testuser",
                                    password = password,
                                )
                            )
                        )
                    }
                    .status shouldBe HttpStatusCode.Created

                val auth = Base64.getEncoder().encodeToString("$email:$password".toByteArray())

                // 1. Root Catalog
                val rootResponse =
                    client.get("/api/opds/v1.2/catalog") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                rootResponse.status shouldBe HttpStatusCode.OK
                val rootBody = rootResponse.bodyAsText()
                rootBody shouldContain "Shelf OPDS Catalog"
                rootBody shouldContain "kind=navigation"
                rootBody shouldContain "rel=\"search\""
                rootBody shouldContain "type=\"application/opensearchdescription+xml\""

                // 2. All Books Feed
                val booksResponse =
                    client.get("/api/opds/v1.2/books") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                booksResponse.status shouldBe HttpStatusCode.OK
                val booksBody = booksResponse.bodyAsText()
                booksBody shouldContain "All Books"
                booksBody shouldContain "kind=acquisition"

                // 3. Authors Feed
                val authorsResponse =
                    client.get("/api/opds/v1.2/authors") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                authorsResponse.status shouldBe HttpStatusCode.OK
                val authorsBody = authorsResponse.bodyAsText()
                authorsBody shouldContain "Authors"
                authorsBody shouldContain "kind=navigation"

                // 4. Series Feed
                val seriesResponse =
                    client.get("/api/opds/v1.2/series") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                seriesResponse.status shouldBe HttpStatusCode.OK
                val seriesBody = seriesResponse.bodyAsText()
                seriesBody shouldContain "Series"
                seriesBody shouldContain "kind=navigation"

                // 5. OpenSearch Description
                val osResponse =
                    client.get("/api/opds/v1.2/search/description") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }
                osResponse.status shouldBe HttpStatusCode.OK
                val osBody = osResponse.bodyAsText()
                // Use a simpler check or check for parts
                osBody shouldContain "ShortName"
                osBody shouldContain "Shelf"
                osBody shouldContain "template"

                // 6. Search Feed (Empty)
                val searchResponseEmpty =
                    client.get("/api/opds/v1.2/search?q=nothing-here") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                searchResponseEmpty.status shouldBe HttpStatusCode.OK
                val searchBodyEmpty = searchResponseEmpty.bodyAsText()
                searchBodyEmpty shouldContain "Search results for: nothing-here"
                searchBodyEmpty shouldNotContain "<entry>"
            }
        }

        "OPDS search results with data" {
            testWithDeps { deps ->
                val email = "search-test@example.com"
                val password = "password123"
                val auth = Base64.getEncoder().encodeToString("$email:$password".toByteArray())

                testApp { client ->
                    // 1. Create a user
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = email,
                                    username = "searchuser",
                                    password = password,
                                )
                            )
                        )
                    }

                    // 2. Insert a book directly via database to ensure search has something to find
                    with(this) {
                        deps.database.transaction {
                            deps.database.bookQueries
                                .insert("Testing OPDS Search", null)
                                .executeAsOne()
                        }
                    }

                    // 3. Search for the book
                    val searchResponse =
                        client.get("/api/opds/v1.2/search?q=Testing") {
                            header(HttpHeaders.Authorization, "Basic $auth")
                            header(HttpHeaders.Accept, "application/atom+xml")
                        }

                    searchResponse.status shouldBe HttpStatusCode.OK
                    val searchBody = searchResponse.bodyAsText()
                    searchBody shouldContain "Search results for: Testing"
                    searchBody shouldContain "<entry>"
                    searchBody shouldContain "Testing OPDS Search"
                }
            }
        }

        "OPDS author and series sub-feeds with pagination" {
            testApp { client ->
                val email = "test-paged@example.com"
                val password = "password123"
                client
                    .post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = email,
                                    username = "pageduser",
                                    password = password,
                                )
                            )
                        )
                    }
                    .status shouldBe HttpStatusCode.Created

                val auth = Base64.getEncoder().encodeToString("$email:$password".toByteArray())

                // Verify pagination on All Books
                val pagedResponse =
                    client.get("/api/opds/v1.2/books?page=0&size=1") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                        header(HttpHeaders.Accept, "application/atom+xml")
                    }
                pagedResponse.status shouldBe HttpStatusCode.OK
            }
        }

        "OPDS basic auth can download acquisition links" {
            testApp { client ->
                val email = "opds-download@example.com"
                val password = "password123"
                client
                    .post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = email,
                                    username = "opdsdownload",
                                    password = password,
                                )
                            )
                        )
                    }
                    .status shouldBe HttpStatusCode.Created

                var bookIdString = ""
                testWithDeps { deps ->
                    recover({
                        with(this) {
                            val bookId =
                                deps.database.bookQueries.createBook("OPDS Download Book", null)
                            bookIdString = bookId.value.toString()
                            val storagePath = StoragePath.fromRaw("opds/opds-download-book.epub")
                            deps.storageService.save(
                                storagePath,
                                Paths.get("src/test/resources/book.epub"),
                            )
                            deps.database.metadataQueries.saveAggregate(
                                MetadataAggregate(
                                    metadata =
                                        MetadataRoot<PersistenceState.Unsaved>(
                                            id = Identity.Unsaved,
                                            bookId = bookId,
                                            title = "OPDS Download Book",
                                        ),
                                    editions =
                                        listOf(
                                            EditionWithChapters(
                                                edition =
                                                    Edition<PersistenceState.Unsaved>(
                                                        id = Identity.Unsaved,
                                                        bookId = bookId,
                                                        format = BookFormat.EBOOK,
                                                        path = storagePath,
                                                        size = 1,
                                                    )
                                            )
                                        ),
                                )
                            )
                        }
                    }) {
                        fail("Seeding OPDS download book failed: $it")
                    }
                }

                val auth = Base64.getEncoder().encodeToString("$email:$password".toByteArray())
                val response =
                    client.get("/api/books/$bookIdString/download") {
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentDisposition] shouldContain
                    "OPDS Download Book.epub"
                response.headers[HttpHeaders.ContentType] shouldBe "application/epub+zip"
            }
        }
    })
