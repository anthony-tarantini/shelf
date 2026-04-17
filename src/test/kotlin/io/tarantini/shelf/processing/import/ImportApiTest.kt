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
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserRole
import io.tarantini.shelf.user.identity.domain.UserWithToken
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
    })
