package io.tarantini.shelf.organization.settings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SettingsCommandTest :
    StringSpec({
        "toCommand maps request to update command" {
            val command = UpdateSettingsRequest(syncMetadataToFiles = true).toCommand()
            command.syncMetadataToFiles shouldBe true
        }

        "decider returns the provided command" {
            val command = UpdateUserSettingsCommand(syncMetadataToFiles = false)
            SettingsMutationDecider.decideUpdate(command) shouldBe command
        }
    })
