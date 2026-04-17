package io.tarantini.shelf.user.identity

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import io.tarantini.shelf.user.identity.domain.TokenId
import kotlinx.serialization.Serializable

@Serializable data class CreateTokenRequest(val description: String)

fun Route.tokenRoutes(tokenService: TokenService, jwtService: JwtService) {
    route("/api/tokens") {
        get {
            jwtAuth(jwtService) { auth ->
                with(auth) { respond({ tokenService.getTokens(userId) }) }
            }
        }

        post {
            jwtAuth(jwtService) { auth ->
                val request = call.receive<Request<CreateTokenRequest>>().data
                with(auth) {
                    respond(
                        { tokenService.createToken(userId, request.description) },
                        HttpStatusCode.Created,
                    )
                }
            }
        }

        delete("/{id}") {
            jwtAuth(jwtService) { auth ->
                val id =
                    call.parameters["id"] ?: return@jwtAuth call.respond(HttpStatusCode.BadRequest)
                with(auth) {
                    respond(
                        { tokenService.deleteToken(userId, TokenId(id)) },
                        HttpStatusCode.NoContent,
                    )
                }
            }
        }
    }
}
