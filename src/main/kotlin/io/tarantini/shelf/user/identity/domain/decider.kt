package io.tarantini.shelf.user.identity.domain

import io.tarantini.shelf.app.id

object UserMutationDecider {
    fun decideRegistration(command: RegisterUserCommand, role: UserRole): NewUser {
        val salt = Salt.generate()
        val hashedPassword = HashedPassword.create(command.password, salt)
        return NewUser(
            email = command.email,
            username = command.username,
            role = role,
            salt = salt,
            hashedPassword = hashedPassword,
        )
    }

    fun decideUpdate(existing: SavedUserAggregate, command: UpdateCurrentUserCommand): UpdateUser {
        val email = command.email ?: existing.user.email
        val username = command.username ?: existing.user.username
        val role = existing.user.role
        val salt = existing.salt
        val hashedPassword =
            command.password?.let { HashedPassword.create(it, salt) } ?: existing.hashedPassword

        return UpdateUser(
            id = existing.user.id.id,
            email = email,
            username = username,
            role = role,
            salt = salt,
            hashedPassword = hashedPassword,
        )
    }
}
