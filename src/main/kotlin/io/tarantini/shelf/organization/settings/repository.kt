package io.tarantini.shelf.organization.settings

import io.tarantini.shelf.organization.settings.persistence.SettingsQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SettingsMutationRepository {
    suspend fun updateUserSettings(userId: UserId, syncMetadataToFiles: Boolean)
}

fun settingsMutationRepository(queries: SettingsQueries): SettingsMutationRepository =
    SqlDelightSettingsMutationRepository(queries)

private class SqlDelightSettingsMutationRepository(private val queries: SettingsQueries) :
    SettingsMutationRepository {
    override suspend fun updateUserSettings(userId: UserId, syncMetadataToFiles: Boolean) {
        withContext(Dispatchers.IO) { queries.updateUserSettings(userId, syncMetadataToFiles) }
    }
}
