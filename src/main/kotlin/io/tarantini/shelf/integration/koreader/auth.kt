package io.tarantini.shelf.integration.koreader

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.app.id
import io.tarantini.shelf.integration.koreader.persistence.KoreaderQueries
import io.tarantini.shelf.user.identity.TokenService
import io.tarantini.shelf.user.identity.UserService
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.UserName
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface KoreaderAuthService {
    context(_: RaiseContext)
    suspend fun register(username: String, authKey: String)

    context(_: RaiseContext)
    suspend fun authenticate(username: String, authKey: String): UserId?
}

fun koreaderAuthService(
    koreaderQueries: KoreaderQueries,
    userService: UserService,
    tokenService: TokenService,
) =
    object : KoreaderAuthService {
        context(_: RaiseContext)
        override suspend fun register(username: String, authKey: String) {
            val user = userService.getUserByName(UserName(username))
            ensure(tokenService.validateMd5ForUser(user.id.id, authKey)) { AccessDenied }
            withContext(Dispatchers.IO) { koreaderQueries.upsertKoreaderUser(user.id.id, authKey) }
        }

        context(_: RaiseContext)
        override suspend fun authenticate(username: String, authKey: String): UserId? =
            withContext(Dispatchers.IO) {
                val user =
                    arrow.core.raise.context
                        .either { userService.getUserByName(UserName(username)) }
                        .getOrNull() ?: return@withContext null
                val row =
                    koreaderQueries.selectKoreaderUser(user.id.id).executeAsOneOrNull()
                        ?: return@withContext null
                if (MessageDigest.isEqual(authKey.toByteArray(), row.auth_key.toByteArray())) {
                    user.id.id
                } else {
                    null
                }
            }
    }
