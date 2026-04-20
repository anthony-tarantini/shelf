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
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi

class KoreaderApiTest :
    IntegrationSpec({
        "sync reading progress" {
            testApp { client ->
                // 1. Register user and create API token
                val username = "koreaderuser"
                val jwt =
                    registerUser(
                        client,
                        email = "koreader@test.com",
                        username = username,
                        password = "password123",
                    )

                val tokenResponse =
                    client.post("/api/tokens") {
                        header(HttpHeaders.Authorization, "Bearer $jwt")
                        contentType(ContentType.Application.Json)
                        setBody(Request(CreateTokenRequest(description = "KOReader")))
                    }
                tokenResponse.status shouldBe HttpStatusCode.Created
                val rawToken = tokenResponse.body<Response<ApiToken>>().data.token

                // KOReader MD5-hashes the token before sending
                val md5Key = md5Hex(rawToken)

                // 2. Link KOReader credentials (validates MD5 against stored token)
                val createResponse =
                    client.post("/koreader/sync/users/create") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("username" to username, "password" to md5Key))
                    }
                createResponse.status shouldBe HttpStatusCode.Created

                // 3. Seed a book with a specific MD5 hash
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

                // 4. Update progress from KOReader
                val payload =
                    ProgressPayload(
                        document = documentHash,
                        progress = "0.5",
                        device = "Kindle",
                        deviceId = "device123",
                        percentage = 10.0,
                    )

                val updateResponse =
                    client.put("/koreader/sync/syncs/progress") {
                        header("x-auth-user", username)
                        header("x-auth-key", md5Key)
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                updateResponse.status shouldBe HttpStatusCode.OK

                // 5. Retrieve progress
                val getResponse =
                    client.get("/koreader/sync/syncs/progress/$documentHash") {
                        header("x-auth-user", username)
                        header("x-auth-key", md5Key)
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val responseBody = getResponse.body<String>()
                responseBody.contains(documentHash) shouldBe true
                responseBody.contains("0.5") shouldBe true
                responseBody.contains("Kindle") shouldBe true
            }
        }
    })

private fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") {
        "%02x".format(it)
    }
