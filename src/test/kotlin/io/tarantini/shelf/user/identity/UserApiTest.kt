@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

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

class UserApiTest :
    IntegrationSpec({
        "register a new user" {
            testApp { client ->
                val request =
                    Request(
                        UserRequest(
                            email = "api-test@example.com",
                            username = "apitestuser",
                            password = "password123",
                        )
                    )

                val response =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Response<UserWithToken>>()
                body.data.user.email.value shouldBe request.data.email
                body.data.user.username.value shouldBe request.data.username
                body.data.token.value shouldNotBe ""
            }
        }

        "login as an existing user" {
            testApp { client ->
                val registerRequest =
                    Request(
                        UserRequest(
                            email = "login-test@example.com",
                            username = "logintestuser",
                            password = "password123",
                        )
                    )

                client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }

                val loginRequest =
                    Request(
                        UserRequest(
                            email = registerRequest.data.email,
                            password = registerRequest.data.password,
                        )
                    )

                val response =
                    client.post("/api/users/login") {
                        contentType(ContentType.Application.Json)
                        setBody(loginRequest)
                    }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Response<UserWithToken>>()
                body.data.user.email.value shouldBe loginRequest.data.email
                body.data.token.value shouldNotBe ""
            }
        }

        "fail login with incorrect password" {
            testApp { client ->
                val registerRequest =
                    Request(
                        UserRequest(
                            email = "fail-login@example.com",
                            username = "faillogin",
                            password = "password123",
                        )
                    )

                client.post("/api/users") {
                    contentType(ContentType.Application.Json)
                    setBody(registerRequest)
                }

                val loginRequest =
                    Request(
                        UserRequest(email = registerRequest.data.email, password = "wrongpassword")
                    )

                val response =
                    client.post("/api/users/login") {
                        contentType(ContentType.Application.Json)
                        setBody(loginRequest)
                    }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "get current user with valid token" {
            testApp { client ->
                val registerRequest =
                    Request(
                        UserRequest(
                            email = "current-user@example.com",
                            username = "currentuser",
                            password = "password123",
                        )
                    )

                val registerResponse =
                    client.post("/api/users") {
                        contentType(ContentType.Application.Json)
                        setBody(registerRequest)
                    }
                val token = registerResponse.body<Response<UserWithToken>>().data.token.value

                val response =
                    client.get("/api/users") { header(HttpHeaders.Authorization, "Bearer $token") }

                response.status shouldBe HttpStatusCode.OK
                val body =
                    response.body<Response<UserWithToken>>() // Routes says it returns UserWithToken
                body.data.user.email.value shouldBe registerRequest.data.email
            }
        }
    })
