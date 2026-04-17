@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.catalog.author.domain.*
import io.tarantini.shelf.catalog.series.domain.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CatalogDomainTest :
    StringSpec({
        "AuthorId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val authorId = AuthorId(uuid.toString())
                authorId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "AuthorId should fail for an empty string" {
            recover({
                AuthorId("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyAuthorId
            }
        }

        "AuthorRoot.new should create an unsaved root" {
            recover({
                val root = AuthorRoot.new("John Doe")
                root.id shouldBe Identity.Unsaved
                root.name shouldBe "John Doe"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "AuthorRoot.new should fail for empty name" {
            recover({
                AuthorRoot.new("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyAuthorFirstName
            }
        }

        "SeriesId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val seriesId = SeriesId(uuid.toString())
                seriesId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "SeriesRoot.new should create an unsaved root" {
            val root = SeriesRoot.new("Foundation")
            root.id shouldBe Identity.Unsaved
            root.name shouldBe "Foundation"
        }
    })
