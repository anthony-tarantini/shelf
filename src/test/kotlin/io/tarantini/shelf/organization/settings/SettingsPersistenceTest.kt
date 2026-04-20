@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.settings

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.user.identity.createUser
import io.tarantini.shelf.user.identity.domain.*
import kotlin.uuid.ExperimentalUuidApi

class SettingsPersistenceTest :
    IntegrationSpec({
        "getUserSettings should return default settings if none exist" {
            testWithDeps { deps ->
                val userQueries = deps.database.userQueries
                val settingsService =
                    settingsService(deps.database.settingsQueries, deps.observability)

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("settings-test-1@example.com"),
                                username = UserName.fromRaw("settingstest1"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    val settings = settingsService.getUserSettings(user.id.id)
                    settings.userId shouldBe user.id.id
                    settings.syncMetadataToFiles shouldBe false
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "updateUserSettings should persist and retrieve settings" {
            testWithDeps { deps ->
                val userQueries = deps.database.userQueries
                val settingsService =
                    settingsService(deps.database.settingsQueries, deps.observability)

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("settings-test-2@example.com"),
                                username = UserName.fromRaw("settingstest2"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    settingsService.updateUserSettings(
                        user.id.id,
                        UpdateUserSettingsCommand(syncMetadataToFiles = true),
                    )
                    val settings = settingsService.getUserSettings(user.id.id)
                    settings.syncMetadataToFiles shouldBe true

                    settingsService.updateUserSettings(
                        user.id.id,
                        UpdateUserSettingsCommand(syncMetadataToFiles = false),
                    )
                    val updatedSettings = settingsService.getUserSettings(user.id.id)
                    updatedSettings.syncMetadataToFiles shouldBe false
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
