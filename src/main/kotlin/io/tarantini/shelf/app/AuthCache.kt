package io.tarantini.shelf.app

import io.ktor.server.auth.UserIdPrincipal
import io.lettuce.core.api.StatefulRedisConnection
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface AuthCache {
    fun get(key: String): UserIdPrincipal?

    fun put(key: String, principal: UserIdPrincipal)
}

class InMemoryAuthCache(private val ttl: Duration = Duration.ofMinutes(5)) : AuthCache {
    private data class CachedCredential(val principal: UserIdPrincipal, val expiresAt: Instant)

    private val cache = ConcurrentHashMap<String, CachedCredential>()

    override fun get(key: String): UserIdPrincipal? {
        val cached = cache[key]
        return if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            cached.principal
        } else {
            cache.remove(key)
            null
        }
    }

    override fun put(key: String, principal: UserIdPrincipal) {
        cache[key] = CachedCredential(principal, Instant.now().plus(ttl))
    }
}

private const val AUTH_CACHE_PREFIX = "auth_cache:"

class ValkeyAuthCache(
    private val connection: StatefulRedisConnection<String, String>,
    private val ttl: Duration = Duration.ofMinutes(5),
) : AuthCache {
    private val commands = connection.sync()

    override fun get(key: String): UserIdPrincipal? {
        val principalId = commands.get("$AUTH_CACHE_PREFIX$key") ?: return null
        return UserIdPrincipal(principalId)
    }

    override fun put(key: String, principal: UserIdPrincipal) {
        commands.setex("$AUTH_CACHE_PREFIX$key", ttl.toSeconds(), principal.name)
    }
}
