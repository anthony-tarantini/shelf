package io.tarantini.shelf.integration.core

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.metadata.domain.ExternalMetadata
import kotlinx.serialization.Serializable

@Serializable
data class ExternalAuthorResult(val id: String, val name: String, val imageUrl: String?)

interface ExternalMetadataProvider {
    context(_: RaiseContext)
    suspend fun searchBookMetadataByName(name: String): List<ExternalMetadata>

    context(_: RaiseContext)
    suspend fun searchAuthorsByName(name: String): List<ExternalAuthorResult>
}
