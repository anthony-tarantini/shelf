package io.tarantini.shelf.user.identity

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("users")
data class UsersResource(val parent: RootResource = RootResource) {
    @Resource("login") data class Login(val parent: UsersResource = UsersResource())
}

@Resource("setup") data class SetupResource(val parent: RootResource = RootResource)

@Resource("admin")
data class AdminResource(val parent: RootResource = RootResource) {
    @Resource("users")
    data class Users(val parent: AdminResource = AdminResource()) {
        @Resource("{id}")
        data class ById(val parent: Users = Users(), val id: String) {
            @Resource("role") data class Role(val parent: ById)
        }
    }
}
