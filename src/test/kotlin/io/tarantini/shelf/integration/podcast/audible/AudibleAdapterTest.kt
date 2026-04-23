package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.podcast.domain.AudibleAuthFailed

class AudibleAdapterTest : StringSpec({
    "audible adapter should fail on missing token" {
        val adapter = audibleAdapter()
        recover({
            adapter.fetchLibrary(AudibleCredentials("invalid-cookies"))
            fail("Should have failed")
        }) {
            it shouldBe AudibleAuthFailed
        }
    }
})
