package io.tarantini.shelf.organization.settings

import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.patch
import io.ktor.server.routing.*
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import kotlinx.serialization.Serializable

@Resource("/settings")
class SettingsResource {
    @Resource("user") class User(val parent: SettingsResource = SettingsResource())
}

@Serializable data class UpdateSettingsRequest(val syncMetadataToFiles: Boolean)

@Serializable data class SettingsResponse(val syncMetadataToFiles: Boolean)

fun Route.settingsRoutes(settingsService: SettingsService, jwtService: JwtService) {
    get<SettingsResource.User> {
        this.jwtAuth(jwtService) { context ->
            respond({
                val settings = settingsService.getUserSettings(context.userId)
                SettingsResponse(syncMetadataToFiles = settings.syncMetadataToFiles)
            })
        }
    }

    patch<SettingsResource.User> {
        this.jwtAuth(jwtService) { context ->
            val request = call.receive<Request<UpdateSettingsRequest>>().data
            respond({
                val updated =
                    settingsService.updateUserSettings(context.userId, request.syncMetadataToFiles)
                SettingsResponse(syncMetadataToFiles = updated.syncMetadataToFiles)
            })
        }
    }
}
