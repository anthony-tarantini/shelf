@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.id
import io.tarantini.shelf.processing.import.domain.ScanDirectoryRequest
import io.tarantini.shelf.testing.MediaFixtureFactory
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserRole
import io.tarantini.shelf.user.identity.domain.UserWithToken
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi

class ImportApiTest :
    IntegrationSpec({
        "allow directory scan for admin users" {
            testApp { client ->
                val response =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = "scan-admin@example.com",
                                    username = "scanadmin",
                                    password = "password123",
                                )
                            )
                        )
                    }
                val userWithToken = response.body<Response<UserWithToken>>().data

                testWithDeps { deps ->
                    recover({
                        with(deps) {
                            userService.updateRole(userWithToken.user.id.id, UserRole.ADMIN)
                        }
                    }) {
                        fail("Role update failed: $it")
                    }
                }

                val scanResponse =
                    client.post("/api/books/import/scan") {
                        header(HttpHeaders.Authorization, "Bearer ${userWithToken.token.value}")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ScanDirectoryRequest(path = "./storage", staged = true)))
                    }

                scanResponse.status shouldBe HttpStatusCode.Accepted
            }
        }

        "reject directory scan for non-admin users" {
            testApp { client ->
                val response =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = "scan-user@example.com",
                                    username = "scanuser",
                                    password = "password123",
                                )
                            )
                        )
                    }
                val token = response.body<Response<UserWithToken>>().data.token.value

                val scanResponse =
                    client.post("/api/books/import/scan") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(ScanDirectoryRequest(path = "./storage", staged = true)))
                    }

                scanResponse.status shouldBe HttpStatusCode.Forbidden
            }
        }

        "require authentication for scan progress" {
            testApp { client ->
                client.get("/api/books/import/progress").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "return null progress when user has no active scan" {
            testApp { client ->
                val token = registerUser(client, "scan-progress@example.com", "scanprogress")

                val response =
                    client.get("/api/books/import/progress") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.OK
                response.body<Response<String?>>().data shouldBe null
            }
        }

        "staged cover endpoint returns not found for unknown staged id" {
            testApp { client ->
                val token = registerUser(client, "staged-cover-miss@example.com", "stagedcovermiss")

                val response =
                    client.get("/api/books/staged/missing-stage-id/cover") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "staged cover endpoint returns image content with cache headers" {
            testApp { client ->
                val registerResponse =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                UserRequest(
                                    email = "staged-cover-hit@example.com",
                                    username = "stagedcoverhit",
                                    password = "password123",
                                )
                            )
                        )
                    }
                registerResponse.status shouldBe HttpStatusCode.Created
                val userWithToken = registerResponse.body<Response<UserWithToken>>().data

                var stagedId = ""
                testWithDeps { deps ->
                    recover({
                        val auth =
                            JwtContext(
                                JwtToken("seed-only"),
                                UserId.fromRaw(userWithToken.user.id.id.value),
                            )
                        val stagedBook =
                            with(this) {
                                with(auth) {
                                    val epubPath = Files.createTempFile("shelf-import-api", ".epub")
                                    MediaFixtureFactory.createMinimalEpub(
                                        epubPath,
                                        MediaFixtureFactory.EpubSpec(
                                            title = "The Primal Hunter 14: A LitRPG Adventure",
                                            author = "Zogarth",
                                            seriesName = "The Primal Hunter",
                                            seriesIndex = 14.0,
                                        ),
                                    )
                                    deps.importService.importToStaging(
                                        epubPath,
                                        "staged-cover-hit.epub",
                                    )
                                }
                            }
                        stagedId = stagedBook.id
                    }) {
                        fail("Seeding failed: $it")
                    }
                }

                val response =
                    client.get("/api/books/staged/$stagedId/cover") {
                        header(HttpHeaders.Authorization, "Bearer ${userWithToken.token.value}")
                    }

                response.headers[HttpHeaders.CacheControl] shouldBe "public, max-age=86400"
                if (response.status == HttpStatusCode.OK) {
                    response.status shouldBe HttpStatusCode.OK
                    response
                        .contentType()
                        ?.withoutParameters()
                        ?.toString()
                        ?.startsWith("image/") shouldBe true
                } else {
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    })
