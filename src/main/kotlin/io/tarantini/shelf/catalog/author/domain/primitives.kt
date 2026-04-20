@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author.domain

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class AuthorId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(authorId: String?): AuthorId {
            ensureNotNull(authorId) { EmptyAuthorId }
            ensure(authorId.isNotEmpty()) { EmptyAuthorId }
            return AuthorId(ensureNotNull(Uuid.parseOrNull(authorId)) { InvalidAuthorId })
        }

        fun fromRaw(value: Uuid) = AuthorId(value)

        fun fromRaw(value: UUID) = AuthorId(value.toKotlinUuid())

        fun fromRaw(value: String) = AuthorId(Uuid.parse(value))

        val adapter = object : UuidAdapter<AuthorId>(::fromRaw) {}
    }
}
