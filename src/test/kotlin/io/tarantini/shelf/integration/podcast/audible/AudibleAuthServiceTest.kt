package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed

class AudibleAuthServiceTest : StringSpec({
    "audible auth service should generate login url with correct params" {
        val authService = audibleAuthService()
        recover({
            val session = authService.generateLoginUrl()
            session.loginUrl shouldStartWith "https://www.amazon.com/ap/signin"
            session.loginUrl shouldContain "openid.oa2.client_id=device%3Aamzn1.application-oa-client.8df46f88127042a99d63c5d63f9157ec"
            session.loginUrl shouldContain "openid.oa2.response_type=code"
        }) {
            fail("Should not have failed: $it")
        }
    }

    "audible auth service should attempt finalize auth and fail on network" {
        val authService = audibleAuthService()
        recover({
            val callbackUrl = "https://www.amazon.com/ap/maplanding?openid.oa2.authorization_code=TEST_CODE&other=param"
            authService.finalizeAuth("session-id", callbackUrl)
            fail("Should have failed on network call")
        }) {
            it shouldBe AudibleAuthFailed
        }
    }
})
