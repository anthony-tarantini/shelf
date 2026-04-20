package io.tarantini.shelf.organization.library.domain

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.serialization.Serializable

@Serializable
data class LibrarySummary(
    val id: LibraryId,
    val userId: UserId,
    val title: String,
    val bookCount: Int = 0,
)

@Serializable data class LibraryRequest(val id: String? = null, val title: String? = null)

context(_: RaiseContext)
fun LibraryRequest.toCreateCommand(): CreateLibraryCommand =
    CreateLibraryCommand(title = LibraryTitle(title))

context(_: RaiseContext)
fun LibraryRequest.toUpdateCommand(id: String): UpdateLibraryCommand =
    UpdateLibraryCommand(id = LibraryId(id), title = title?.let { LibraryTitle(it) })
