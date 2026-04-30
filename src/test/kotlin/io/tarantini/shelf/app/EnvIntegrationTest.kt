package io.tarantini.shelf.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage

class EnvIntegrationTest :
    StringSpec({
        "integration env allows insecure default secret in development" {
            val env =
                Env.Integration.fromEnv { key ->
                    when (key) {
                        "OBSERVABILITY_ENVIRONMENT" -> "development"
                        else -> null
                    }
                }

            env.encryptionSecret shouldBe "insecure-local-default-change-me"
        }

        "integration env requires ENCRYPTION_SECRET outside development" {
            val error =
                shouldThrow<IllegalStateException> {
                    Env.Integration.fromEnv { key ->
                        when (key) {
                            "OBSERVABILITY_ENVIRONMENT" -> "production"
                            else -> null
                        }
                    }
                }

            error.message shouldNotBe null
            error shouldHaveMessage
                "ENCRYPTION_SECRET is required when OBSERVABILITY_ENVIRONMENT is 'production'."
        }

        "integration env uses configured secret in production" {
            val env =
                Env.Integration.fromEnv { key ->
                    when (key) {
                        "OBSERVABILITY_ENVIRONMENT" -> "production"
                        "ENCRYPTION_SECRET" -> "super-secret"
                        else -> null
                    }
                }

            env.encryptionSecret shouldBe "super-secret"
        }
    })
