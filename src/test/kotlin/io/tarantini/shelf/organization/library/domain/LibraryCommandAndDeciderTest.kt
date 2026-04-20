@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.id
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.Uuid

class LibraryCommandAndDeciderTest :
    StringSpec({
        "toCreateCommand maps request to typed command" {
            recover({
                val command = LibraryRequest(title = "  Favorites  ").toCreateCommand()
                command.title.value shouldBe "Favorites"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toUpdateCommand maps id and optional title" {
            recover({
                val id = Uuid.random().toString()
                val command = LibraryRequest(title = "  Sci-Fi  ").toUpdateCommand(id)
                command.id.value.toString() shouldBe id
                command.title?.value shouldBe "Sci-Fi"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toCreateCommand rejects blank title" {
            recover({
                LibraryRequest(title = "   ").toCreateCommand()
                fail("Should have failed")
            }) {
                it shouldBe EmptyLibraryTitle
            }
        }

        "decideUpdate keeps existing title when update title is missing" {
            val existing =
                LibraryRoot.fromRaw(
                    id = LibraryId.fromRaw(Uuid.random()),
                    userId = UserId.fromRaw(Uuid.random()),
                    title = "Favorites",
                )
            val command = UpdateLibraryCommand(id = existing.id.id, title = null)
            LibraryMutationDecider.decideUpdate(existing, command).title.value shouldBe "Favorites"
        }
    })
