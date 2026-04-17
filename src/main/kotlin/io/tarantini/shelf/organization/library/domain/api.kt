package io.tarantini.shelf.organization.library.domain

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
