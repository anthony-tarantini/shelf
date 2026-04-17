@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book.domain

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
value class BookId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(bookId: String?): BookId {
            ensureNotNull(bookId) { EmptyBookId }
            ensure(bookId.isNotEmpty()) { EmptyBookId }
            return BookId(ensureNotNull(Uuid.parseOrNull(bookId)) { InvalidBookId })
        }

        fun fromRaw(value: Uuid) = BookId(value)

        fun fromRaw(value: String) = BookId(Uuid.parse(value))

        fun fromRaw(value: UUID) = BookId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<BookId>(::fromRaw) {}
    }
}
