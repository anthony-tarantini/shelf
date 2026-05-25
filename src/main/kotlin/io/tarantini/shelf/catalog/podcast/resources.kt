package io.tarantini.shelf.catalog.podcast

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("podcasts")
data class PodcastsResource(val parent: RootResource = RootResource) {
    @Resource("{id}")
    data class Id(val parent: PodcastsResource = PodcastsResource(), val id: String) {
        @Resource("cover") data class Cover(val parent: Id)

        @Resource("episodes/{episodeId}/cover")
        data class EpisodeCover(val parent: Id, val episodeId: String)

        @Resource("rotate-token") data class RotateToken(val parent: Id)

        @Resource("revoke-token") data class RevokeToken(val parent: Id)

        @Resource("credentials") data class Credentials(val parent: Id)

        @Resource("reprobe") data class Reprobe(val parent: Id)

        @Resource("upstream/refresh") data class UpstreamRefresh(val parent: Id)

        @Resource("upstream/episodes") data class UpstreamEpisodes(val parent: Id)

        @Resource("mappings/unmatched") data class MappingsUnmatched(val parent: Id)

        @Resource("mappings/{upstreamGuid}")
        data class Mapping(val parent: Id, val upstreamGuid: String)
    }
}
