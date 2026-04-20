package io.tarantini.shelf.catalog.author

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("authors")
data class AuthorsResource(val parent: RootResource = RootResource) {
    @Resource("page")
    data class Page(
        val parent: AuthorsResource = AuthorsResource(),
        val page: Int = 0,
        val size: Int = 20,
        val sortBy: String = "name",
        val sortDir: String = "ASC",
    )

    @Resource("")
    data class Search(val parent: AuthorsResource = AuthorsResource(), val query: String)

    @Resource("hardcover")
    data class Hardcover(val parent: AuthorsResource = AuthorsResource()) {
        @Resource("search")
        data class Search(val parent: Hardcover = Hardcover(), val query: String)
    }

    @Resource("{id}")
    data class Id(val parent: AuthorsResource = AuthorsResource(), val id: String) {
        @Resource("books")
        data class Books(val parent: Id) {
            val id = parent.id
        }

        @Resource("details")
        data class Details(val parent: Id) {
            val id = parent.id
        }

        @Resource("image")
        data class Image(val parent: Id) {
            val id = parent.id

            @Resource("url")
            data class Url(val parent: Image) {
                val id = parent.id
            }
        }
    }
}
