package io.tarantini.shelf.catalog.series

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("series")
data class SeriesResource(val parent: RootResource = RootResource) {
    @Resource("page")
    data class Page(
        val parent: SeriesResource = SeriesResource(),
        val page: Int = 0,
        val size: Int = 20,
        val sortBy: String = "title",
        val sortDir: String = "ASC",
    )

    @Resource("{id}")
    data class Id(val parent: SeriesResource = SeriesResource(), val id: String) {
        @Resource("cover")
        data class Cover(val parent: Id) {
            val id = parent.id
        }

        @Resource("books")
        data class Books(val parent: Id) {
            val id = parent.id
        }

        @Resource("authors")
        data class Authors(val parent: Id) {
            val id = parent.id
        }

        @Resource("details")
        data class Details(val parent: Id) {
            val id = parent.id
        }
    }
}
