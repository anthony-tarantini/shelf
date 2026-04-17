package io.tarantini.shelf.user.identity

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.adminAuth
import io.tarantini.shelf.user.identity.domain.NewUserRequest
import io.tarantini.shelf.user.identity.domain.UpdateRoleRequest
import io.tarantini.shelf.user.identity.domain.UserId

fun Route.adminRoutes(userService: UserService, jwtService: JwtService) {
    get<AdminResource.Users> {
        adminAuth(jwtService, userService) { respond({ userService.getAllUsers() }) }
    }

    post<AdminResource.Users> {
        adminAuth(jwtService, userService) {
            respond(
                {
                    val req = call.receive<Request<NewUserRequest>>().data
                    userService.register(req).second
                },
                HttpStatusCode.Created,
            )
        }
    }

    put<AdminResource.Users.ById.Role> { resource ->
        adminAuth(jwtService, userService) {
            respond({
                val req = call.receive<Request<UpdateRoleRequest>>().data
                userService.updateRole(UserId(resource.parent.id), req.role)
            })
        }
    }

    delete<AdminResource.Users.ById> { resource ->
        adminAuth(jwtService, userService) {
            respond({ userService.deleteUserById(UserId(resource.id)) })
        }
    }
}
