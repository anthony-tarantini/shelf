@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class BookDomainTest :
    StringSpec({
        "BookId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val bookId = BookId(uuid.toString())
                bookId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "BookId should fail for an empty string" {
            recover({
                BookId("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyBookId
            }
        }

        "BookRoot.new should create an unsaved root" {
            val root = BookRoot.new("The Caves of Steel")
            root.id shouldBe Identity.Unsaved
            root.title shouldBe "The Caves of Steel"
        }

        "BookRoot.fromRaw should create a persisted root" {
            val id = BookId.fromRaw(Uuid.random())
            val root = BookRoot.fromRaw(id, "Foundation")
            root.id shouldBe Identity.Persisted(id)
            root.title shouldBe "Foundation"
        }

        "deriveCoverPath should work correctly" {
            BookRoot.deriveCoverPath("books/asimov/foundation.epub")?.value shouldBe
                "books/asimov/cover.jpg"
            BookRoot.deriveCoverPath("no-slash.epub") shouldBe null
        }
    })
