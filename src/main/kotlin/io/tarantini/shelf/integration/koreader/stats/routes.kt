@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.catalog.metadata.domain.EmptyMetadataId
import io.tarantini.shelf.catalog.metadata.domain.InvalidMetadataId
import io.tarantini.shelf.integration.koreader.stats.domain.InvalidStatsDateRange
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatsBookNotFound
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatsDateRange
import io.tarantini.shelf.integration.koreader.stats.domain.toResponse
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

fun Route.koreaderStatsRoutes(service: KoreaderStatsService, jwtService: JwtService) {
    route("/api/koreader/stats") {
        get("/books") {
            jwtAuth(jwtService) { auth ->
                respond({ service.listBooks(auth.userId).map { it.toResponse() } })
            }
        }

        get("/unmatched") {
            jwtAuth(jwtService) { auth ->
                respond({ service.listUnmatchedBooks(auth.userId).map { it.toResponse() } })
            }
        }

        get("/books/{editionId}/sessions") {
            jwtAuth(jwtService) { auth ->
                respond({
                    val editionId = parseEditionId(call.parameters["editionId"])
                    val range = parseDateRange()
                    service.sessionsForEdition(auth.userId, editionId, range).map {
                        it.toResponse()
                    }
                })
            }
        }

        get("/books/{editionId}/totals") {
            jwtAuth(jwtService) { auth ->
                respond({
                    val editionId = parseEditionId(call.parameters["editionId"])
                    val totals =
                        ensureNotNull(service.totalsForEdition(auth.userId, editionId)) {
                            KoreaderStatsBookNotFound
                        }
                    totals.toResponse()
                })
            }
        }

        get("/daily") {
            jwtAuth(jwtService) { auth ->
                respond({
                    val range = ensureNotNull(parseDateRange()) { InvalidStatsDateRange }
                    service.dailyAggregate(auth.userId, range).map { it.toResponse() }
                })
            }
        }
    }
}

context(_: RaiseContext)
private fun parseEditionId(raw: String?): EditionId {
    ensureNotNull(raw) { EmptyMetadataId }
    ensure(raw.isNotEmpty()) { EmptyMetadataId }
    val uuid = ensureNotNull(Uuid.parseOrNull(raw)) { InvalidMetadataId }
    return EditionId.fromRaw(uuid.toJavaUuid())
}

private fun RoutingContext.parseDateRange(): KoreaderStatsDateRange? {
    val from = call.parameters["from"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val to = call.parameters["to"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
    if (from == null || to == null) return null
    return KoreaderStatsDateRange(from, to)
}
