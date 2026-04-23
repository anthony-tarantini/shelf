package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class AudibleAuthServiceTest : StringSpec({
    "audible auth service should generate login url from stub" {
        val authService = audibleAuthService()
        recover({
            val session = authService.generateLoginUrl()
            session.loginUrl shouldStartWith "https://www.amazon.com"
        }) {
            fail("Should not have failed: $it")
        }
    }

    "audible auth service should finalize auth from stub" {
        val authService = audibleAuthService()
        recover({
            val credentials = authService.finalizeAuth("session-id", "https://callback")
            credentials.activationBytes shouldBe "deadbeef"
        }) {
            fail("Should not have failed: $it")
        }
    }
})
