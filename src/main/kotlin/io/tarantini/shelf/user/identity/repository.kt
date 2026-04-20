@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.user.identity.domain.*
import io.tarantini.shelf.user.identity.persistence.UserQueries
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface UserMutationRepository {
    context(_: RaiseContext)
    suspend fun getUserById(userId: UserId): SavedUserAggregate

    context(_: RaiseContext)
    suspend fun getUserByEmail(email: UserEmail): SavedUserAggregate

    context(_: RaiseContext)
    suspend fun getUserByUsername(username: UserName): SavedUserAggregate

    context(_: RaiseContext)
    suspend fun createUser(user: NewUser): SavedUserRoot

    context(_: RaiseContext)
    suspend fun updateUser(user: UpdateUser): SavedUserRoot

    context(_: RaiseContext)
    suspend fun deleteUserById(userId: UserId)
}

fun userMutationRepository(queries: UserQueries): UserMutationRepository =
    SqlDelightUserMutationRepository(queries)

private class SqlDelightUserMutationRepository(private val queries: UserQueries) :
    UserMutationRepository {
    context(_: RaiseContext)
    override suspend fun getUserById(userId: UserId): SavedUserAggregate =
        withContext(Dispatchers.IO) { queries.getUserById(userId) }

    context(_: RaiseContext)
    override suspend fun getUserByEmail(email: UserEmail): SavedUserAggregate =
        withContext(Dispatchers.IO) { queries.getUserByEmail(email) }

    context(_: RaiseContext)
    override suspend fun getUserByUsername(username: UserName): SavedUserAggregate =
        withContext(Dispatchers.IO) { queries.getUserByUsername(username) }

    context(_: RaiseContext)
    override suspend fun createUser(user: NewUser): SavedUserRoot =
        withContext(Dispatchers.IO) { queries.createUser(user) }

    context(_: RaiseContext)
    override suspend fun updateUser(user: UpdateUser): SavedUserRoot =
        withContext(Dispatchers.IO) { queries.updateUser(user) }

    context(_: RaiseContext)
    override suspend fun deleteUserById(userId: UserId) {
        withContext(Dispatchers.IO) { queries.deleteUserById(userId) }
    }
}
