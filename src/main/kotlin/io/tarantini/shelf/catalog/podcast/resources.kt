package io.tarantini.shelf.catalog.podcast

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("podcasts")
data class PodcastsResource(val parent: RootResource = RootResource) {
    @Resource("audible")
    data class Audible(val parent: PodcastsResource = PodcastsResource()) {
        @Resource("connect")
        data class Connect(val parent: Audible = Audible())

        @Resource("finalize")
        data class Finalize(val parent: Audible = Audible())

        @Resource("library")
        data class Library(val parent: Audible = Audible())

        @Resource("import")
        data class Import(val parent: Audible = Audible())
    }

    @Resource("{id}")
    data class Id(val parent: PodcastsResource = PodcastsResource(), val id: String) {
        @Resource("rotate-token")
        data class RotateToken(val parent: Id)

        @Resource("revoke-token")
        data class RevokeToken(val parent: Id)
    }
}
