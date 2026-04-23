package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class AudibleAuthServiceTest : StringSpec({
    "audible auth service should generate login url with correct params" {
        val authService = audibleAuthService()
        recover({
            val session = authService.generateLoginUrl()
            session.loginUrl shouldStartWith "https://www.amazon.com/ap/signin"
            session.loginUrl shouldContain "openid.oa2.client_id=amzn1.application-oa-client.0a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p"
            session.loginUrl shouldContain "openid.oa2.response_type=code"
        }) {
            fail("Should not have failed: $it")
        }
    }

    "audible auth service should finalize auth by extracting code" {
        val authService = audibleAuthService()
        recover({
            val callbackUrl = "https://www.amazon.com/ap/maplanding?openid.oa2.authorization_code=TEST_CODE&other=param"
            val credentials = authService.finalizeAuth("session-id", callbackUrl)
            credentials.cookies shouldContain "captured-TEST_CODE"
        }) {
            fail("Should not have failed: $it")
        }
    }
})
