@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.app

import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.tarantini.shelf.catalog.author.authorRoutes
import io.tarantini.shelf.catalog.book.bookRoutes
import io.tarantini.shelf.catalog.metadata.metadataRoutes
import io.tarantini.shelf.catalog.opds.opdsRoutes
import io.tarantini.shelf.catalog.podcast.podcastRoutes
import io.tarantini.shelf.catalog.podcast.rss.podcastRssRoutes
import io.tarantini.shelf.catalog.search.searchRoutes
import io.tarantini.shelf.catalog.series.seriesRoutes
import io.tarantini.shelf.integration.koreader.koreaderRoutes
import io.tarantini.shelf.integration.koreader.stats.koreaderStatsRoutes
import io.tarantini.shelf.observability.appOwnsServerTraces
import io.tarantini.shelf.observability.observabilityRoutes
import io.tarantini.shelf.organization.library.libraryRoutes
import io.tarantini.shelf.organization.settings.settingsRoutes
import io.tarantini.shelf.processing.import.importRoutes
import io.tarantini.shelf.processing.import.stagedRoutes
import io.tarantini.shelf.user.identity.adminRoutes
import io.tarantini.shelf.user.identity.tokenRoutes
import io.tarantini.shelf.user.identity.userRoutes
import kotlinx.serialization.ExperimentalSerializationApi

@Resource("/api") data object RootResource

fun Application.routes(deps: Dependencies) = routing {
    if (deps.observability.config.appOwnsServerTraces()) {
        install(KtorServerTelemetry) { setOpenTelemetry(deps.observability.openTelemetry) }
    }
    observabilityRoutes(deps.observability)
    userRoutes(deps.userService, deps.jwtService)
    adminRoutes(deps.userService, deps.jwtService)
    tokenRoutes(deps.tokenService, deps.jwtService)
    authorRoutes(
        deps.authorService,
        deps.bookService,
        deps.activityService,
        deps.jwtService,
        deps.storageService,
        deps.externalMetadataProvider,
    )
    bookRoutes(
        deps.bookService,
        deps.bookService,
        deps.bookService,
        deps.bookService,
        deps.bookService,
        deps.bookService,
        deps.storageService,
        deps.authorService,
        deps.seriesService,
        deps.activityService,
        deps.jwtService,
        deps.userService,
        deps.observability,
    )
    metadataRoutes(deps.metadataService, deps.jwtService)
    with(deps.sqlDriver) { searchRoutes(deps.searchService, deps.activityService, deps.jwtService) }
    podcastRoutes(deps.podcastService, deps.jwtService, deps.storageService)
    podcastRssRoutes(deps.podcastRssService, deps.storageService)
    seriesRoutes(
        deps.seriesService,
        deps.bookService,
        deps.authorService,
        deps.activityService,
        deps.storageService,
        deps.jwtService,
    )
    with(deps.sqlDriver) { opdsRoutes(deps.opdsService) }
    libraryRoutes(deps.jwtService, deps.libraryService, deps.bookService)
    importRoutes(deps.importService, deps.podcastLibationService, deps.jwtService, deps.userService)
    with(deps.database) {
        stagedRoutes(deps.jwtService, deps.stagedBookService, deps.storageService)
    }
    settingsRoutes(deps.settingsService, deps.jwtService)
    koreaderRoutes(deps)
    koreaderStatsRoutes(deps.koreaderStatsService, deps.jwtService)
}
