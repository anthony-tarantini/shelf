@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LibraryDomainTest :
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

        "LibraryRoot.fromRaw should create a persisted root" {
            val id = LibraryId.fromRaw(Uuid.random())
            val userId = UserId.fromRaw(Uuid.random())
            val title = "My Library"
            val root = LibraryRoot.fromRaw(id, userId, title)

            root.id shouldBe Identity.Persisted(id)
            root.userId shouldBe userId
            root.title shouldBe title
        }

        "LibraryRoot.new should create an unsaved root" {
            val userId = UserId.fromRaw(Uuid.random())
            val title = "New Library"
            val root = LibraryRoot.new(userId, title)

            root.id shouldBe Identity.Unsaved
            root.userId shouldBe userId
            root.title shouldBe title
        }
    })
