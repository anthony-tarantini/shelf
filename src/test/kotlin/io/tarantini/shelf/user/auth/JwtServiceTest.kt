@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.auth

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Env
import io.tarantini.shelf.user.identity.domain.JwtInvalid
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay

class JwtServiceTest :
    StringSpec({
        val env =
            Env.Auth(
                secret =
                    "test-secret-that-is-long-enough-for-hs512-test-secret-that-is-long-enough-for-hs512",
                issuer = "test-issuer",
                duration = 1.hours,
            )
        val service = jwtService(env)

        "generate and verify a valid JWT token" {
            val userId = UserId.fromRaw(Uuid.random())
            recover({
                val token = service.generateJwtToken(userId)
                val verifiedUserId = service.verifyJwtToken(token)
                verifiedUserId shouldBe userId
            }) {
                fail("Should not have failed: $it")
            }
        }

        "fail for an invalid JWT token" {
            recover({
                service.verifyJwtToken(JwtToken("invalid-token"))
                fail("Should have failed")
            }) {
                it shouldBe JwtInvalid
            }
        }

        "fail for an expired token" {
            val shortEnv = env.copy(duration = 1.milliseconds)
            val shortService = jwtService(shortEnv)
            val userId = UserId.fromRaw(Uuid.random())

            recover({
                val token = shortService.generateJwtToken(userId)
                delay(10.milliseconds) // Wait for token to expire
                shortService.verifyJwtToken(token)
                fail("Should have failed")
            }) {
                it shouldBe JwtInvalid
            }
        }

        "fail when token issuer does not match verifier configuration" {
            val userId = UserId.fromRaw(Uuid.random())
            val mismatchedVerifier = jwtService(env.copy(issuer = "other-issuer"))

            recover({
                val token = service.generateJwtToken(userId)
                mismatchedVerifier.verifyJwtToken(token)
                fail("Should have failed")
            }) {
                it shouldBe JwtInvalid
            }
        }
    })
