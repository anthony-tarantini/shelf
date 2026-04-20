package io.tarantini.shelf.organization.library

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("libraries")
data class LibraryResource(val parent: RootResource = RootResource) {
    @Resource("{id}")
    data class Id(val parent: LibraryResource = LibraryResource(), val id: String) {
        @Resource("books")
        data class Books(val parent: Id) {
            val id = parent.id
        }

        @Resource("details")
        data class Details(val parent: Id) {
            val id = parent.id
        }
    }
}
