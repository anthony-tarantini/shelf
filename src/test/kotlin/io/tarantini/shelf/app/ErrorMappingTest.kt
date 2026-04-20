package io.tarantini.shelf.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.tarantini.shelf.catalog.series.domain.SeriesFuzzySearchDisabled

class ErrorMappingTest :
    StringSpec({
        "series fuzzy disabled maps to not implemented" {
            val (status, message) = SeriesFuzzySearchDisabled.toHttpResponse()
            status shouldBe HttpStatusCode.NotImplemented
            message shouldBe "Fuzzy series search is temporarily disabled"
        }
    })
