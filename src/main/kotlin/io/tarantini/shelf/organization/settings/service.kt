package io.tarantini.shelf.organization.settings

import io.tarantini.shelf.organization.settings.persistence.SettingsQueries
import io.tarantini.shelf.organization.settings.persistence.User_settings
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserSettingsRoot(val userId: UserId, val syncMetadataToFiles: Boolean)

fun User_settings.toRoot() = UserSettingsRoot(user_id, sync_metadata_to_files)

interface SettingsService {
    suspend fun getUserSettings(userId: UserId): UserSettingsRoot

    suspend fun updateUserSettings(userId: UserId, syncMetadataToFiles: Boolean): UserSettingsRoot
}

fun settingsService(queries: SettingsQueries): SettingsService =
    object : SettingsService {
        override suspend fun getUserSettings(userId: UserId): UserSettingsRoot =
            withContext(Dispatchers.IO) {
                queries.getUserSettings(userId).executeAsOneOrNull()?.toRoot()
                    ?: UserSettingsRoot(userId, false)
            }

        override suspend fun updateUserSettings(
            userId: UserId,
            syncMetadataToFiles: Boolean,
        ): UserSettingsRoot =
            withContext(Dispatchers.IO) {
                queries.updateUserSettings(userId, syncMetadataToFiles)
                UserSettingsRoot(userId, syncMetadataToFiles)
            }
    }
