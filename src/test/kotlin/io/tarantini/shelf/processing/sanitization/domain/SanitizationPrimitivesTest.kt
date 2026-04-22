@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SanitizationPrimitivesTest :
    StringSpec({
        "SanitizationJobId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val id = SanitizationJobId(uuid.toString())
                id.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "SanitizationJobId should fail for invalid UUID string" {
            recover({
                SanitizationJobId("invalid")
                fail("Should have failed")
            }) {
                it shouldBe InvalidSanitizationJobId
            }
        }

        "AudioTimestamp should reject negative values" {
            recover({
                AudioTimestamp(-1.0)
                fail("Should have failed")
            }) {
                it shouldBe InvalidAudioTimestamp
            }
        }

        "AdSegment should compute duration when valid" {
            recover({
                val segment =
                    AdSegment(
                        start = AudioTimestamp(10.0),
                        end = AudioTimestamp(25.5),
                        confidence = 0.9,
                    )
                segment.durationSeconds shouldBe 15.5
            }) {
                fail("Should not have failed: $it")
            }
        }

        "AdSegment should fail when end is not after start" {
            recover({
                AdSegment(
                    start = AudioTimestamp(10.0),
                    end = AudioTimestamp(10.0),
                    confidence = 0.9,
                )
                fail("Should have failed")
            }) {
                it shouldBe InvalidAdSegment
            }
        }

        "AdSegment should fail when confidence is outside 0..1" {
            recover({
                AdSegment(
                    start = AudioTimestamp(10.0),
                    end = AudioTimestamp(20.0),
                    confidence = 1.1,
                )
                fail("Should have failed")
            }) {
                it shouldBe InvalidAdSegment
            }
        }
    })
