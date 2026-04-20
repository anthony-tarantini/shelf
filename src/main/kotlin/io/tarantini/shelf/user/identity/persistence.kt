@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.user.identity.domain.EmailAlreadyExists
import io.tarantini.shelf.user.identity.domain.HashedPassword
import io.tarantini.shelf.user.identity.domain.NewUser
import io.tarantini.shelf.user.identity.domain.Salt
import io.tarantini.shelf.user.identity.domain.SavedUserAggregate
import io.tarantini.shelf.user.identity.domain.SavedUserRoot
import io.tarantini.shelf.user.identity.domain.UpdateUser
import io.tarantini.shelf.user.identity.domain.UserAggregate
import io.tarantini.shelf.user.identity.domain.UserEmail
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.UserName
import io.tarantini.shelf.user.identity.domain.UserNotFound
import io.tarantini.shelf.user.identity.domain.UserRole
import io.tarantini.shelf.user.identity.domain.UserRoot
import io.tarantini.shelf.user.identity.domain.UsernameAlreadyExists
import io.tarantini.shelf.user.identity.persistence.UserQueries
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun UserQueries.getUserById(userId: UserId): SavedUserAggregate =
    selectById(userId, fullUserMapper).executeAsOneOrNull() ?: raise(UserNotFound)

context(_: RaiseContext)
fun UserQueries.getUserByUsername(username: UserName): SavedUserAggregate =
    selectByUsername(username, fullUserMapper).executeAsOneOrNull() ?: raise(UserNotFound)

context(_: RaiseContext)
fun UserQueries.getUserByEmail(email: UserEmail): SavedUserAggregate =
    selectByEmail(email, fullUserMapper).executeAsOneOrNull() ?: raise(UserNotFound)

context(_: RaiseContext)
fun UserQueries.countUsers(): Long = countAll().executeAsOne()

context(_: RaiseContext)
fun UserQueries.getAllUsers(): List<SavedUserRoot> = selectAllUsers(userRootMapper).executeAsList()

context(_: RaiseContext)
fun UserQueries.createUser(user: NewUser): SavedUserRoot = transactionWithResult {
    ensure(selectByEmail(user.email, fullUserMapper).executeAsOneOrNull() == null) {
        EmailAlreadyExists
    }
    ensure(selectByUsername(user.username, fullUserMapper).executeAsOneOrNull() == null) {
        UsernameAlreadyExists
    }
    val userId =
        insertAndGetId(user.email, user.username, user.role, user.salt, user.hashedPassword)
            .executeAsOneOrNull() ?: raise(UserNotFound)
    selectById(userId, userRootMapper).executeAsOneOrNull() ?: raise(UserNotFound)
}

context(_: RaiseContext)
fun UserQueries.updateUser(user: UpdateUser): SavedUserRoot = transactionWithResult {
    val existingByEmail = selectByEmail(user.email, fullUserMapper).executeAsOneOrNull()
    ensure(existingByEmail == null || existingByEmail.user.id.id == user.id) { EmailAlreadyExists }

    val existingByUsername = selectByUsername(user.username, fullUserMapper).executeAsOneOrNull()
    ensure(existingByUsername == null || existingByUsername.user.id.id == user.id) {
        UsernameAlreadyExists
    }

    update(user.email, user.username, user.role, user.hashedPassword, user.id)
    selectById(user.id, userRootMapper).executeAsOneOrNull() ?: raise(UserNotFound)
}

context(_: RaiseContext)
fun UserQueries.deleteUserById(userId: UserId) {
    ensure(deleteById(userId).value == 1L) { UserNotFound }
}

context(_: RaiseContext)
fun UserQueries.deleteUserByUsername(username: UserName) {
    ensure(deleteByUsername(username).value == 1L) { UserNotFound }
}

context(_: RaiseContext)
fun UserQueries.deleteUserByEmail(email: UserEmail) {
    ensure(deleteByEmail(email).value == 1L) { UserNotFound }
}

private val fullUserMapper =
    {
        id: UserId,
        email: UserEmail,
        username: UserName,
        role: UserRole,
        salt: Salt,
        hash: HashedPassword ->
        UserAggregate(UserRoot.fromRaw(id, email, username, role), role, salt, hash)
    }

private val userRootMapper =
    { id: UserId, email: UserEmail, username: UserName, role: UserRole, _: Salt, _: HashedPassword
        ->
        UserRoot.fromRaw(id, email, username, role)
    }
