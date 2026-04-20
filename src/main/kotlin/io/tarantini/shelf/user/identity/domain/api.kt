package io.tarantini.shelf.user.identity.domain

import kotlinx.serialization.Serializable

@Serializable
data class UserRequest(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
)

@Serializable data class UserWithToken(val user: SavedUserRoot, val token: UserToken)

@Serializable
data class NewUserRequest(val email: String?, val username: String?, val password: String?)

@Serializable data class LoginUserRequest(val email: String?, val password: String?)

@Serializable
data class UpdateUserRequest(
    val id: UserId? = null,
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable data class SetupStatusResponse(val complete: Boolean)

@Serializable data class UpdateRoleRequest(val role: UserRole)

@JvmInline @Serializable value class UserToken(val value: String)

context(_: io.tarantini.shelf.RaiseContext)
fun NewUserRequest.toRegisterCommand(): RegisterUserCommand =
    RegisterUserCommand(
        email = UserEmail(email),
        username = UserName(username),
        password = UserPassword(password),
    )

context(_: io.tarantini.shelf.RaiseContext)
fun NewUserRequest.toSetupCommand(): SetupUserCommand =
    SetupUserCommand(
        email = UserEmail(email),
        username = UserName(username),
        password = UserPassword(password),
    )

context(_: io.tarantini.shelf.RaiseContext)
fun LoginUserRequest.toCommand(): LoginUserCommand =
    LoginUserCommand(email = UserEmail(email), password = UserPassword(password))

context(_: io.tarantini.shelf.RaiseContext)
fun UpdateUserRequest.toCommand(): UpdateCurrentUserCommand =
    UpdateCurrentUserCommand(
        email = email?.let { UserEmail(it) },
        username = username?.let { UserName(it) },
        password = password?.let { UserPassword(it) },
    )
