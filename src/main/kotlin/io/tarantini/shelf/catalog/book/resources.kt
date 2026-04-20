@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import io.ktor.resources.Resource
import io.tarantini.shelf.app.RootResource
import kotlin.uuid.ExperimentalUuidApi

@Resource("books")
data class BooksResource(val parent: RootResource = RootResource) {
    @Resource("details") data class Details(val parent: BooksResource = BooksResource())

    @Resource("page")
    data class Page(
        val parent: BooksResource = BooksResource(),
        val page: Int = 0,
        val size: Int = 20,
        val sortBy: String? = "createdAt",
        val sortDir: String? = "DESC",
        val title: String? = null,
        val author: String? = null,
        val series: String? = null,
        val status: String? = null,
        val format: String? = null,
    )

    @Resource("{id}")
    data class Id(val parent: BooksResource = BooksResource(), val id: String) {
        @Resource("details")
        data class Details(val parent: Id) {
            val id = parent.id
        }

        @Resource("stream")
        data class Stream(val parent: Id) {
            val id = parent.id
        }

        @Resource("download")
        data class Download(val parent: Id) {
            val id = parent.id
        }

        @Resource("cover")
        data class Cover(val parent: Id) {
            val id = parent.id
        }

        @Resource("thumbnail")
        data class Thumbnail(val parent: Id) {
            val id = parent.id
        }

        @Resource("epub/{path...}")
        data class Epub(val parent: Id, val path: List<String> = emptyList()) {
            val id = parent.id
        }

        @Resource("authors")
        data class Authors(val parent: Id) {
            val id = parent.id
        }

        @Resource("series")
        data class Series(val parent: Id) {
            val id = parent.id
        }

        @Resource("progress")
        data class Progress(val parent: Id) {
            val id = parent.id
        }

        @Resource("status")
        data class Status(val parent: Id) {
            val id = parent.id
        }

        @Resource("metadata")
        data class Metadata(val parent: Id) {
            val id = parent.id
        }
    }
}
