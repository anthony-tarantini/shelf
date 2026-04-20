@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.catalog.metadata

import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EmptySearchQuery
import io.tarantini.shelf.catalog.metadata.domain.MetadataNotFound
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.metadataRoutes(metadataService: MetadataService, jwtService: JwtService) {
    get<BookMetadataResource> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                metadataService.getMetadataForBook(BookId(resource.id)) ?: raise(MetadataNotFound)
            })
        }
    }

    get<BookMetadataResource.Chapters> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                val metadata = metadataService.getMetadataForBook(BookId(resource.id))
                metadata?.editions?.flatMap { it.chapters } ?: emptyList()
            })
        }
    }

    get<MetadataResource.External.Search> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                ensure(!resource.query.isNullOrEmpty()) { EmptySearchQuery }
                metadataService.getExternalMetadata(resource.query)
            })
        }
    }
}
