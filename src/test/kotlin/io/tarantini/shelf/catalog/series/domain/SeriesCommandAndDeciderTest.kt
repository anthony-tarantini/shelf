@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.id
import kotlin.uuid.Uuid

class SeriesCommandAndDeciderTest :
    StringSpec({
        "toCreateCommand maps request to typed command" {
            recover({
                val command = SeriesRequest(title = "  Foundation  ").toCreateCommand()
                command.title.value shouldBe "Foundation"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toUpdateCommand maps id and optional title" {
            recover({
                val id = Uuid.random().toString()
                val command = SeriesRequest(title = "  Dune  ").toUpdateCommand(id)
                command.id.value.toString() shouldBe id
                command.title?.value shouldBe "Dune"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toCreateCommand rejects blank title" {
            recover({
                SeriesRequest(title = "   ").toCreateCommand()
                fail("Should have failed")
            }) {
                it shouldBe EmptySeriesTitle
            }
        }

        "decideUpdate keeps existing title when update title is missing" {
            val existing = SeriesRoot.fromRaw(SeriesId.fromRaw(Uuid.random()), "Dune")
            val command = UpdateSeriesCommand(id = existing.id.id, title = null)
            SeriesMutationDecider.decideUpdate(existing, command).title.value shouldBe "Dune"
        }
    })
