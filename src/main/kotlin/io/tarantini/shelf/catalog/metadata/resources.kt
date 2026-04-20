package io.tarantini.shelf.catalog.metadata

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource
import io.tarantini.shelf.catalog.book.BooksResource

@Resource("metadata")
data class BookMetadataResource(val parent: BooksResource.Id) {
    val id = parent.id

    @Resource("chapters")
    data class Chapters(val parent: BookMetadataResource) {
        val id = parent.id
    }
}

@Resource("metadata")
data class MetadataResource(val parent: RootResource = RootResource) {
    @Resource("external")
    data class External(val parent: MetadataResource) {
        @Resource("search") data class Search(val parent: External, val query: String? = null)
    }
}
