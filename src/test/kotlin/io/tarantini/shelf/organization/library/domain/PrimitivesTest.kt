@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PrimitivesTest :
    StringSpec({
        "LibraryId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val libraryId = LibraryId(uuid.toString())
                libraryId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "LibraryId should fail for an empty string" {
            recover({
                LibraryId("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyLibraryId
            }
        }

        "LibraryId should fail for an invalid UUID string" {
            recover({
                LibraryId("not-a-uuid")
                fail("Should have failed")
            }) {
                it shouldBe InvalidLibraryId
            }
        }

        "LibraryId should be valid for a correct Uuid" {
            val uuid = Uuid.random()
            recover({
                val libraryId = LibraryId(uuid).bind()
                libraryId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "LibraryId should fail for a null Uuid" {
            recover({
                LibraryId(null as Uuid?).bind()
                fail("Should have failed")
            }) {
                it shouldBe EmptyLibraryId
            }
        }
    })
