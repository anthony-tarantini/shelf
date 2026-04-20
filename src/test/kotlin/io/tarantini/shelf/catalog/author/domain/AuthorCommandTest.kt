@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

class AuthorCommandTest :
    StringSpec({
        "toUpdateCommand maps request to typed command" {
            recover({
                val id = Uuid.random().toString()
                val command = AuthorRequest(id = id, name = "  Isaac Asimov  ").toUpdateCommand()
                command.id.value.toString() shouldBe id
                command.name.value shouldBe "Isaac Asimov"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toUpdateCommand rejects blank name" {
            recover({
                AuthorRequest(id = Uuid.random().toString(), name = "   ").toUpdateCommand()
                fail("Should have failed")
            }) {
                it shouldBe EmptyAuthorFirstName
            }
        }

        "toUpdateCommand rejects invalid id" {
            recover({
                AuthorRequest(id = "not-a-uuid", name = "Valid Name").toUpdateCommand()
                fail("Should have failed")
            }) {
                it shouldBe InvalidAuthorId
            }
        }
    })
