package io.tarantini.shelf.catalog.podcast

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("podcasts")
data class PodcastsResource(val parent: RootResource = RootResource) {
    @Resource("libation")
    data class Libation(val parent: PodcastsResource = PodcastsResource()) {
        @Resource("scan") data class Scan(val parent: Libation = Libation())

        @Resource("status") data class Status(val parent: Libation = Libation())
    }

    @Resource("{id}")
    data class Id(val parent: PodcastsResource = PodcastsResource(), val id: String) {
        @Resource("cover") data class Cover(val parent: Id)

        @Resource("rotate-token") data class RotateToken(val parent: Id)

        @Resource("revoke-token") data class RevokeToken(val parent: Id)
    }
}
