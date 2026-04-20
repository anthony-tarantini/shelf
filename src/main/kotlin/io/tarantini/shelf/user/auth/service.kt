@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.auth

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nefilim.kjwt.sign
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Env
import io.tarantini.shelf.user.identity.domain.JwtGeneration
import io.tarantini.shelf.user.identity.domain.JwtInvalid
import io.tarantini.shelf.user.identity.domain.UserId
import java.time.Clock
import java.time.Instant
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi

interface JwtService {
    context(_: RaiseContext)
    suspend fun generateJwtToken(userId: UserId): JwtToken

    context(_: RaiseContext)
    suspend fun verifyJwtToken(token: JwtToken): UserId
}

fun jwtService(env: Env.Auth) =
    object : JwtService {
        context(_: RaiseContext)
        override suspend fun generateJwtToken(userId: UserId) =
            JWT.hs512 {
                    val now = Instant.now(Clock.systemUTC())
                    issuedAt(now)
                    expiresAt(now + env.duration.toJavaDuration())
                    issuer(env.issuer)
                    claim("id", userId.value.toString())
                }
                .sign(env.secret)
                .fold({ raise(JwtGeneration) }, { JwtToken(it.rendered) })

        context(_: RaiseContext)
        override suspend fun verifyJwtToken(token: JwtToken): UserId {
            val jwt =
                ensureNotNull(JWT.decodeT(token.value, JWSHMAC512Algorithm).getOrNull()) {
                    JwtInvalid
                }
            val issuer = ensureNotNull(jwt.issuer().getOrNull()) { JwtInvalid }
            ensure(issuer == env.issuer) { JwtInvalid }
            val userId = ensureNotNull(jwt.claimValue("id").getOrNull()) { JwtInvalid }
            val expiresAt = ensureNotNull(jwt.expiresAt().getOrNull()) { JwtInvalid }
            ensure(expiresAt.isAfter(Instant.now(Clock.systemUTC()))) { JwtInvalid }
            return UserId(userId)
        }
    }
