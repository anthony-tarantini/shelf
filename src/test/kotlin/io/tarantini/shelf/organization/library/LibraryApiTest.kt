@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.id
import io.tarantini.shelf.organization.library.domain.LibraryRequest
import io.tarantini.shelf.organization.library.domain.LibrarySummary
import io.tarantini.shelf.organization.library.domain.SavedLibraryRoot
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserWithToken
import kotlin.uuid.ExperimentalUuidApi

class LibraryApiTest :
    IntegrationSpec({
        "libraries should require authentication" {
            testApp { client ->
                client.get("/api/libraries").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        suspend fun getAuthToken(client: io.ktor.client.HttpClient, email: String): String {
            val registerRequest =
                Request(
                    UserRequest(
                        email = email,
                        username = email.substringBefore("@"),
                        password = "password123",
                    )
                )
            val response =
                client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }
            return response.body<Response<UserWithToken>>().data.token.value
        }

        "create and list libraries" {
            testApp { client ->
                val token = getAuthToken(client, "lib-api-test-1@example.com")

                // Create a library
                val createRequest = Request(LibraryRequest(title = "API Library"))
                val createResponse =
                    client.post("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                createResponse.status shouldBe HttpStatusCode.Created
                val savedLibrary = createResponse.body<Response<SavedLibraryRoot>>().data
                savedLibrary.title shouldBe "API Library"

                // List libraries
                val listResponse =
                    client.get("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                listResponse.status shouldBe HttpStatusCode.OK
                val libraries = listResponse.body<Response<List<LibrarySummary>>>().data
                libraries.any { it.title == "API Library" } shouldBe true
            }
        }

        "update a library" {
            testApp { client ->
                val token = getAuthToken(client, "lib-api-test-2@example.com")

                // Create
                val createRequest = Request(LibraryRequest(title = "To Update"))
                val createResponse =
                    client.post("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                val libraryId = createResponse.body<Response<SavedLibraryRoot>>().data.id.id

                // Update
                val updateRequest = Request(LibraryRequest(title = "Updated Title"))
                val updateResponse =
                    client.put("/api/libraries/${libraryId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(updateRequest)
                    }
                updateResponse.status shouldBe HttpStatusCode.OK
                updateResponse.body<Response<SavedLibraryRoot>>().data.title shouldBe
                    "Updated Title"
            }
        }

        "update with null title keeps existing title" {
            testApp { client ->
                val token = getAuthToken(client, "lib-api-test-null-title@example.com")

                val createResponse =
                    client.post("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(LibraryRequest(title = "Keep Me")))
                    }
                createResponse.status shouldBe HttpStatusCode.Created
                val libraryId = createResponse.body<Response<SavedLibraryRoot>>().data.id.id

                val updateResponse =
                    client.put("/api/libraries/${libraryId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(LibraryRequest(title = null)))
                    }
                updateResponse.status shouldBe HttpStatusCode.OK
                updateResponse.body<Response<SavedLibraryRoot>>().data.title shouldBe "Keep Me"
            }
        }

        "delete a library" {
            testApp { client ->
                val token = getAuthToken(client, "lib-api-test-3@example.com")

                // Create
                val createRequest = Request(LibraryRequest(title = "To Delete"))
                val createResponse =
                    client.post("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                val libraryId = createResponse.body<Response<SavedLibraryRoot>>().data.id.id

                // Delete
                val deleteResponse =
                    client.delete("/api/libraries/${libraryId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                deleteResponse.status shouldBe HttpStatusCode.NoContent

                // Verify deleted
                val getResponse =
                    client.get("/api/libraries/${libraryId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.NotFound
            }
        }

        "fail to access another user's library" {
            testApp { client ->
                val token1 = getAuthToken(client, "user1@example.com")
                val token2 = getAuthToken(client, "user2@example.com")

                // User 1 creates a library
                val createRequest = Request(LibraryRequest(title = "User 1 Library"))
                val createResponse =
                    client.post("/api/libraries") {
                        header(HttpHeaders.Authorization, "Bearer $token1")
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                val libraryId = createResponse.body<Response<SavedLibraryRoot>>().data.id.id

                // User 2 tries to access it
                val getResponse =
                    client.get("/api/libraries/${libraryId.value}") {
                        header(HttpHeaders.Authorization, "Bearer $token2")
                    }
                getResponse.status shouldBe HttpStatusCode.Forbidden
            }
        }
    })
