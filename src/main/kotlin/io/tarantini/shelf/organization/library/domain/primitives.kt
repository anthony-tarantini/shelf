@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library.domain

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.either
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
value class LibraryId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(libraryId: String?): LibraryId {
            ensureNotNull(libraryId) { EmptyLibraryId }
            ensure(libraryId.isNotEmpty()) { EmptyLibraryId }
            return LibraryId(ensureNotNull(Uuid.parseOrNull(libraryId)) { InvalidLibraryId })
        }

        operator fun invoke(libraryId: Uuid?) = either {
            ensureNotNull(libraryId) { EmptyLibraryId }
            LibraryId(libraryId)
        }

        operator fun invoke(libraryId: UUID?) = either {
            ensureNotNull(libraryId) { EmptyLibraryId }
            LibraryId(libraryId.toKotlinUuid())
        }

        fun fromRaw(value: Uuid) = LibraryId(value)

        fun fromRaw(value: UUID) = LibraryId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<LibraryId>(::fromRaw) {}
    }
}
