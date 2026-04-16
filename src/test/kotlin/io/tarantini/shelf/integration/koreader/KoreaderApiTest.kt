@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.NewEdition
import io.tarantini.shelf.catalog.metadata.saveEdition
import io.tarantini.shelf.integration.koreader.domain.ProgressPayload
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class KoreaderApiTest :
    IntegrationSpec({
        "sync reading progress" {
            testApp { client ->
                // 1. Setup KOReader account
                val username = "koreaderuser"
                val password = "password123"
                val createResponse =
                    client.post("/koreader/sync/users/create") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("username" to username, "password" to password))
                    }
                createResponse.status shouldBe HttpStatusCode.Created

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
                        header("x-auth-user", username)
                        header("x-auth-key", password)
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                updateResponse.status shouldBe HttpStatusCode.OK

                // 4. Retrieve progress
                val getResponse =
                    client.get("/koreader/sync/syncs/progress/$documentHash") {
                        header("x-auth-user", username)
                        header("x-auth-key", password)
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
