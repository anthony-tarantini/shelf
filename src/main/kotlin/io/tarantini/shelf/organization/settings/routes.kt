package io.tarantini.shelf.organization.settings

import io.ktor.resources.Resource
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.RootResource
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import kotlinx.serialization.Serializable

@Resource("settings")
class SettingsResource(val parent: RootResource = RootResource) {
    @Resource("user") class User(val parent: SettingsResource = SettingsResource())
}

@Serializable data class UpdateSettingsRequest(val syncMetadataToFiles: Boolean)

@Serializable data class SettingsResponse(val syncMetadataToFiles: Boolean)

fun Route.settingsRoutes(settingsService: SettingsService, jwtService: JwtService) {
    get<SettingsResource.User> {
        jwtAuth(jwtService) { context ->
            respond({
                val settings = settingsService.getUserSettings(context.userId)
                SettingsResponse(syncMetadataToFiles = settings.syncMetadataToFiles)
            })
        }
    }

    patch<SettingsResource.User> {
        jwtAuth(jwtService) { context ->
            val request = call.receive<Request<UpdateSettingsRequest>>().data
            respond({
                val updated =
                    settingsService.updateUserSettings(context.userId, request.toCommand())
                SettingsResponse(syncMetadataToFiles = updated.syncMetadataToFiles)
            })
        }
    }
}
