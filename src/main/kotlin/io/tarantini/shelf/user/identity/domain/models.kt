package io.tarantini.shelf.user.identity.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN,
    USER,
}

@Serializable
data class UserAggregate<S : PersistenceState>(
    val user: UserRoot<S>,
    val role: UserRole,
    val salt: Salt,
    val hashedPassword: HashedPassword,
)

typealias SavedUserAggregate = UserAggregate<PersistenceState.Persisted>

typealias NewUserAggregate = UserAggregate<PersistenceState.Unsaved>

@Serializable
data class UserRoot<S : PersistenceState>(
    val id: Identity<S, UserId>,
    val email: UserEmail,
    val username: UserName,
    val role: UserRole,
) {
    companion object {
        fun fromRaw(id: UserId, email: UserEmail, username: UserName, role: UserRole) =
            UserRoot<PersistenceState.Persisted>(Identity.Persisted(id), email, username, role)

        fun new(email: UserEmail, username: UserName, role: UserRole = UserRole.USER) =
            UserRoot<PersistenceState.Unsaved>(Identity.Unsaved, email, username, role)
    }
}

typealias SavedUserRoot = UserRoot<PersistenceState.Persisted>

typealias NewUserRoot = UserRoot<PersistenceState.Unsaved>

@Serializable
data class NewUser(
    val email: UserEmail,
    val username: UserName,
    val role: UserRole,
    val salt: Salt,
    val hashedPassword: HashedPassword,
)

@Serializable
data class UpdateUser(
    val id: UserId,
    val email: UserEmail,
    val username: UserName,
    val role: UserRole,
    val salt: Salt,
    val hashedPassword: HashedPassword,
)
