@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.user.identity

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import io.tarantini.shelf.user.identity.domain.LoginUserRequest
import io.tarantini.shelf.user.identity.domain.NewUserRequest
import io.tarantini.shelf.user.identity.domain.SetupStatusResponse
import io.tarantini.shelf.user.identity.domain.UpdateUserRequest
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserToken
import io.tarantini.shelf.user.identity.domain.UserWithToken
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.userRoutes(userService: UserService, jwtService: JwtService) {
    get<SetupResource> { respond({ SetupStatusResponse(userService.isSetupComplete()) }) }

    post<SetupResource> {
        respond(
            {
                val req = call.receive<Request<UserRequest>>().data
                val (token, user) =
                    userService.setup(NewUserRequest(req.email, req.username, req.password))
                UserWithToken(user, UserToken(token.value))
            },
            HttpStatusCode.Created,
        )
    }

    post<UsersResource.Login> {
        respond({
            val req = call.receive<Request<UserRequest>>().data
            val (token, user) = userService.login(LoginUserRequest(req.email, req.password))
            UserWithToken(user, UserToken(token.value))
        })
    }

    get<UsersResource> {
        jwtAuth(jwtService) { context ->
            with(context) {
                respond({ UserWithToken(userService.getCurrentUser(), UserToken(token.value)) })
            }
        }
    }

    put<UsersResource> {
        jwtAuth(jwtService) { context ->
            with(context) {
                respond({
                    val req = call.receive<Request<UserRequest>>().data
                    val user =
                        userService.update(
                            UpdateUserRequest(null, req.email, req.username, req.password)
                        )
                    UserWithToken(user, UserToken(token.value))
                })
            }
        }
    }

    post<UsersResource> {
        respond(
            {
                val req = call.receive<Request<UserRequest>>().data
                val (token, user) =
                    userService.register(NewUserRequest(req.email, req.username, req.password))
                UserWithToken(user, UserToken(token.value))
            },
            HttpStatusCode.Created,
        )
    }
}
