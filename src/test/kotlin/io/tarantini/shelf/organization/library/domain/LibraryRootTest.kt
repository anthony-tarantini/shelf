@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LibraryRootTest :
    StringSpec({
        "LibraryRoot.fromRaw should create a Persisted LibraryRoot" {
            val libraryId = LibraryId.fromRaw(Uuid.random())
            val userId = UserId.fromRaw(Uuid.random())
            val title = "Test Library"

            val libraryRoot = LibraryRoot.fromRaw(libraryId, userId, title)

            libraryRoot.id shouldBe Identity.Persisted(libraryId)
            libraryRoot.userId shouldBe userId
            libraryRoot.title shouldBe title
        }

        "LibraryRoot.new should create an Unsaved LibraryRoot" {
            val userId = UserId.fromRaw(Uuid.random())
            val title = "New Library"

            val libraryRoot = LibraryRoot.new(userId, title)

            libraryRoot.id shouldBe Identity.Unsaved
            libraryRoot.userId shouldBe userId
            libraryRoot.title shouldBe title
        }
    })
