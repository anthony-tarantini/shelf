@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.NewEdition
import io.tarantini.shelf.catalog.metadata.saveEdition
import io.tarantini.shelf.integration.koreader.domain.ProgressPayload
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.identity.ApiToken
import io.tarantini.shelf.user.identity.CreateTokenRequest
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserWithToken
import kotlin.uuid.ExperimentalUuidApi

class KoreaderApiTest :
    IntegrationSpec({
        "sync reading progress" {
            testApp { client ->
                // 1. Setup User and Token
                val registerRequest =
                    Request(
                        UserRequest(
                            email = "koreader-test@example.com",
                            username = "koreaderuser",
                            password = "password123",
                        )
                    )
                val registerResponse =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(registerRequest)
                    }
                val jwt = registerResponse.body<Response<UserWithToken>>().data.token.value
                // userId is not needed for the rest of the test but we can keep it if we fix it
                // val userId = registerResponse.body<Response<UserWithToken>>().data.user.id.value

                val createTokenResponse =
                    client.post("/api/tokens") {
                        header(HttpHeaders.Authorization, "Bearer $jwt")
                        contentType(ContentType.Application.Json)
                        setBody(Request(CreateTokenRequest(description = "Koreader Device")))
                    }
                val apiToken = createTokenResponse.body<Response<ApiToken>>().data.token

                // 2. Seed a book with a specific MD5 hash
                val documentHash = "81dc9bdb52d04dc20036dbd8313ed055" // MD5 for "1234"
                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            val bookId =
                                database.bookQueries.insert("Koreader Book", null).executeAsOne()
                            database.metadataQueries.saveEdition(
                                NewEdition(
                                    id = io.tarantini.shelf.app.Identity.Unsaved,
                                    bookId = bookId,
                                    format = BookFormat.EBOOK,
                                    path = StoragePath.fromRaw("books/test.epub"),
                                    fileHash = documentHash,
                                    size = 1024,
                                )
                            )
                        }
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                // 3. Update progress from Koreader
                val payload =
                    ProgressPayload(
                        document = documentHash,
                        progress = "0.5",
                        device = "Kindle",
                        device_id = "device123",
                        timestamp = System.currentTimeMillis() / 1000,
                    )

                val updateResponse =
                    client.put("/koreader/sync/syncs/progress") {
                        header("x-auth-user", "koreaderuser")
                        header("x-auth-key", apiToken)
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                updateResponse.status shouldBe HttpStatusCode.OK

                // 4. Retrieve progress
                val getResponse =
                    client.get("/koreader/sync/syncs/progress/$documentHash") {
                        header("x-auth-user", "koreaderuser")
                        header("x-auth-key", apiToken)
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val responseBody = getResponse.body<String>()
                val timestampStr = payload.timestamp.toString()
                responseBody.contains(documentHash) shouldBe true
                responseBody.contains("0.5") shouldBe true
                responseBody.contains("Kindle") shouldBe true
                responseBody.contains(timestampStr) shouldBe true
            }
        }
    })
