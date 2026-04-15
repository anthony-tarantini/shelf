@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.metadata.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.IdValidator.create
import io.tarantini.shelf.app.StringAdapter
import io.tarantini.shelf.app.StringValueClass
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class MetadataId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(id: String?) =
            create(id, EmptyMetadataId, InvalidMetadataId, ::MetadataId)

        fun fromRaw(value: UUID) = MetadataId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<MetadataId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class EditionId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(id: String?) =
            create(id, EmptyMetadataId, InvalidMetadataId, ::EditionId)

        fun fromRaw(value: UUID) = EditionId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<EditionId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class ChapterId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(id: String?) =
            create(id, EmptyMetadataId, InvalidMetadataId, ::ChapterId)

        fun fromRaw(value: UUID) = ChapterId(value.toKotlinUuid())

        val adapter = object : UuidAdapter<ChapterId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class ASIN private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(value: String?): ASIN {
            ensure(!value.isNullOrEmpty()) { EmptyASIN }
            value.sanitizeIdentifier().let {
                ensure(it.length == 10) { if (it.length > 10) LongASIN else ShortASIN }
                return ASIN(it)
            }
        }

        fun fromRaw(value: String) = ASIN(value)

        val adapter = object : StringAdapter<ASIN>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class ISBN10 private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(value: String?): ISBN10 {
            ensure(!value.isNullOrEmpty()) { EmptyISBN }
            value.sanitizeIdentifier().let {
                ensure(it.length == 10) { if (it.length > 10) LongISBN else ShortISBN }
                return ISBN10(it)
            }
        }

        fun fromRaw(value: String) = ISBN10(value)

        val adapter = object : StringAdapter<ISBN10>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class ISBN13 private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(value: String?): ISBN13 {
            ensure(!value.isNullOrEmpty()) { EmptyISBN13 }
            value.sanitizeIdentifier().let {
                ensure(it.length == 13) { if (it.length > 13) LongISBN13 else ShortISBN13 }
                return ISBN13(it)
            }
        }

        fun fromRaw(value: String) = ISBN13(value)

        val adapter = object : StringAdapter<ISBN13>(::fromRaw) {}
    }
}

private fun String.sanitizeIdentifier(): String {
    return this.replace(Regex("[^a-zA-Z0-9]"), "")
}
