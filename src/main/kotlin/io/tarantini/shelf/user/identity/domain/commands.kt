package io.tarantini.shelf.user.identity.domain

data class RegisterUserCommand(
    val email: UserEmail,
    val username: UserName,
    val password: UserPassword,
)

data class SetupUserCommand(
    val email: UserEmail,
    val username: UserName,
    val password: UserPassword,
)

data class LoginUserCommand(val email: UserEmail, val password: UserPassword)

data class UpdateCurrentUserCommand(
    val email: UserEmail? = null,
    val username: UserName? = null,
    val password: UserPassword? = null,
)
