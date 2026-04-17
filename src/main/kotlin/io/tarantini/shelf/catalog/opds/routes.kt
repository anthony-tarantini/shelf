@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.opds

import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import io.ktor.http.ContentType
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.InvalidAuthorId
import io.tarantini.shelf.catalog.series.domain.InvalidSeriesId
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.user.auth.sharedCatalogFeedAuth
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.serializer

private const val MAX_PAGE_SIZE = 100

fun Route.opdsRoutes(opdsService: OpdsService) {
    sharedCatalogFeedAuth {
        get<OpdsResource.Catalog> { respondOpds { opdsService.getRootCatalog() } }

        get<OpdsResource.Books> { resource ->
            val (page, size) = opdsPageParams(resource.page, resource.size)
            respondOpds { opdsService.getAllBooksFeed(page, size) }
        }

        get<OpdsResource.Authors> { respondOpds { opdsService.getAuthorsFeed() } }

        get<OpdsResource.Authors.Id> { resource ->
            val (page, size) = opdsPageParams(resource.page, resource.size)
            respondOpds {
                val id = Uuid.parseOrNull(resource.id) ?: raise(InvalidAuthorId)
                opdsService.getAuthorBooksFeed(AuthorId.fromRaw(id), page, size)
            }
        }

        get<OpdsResource.Series> { respondOpds { opdsService.getSeriesFeed() } }

        get<OpdsResource.Series.Id> { resource ->
            val (page, size) = opdsPageParams(resource.page, resource.size)
            respondOpds {
                val id = Uuid.parseOrNull(resource.id) ?: raise(InvalidSeriesId)
                opdsService.getSeriesBooksFeed(SeriesId.fromRaw(id), page, size)
            }
        }

        get<OpdsResource.Search.Description> {
            respondOpds { opdsService.getOpenSearchDescription() }
        }

        get<OpdsResource.Search> { resource ->
            val query = resource.q ?: resource.query ?: ""
            val (page, size) = opdsPageParams(resource.page, resource.size)
            respondOpds { opdsService.searchBooksFeed(query, page, size) }
        }
    }
}

fun opdsPageParams(pageParam: Int?, sizeParam: Int?): Pair<Int, Int> {
    val page = (pageParam ?: 0).coerceAtLeast(0)
    val size = (sizeParam ?: 20).coerceIn(1, MAX_PAGE_SIZE)
    return page to size
}

suspend inline fun <reified T : Any> RoutingContext.respondOpds(
    crossinline block:
        suspend context(RaiseContext)
        () -> T
) {
    either { block() }
        .fold(
            { err: AppError -> respond(err) },
            { result ->
                val contentType =
                    when (result) {
                        is OpdsFeed ->
                            result.link.find { it.rel == OpdsRel.SELF }?.type ?: OpdsMimeType.ATOM
                        is OpenSearchDescription -> OpdsMimeType.OPENSEARCH
                        else -> null
                    }
                if (contentType != null) {
                    val xml =
                        nl.adaptivity.xmlutil.serialization.XML {
                            xmlDeclMode = nl.adaptivity.xmlutil.XmlDeclMode.Charset
                        }
                    val serialized = xml.encodeToString(serializer<T>(), result)
                    call.respondText(serialized, ContentType.parse(contentType))
                } else {
                    call.respond(result)
                }
            },
        )
}
