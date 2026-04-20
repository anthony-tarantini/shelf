package io.tarantini.shelf.catalog.search

import io.ktor.resources.*
import io.tarantini.shelf.app.RootResource

@Resource("search")
data class SearchResource(val parent: RootResource = RootResource, val q: String = "")
