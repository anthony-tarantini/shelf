package io.tarantini.shelf.organization.settings

data class UpdateUserSettingsCommand(val syncMetadataToFiles: Boolean)

object SettingsMutationDecider {
    fun decideUpdate(command: UpdateUserSettingsCommand): UpdateUserSettingsCommand = command
}

fun UpdateSettingsRequest.toCommand(): UpdateUserSettingsCommand =
    UpdateUserSettingsCommand(syncMetadataToFiles = syncMetadataToFiles)
