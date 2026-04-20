package io.tarantini.shelf.organization.library.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext

@JvmInline
value class LibraryTitle private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): LibraryTitle {
            ensure(!raw.isNullOrBlank()) { EmptyLibraryTitle }
            return LibraryTitle(raw.trim())
        }

        fun fromRaw(value: String): LibraryTitle = LibraryTitle(value)
    }
}

data class CreateLibraryCommand(val title: LibraryTitle)

data class UpdateLibraryCommand(val id: LibraryId, val title: LibraryTitle?)
