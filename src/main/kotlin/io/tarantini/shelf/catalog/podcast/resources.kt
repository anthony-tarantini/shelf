package io.tarantini.shelf.catalog.podcast

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("podcasts")
data class PodcastsResource(val parent: RootResource = RootResource) {
    @Resource("{id}")
    data class Id(val parent: PodcastsResource = PodcastsResource(), val id: String) {
        @Resource("rotate-token")
        data class RotateToken(val parent: Id)

        @Resource("revoke-token")
        data class RevokeToken(val parent: Id)
    }
}
