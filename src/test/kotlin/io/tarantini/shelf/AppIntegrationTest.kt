package io.tarantini.shelf

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds

class AppIntegrationTest :
    IntegrationSpec({
        "application starts successfully and health check passes" {
            testApp { client ->
                eventually(5.seconds) { client.get("/readiness").status shouldBe HttpStatusCode.OK }
            }
        }

        "metrics endpoint is exposed" {
            testApp { client ->
                val response = client.get("/metrics")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "jvm_threads"
            }
        }
    })
