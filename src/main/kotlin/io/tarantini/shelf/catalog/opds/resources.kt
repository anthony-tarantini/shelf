package io.tarantini.shelf.catalog.opds

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource

@Resource("opds/v1.2")
data class OpdsResource(val parent: RootResource = RootResource) {
    @Resource("catalog") data class Catalog(val parent: OpdsResource = OpdsResource())

    @Resource("books")
    data class Books(
        val parent: OpdsResource = OpdsResource(),
        val page: Int? = null,
        val size: Int? = null,
    )

    @Resource("authors")
    data class Authors(val parent: OpdsResource = OpdsResource()) {
        @Resource("{id}")
        data class Id(
            val parent: Authors = Authors(),
            val id: String,
            val page: Int? = null,
            val size: Int? = null,
        )
    }

    @Resource("series")
    data class Series(val parent: OpdsResource = OpdsResource()) {
        @Resource("{id}")
        data class Id(
            val parent: Series = Series(),
            val id: String,
            val page: Int? = null,
            val size: Int? = null,
        )
    }

    @Resource("search")
    data class Search(
        val parent: OpdsResource = OpdsResource(),
        val q: String? = null,
        val query: String? = null,
        val page: Int? = null,
        val size: Int? = null,
    ) {
        @Resource("description") data class Description(val parent: Search = Search())
    }
}
