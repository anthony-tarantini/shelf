package io.tarantini.shelf.organization.settings

import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.organization.settings.persistence.SettingsQueries
import io.tarantini.shelf.organization.settings.persistence.User_settings
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserSettingsRoot(val userId: UserId, val syncMetadataToFiles: Boolean)

fun User_settings.toRoot() = UserSettingsRoot(user_id, sync_metadata_to_files)

interface SettingsService {
    suspend fun getUserSettings(userId: UserId): UserSettingsRoot

    suspend fun updateUserSettings(
        userId: UserId,
        command: UpdateUserSettingsCommand,
    ): UserSettingsRoot
}

fun settingsService(
    queries: SettingsQueries,
    observability: Observability,
    mutationRepository: SettingsMutationRepository = settingsMutationRepository(queries),
): SettingsService = UserSettingsService(queries, observability, mutationRepository)

private class UserSettingsService(
    private val queries: SettingsQueries,
    private val observability: Observability,
    private val mutationRepository: SettingsMutationRepository,
) : SettingsService {
    override suspend fun getUserSettings(userId: UserId): UserSettingsRoot =
        withContext(Dispatchers.IO) {
            queries.getUserSettings(userId).executeAsOneOrNull()?.toRoot()
                ?: UserSettingsRoot(userId, false)
        }

    override suspend fun updateUserSettings(
        userId: UserId,
        command: UpdateUserSettingsCommand,
    ): UserSettingsRoot =
        withContext(Dispatchers.IO) {
            val decision = SettingsMutationDecider.decideUpdate(command)
            mutationRepository.updateUserSettings(userId, decision.syncMetadataToFiles)
            UserSettingsRoot(userId, decision.syncMetadataToFiles)
        }
}
