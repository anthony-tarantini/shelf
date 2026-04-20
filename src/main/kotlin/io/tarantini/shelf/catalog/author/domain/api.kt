package io.tarantini.shelf.catalog.author.domain

import io.tarantini.shelf.RaiseContext
import kotlinx.serialization.Serializable

@Serializable
data class AuthorSummary(
    val id: AuthorId,
    val name: String,
    val bookCount: Int,
    val ebookCount: Int = 0,
    val imagePath: String? = null,
)

@Serializable
data class AuthorPage(
    val items: List<AuthorSummary>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)

@Serializable data class AuthorRequest(val id: String? = null, val name: String? = null)

context(_: RaiseContext)
fun AuthorRequest.toUpdateCommand(): UpdateAuthorCommand =
    UpdateAuthorCommand(id = AuthorId(id), name = AuthorName(name))
