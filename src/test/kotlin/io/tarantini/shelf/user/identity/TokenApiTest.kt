@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserWithToken
import kotlin.uuid.ExperimentalUuidApi

class TokenApiTest :
    IntegrationSpec({
        "manage API tokens" {
            testApp { client ->
                // 1. Register and get JWT
                val registerRequest =
                    Request(
                        UserRequest(
                            email = "token-test@example.com",
                            username = "tokentest",
                            password = "password123",
                        )
                    )
                val registerResponse =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(registerRequest)
                    }
                val jwt = registerResponse.body<Response<UserWithToken>>().data.token.value

                // 2. Create a new API token
                val createRequest = CreateTokenRequest(description = "My Kindle")
                val createResponse =
                    client.post("/api/tokens") {
                        header(HttpHeaders.Authorization, "Bearer $jwt")
                        contentType(ContentType.Application.Json)
                        setBody(Request(createRequest))
                    }
                createResponse.status shouldBe HttpStatusCode.Created
                val newToken = createResponse.body<Response<ApiToken>>().data
                newToken.description shouldBe "My Kindle"
                newToken.token shouldNotBe ""

                // 3. List tokens
                val listResponse =
                    client.get("/api/tokens") { header(HttpHeaders.Authorization, "Bearer $jwt") }
                listResponse.status shouldBe HttpStatusCode.OK
                val tokens = listResponse.body<Response<List<ApiToken>>>().data
                tokens shouldHaveSize 1
                tokens[0].description shouldBe "My Kindle"
                tokens[0].token shouldBe "" // Should be empty in list for security

                // 4. Delete/Revoke the token
                val deleteResponse =
                    client.delete("/api/tokens/${newToken.id.value}") {
                        header(HttpHeaders.Authorization, "Bearer $jwt")
                    }
                deleteResponse.status shouldBe HttpStatusCode.NoContent

                // 5. Verify token is now gone
                val listAfterDeleteResponse =
                    client.get("/api/tokens") { header(HttpHeaders.Authorization, "Bearer $jwt") }
                listAfterDeleteResponse.status shouldBe HttpStatusCode.OK
                val tokensAfterDelete =
                    listAfterDeleteResponse.body<Response<List<ApiToken>>>().data
                tokensAfterDelete shouldHaveSize 0
            }
        }

        "prevent one user from deleting another user's token" {
            testApp { client ->
                val userOneJwt =
                    client
                        .post("/api/users") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                Request(
                                    UserRequest(
                                        email = "token-owner@example.com",
                                        username = "tokenowner",
                                        password = "password123",
                                    )
                                )
                            )
                        }
                        .body<Response<UserWithToken>>()
                        .data
                        .token
                        .value

                val userTwoJwt =
                    client
                        .post("/api/users") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                Request(
                                    UserRequest(
                                        email = "token-attacker@example.com",
                                        username = "tokenattacker",
                                        password = "password123",
                                    )
                                )
                            )
                        }
                        .body<Response<UserWithToken>>()
                        .data
                        .token
                        .value

                val createdToken =
                    client
                        .post("/api/tokens") {
                            header(HttpHeaders.Authorization, "Bearer $userOneJwt")
                            contentType(ContentType.Application.Json)
                            setBody(Request(CreateTokenRequest(description = "Private device")))
                        }
                        .body<Response<ApiToken>>()
                        .data

                val deleteResponse =
                    client.delete("/api/tokens/${createdToken.id.value}") {
                        header(HttpHeaders.Authorization, "Bearer $userTwoJwt")
                    }

                deleteResponse.status shouldBe HttpStatusCode.Forbidden
            }
        }
    })
