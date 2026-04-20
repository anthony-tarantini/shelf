@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import

import io.ktor.resources.Resource
import io.tarantini.shelf.catalog.book.BooksResource
import kotlin.uuid.ExperimentalUuidApi

@Resource("staged")
class StagedResource(
    val parent: BooksResource = BooksResource(),
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String? = "createdAt",
    val sortDir: String? = "DESC",
    val author: String? = null,
) {
    @Resource("batch")
    class Batch(val parent: StagedResource) {
        @Resource("progress") class Progress(val parent: Batch)
    }

    @Resource("{id}")
    class Id(val parent: StagedResource, val id: String) {
        @Resource("cover")
        class Cover(val parent: Id) {
            val id = parent.id
        }

        @Resource("promote")
        class Promote(val parent: Id) {
            val id = parent.id
        }

        @Resource("merge")
        class Merge(val parent: Id) {
            val id = parent.id
        }

        @Resource("update")
        class Update(val parent: Id) {
            val id = parent.id
        }
    }
}

@Resource("import")
class ImportResource(val parent: BooksResource = BooksResource()) {
    @Resource("scan") class Scan(val parent: ImportResource)

    @Resource("progress") class Progress(val parent: ImportResource)
}
