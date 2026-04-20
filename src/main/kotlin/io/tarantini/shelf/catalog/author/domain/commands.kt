package io.tarantini.shelf.catalog.author.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext

@JvmInline
value class AuthorName private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): AuthorName {
            ensure(!raw.isNullOrBlank()) { EmptyAuthorFirstName }
            return AuthorName(raw.trim())
        }

        fun fromRaw(value: String): AuthorName = AuthorName(value)
    }
}

data class UpdateAuthorCommand(val id: AuthorId, val name: AuthorName)

data class CreateAuthorCommand(val name: AuthorName)
