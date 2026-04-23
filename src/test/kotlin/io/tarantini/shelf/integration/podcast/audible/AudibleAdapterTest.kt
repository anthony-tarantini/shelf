package io.tarantini.shelf.integration.podcast.audible

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AudibleAdapterTest : StringSpec({
    "audible adapter should return empty library from stub" {
        val adapter = audibleAdapter()
        recover({
            val library = adapter.fetchLibrary(AudibleCredentials("cookies"))
            library shouldBe emptyList()
        }) {
            fail("Should not have failed: $it")
        }
    }
})
