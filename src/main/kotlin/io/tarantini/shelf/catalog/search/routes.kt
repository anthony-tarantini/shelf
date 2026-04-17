package io.tarantini.shelf.catalog.search

import io.ktor.server.resources.*
import io.ktor.server.routing.*
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogRead

fun Route.searchRoutes(
    searchService: SearchService,
    activityService: ActivityService,
    jwtService: JwtService,
) {
    get<SearchResource> { resource ->
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({
                    val result = searchService.search(resource.q)
                    result.copy(books = activityService.enrichBookSummaries(result.books))
                })
            }
        }
    }
}
