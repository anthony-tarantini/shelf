@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series.domain

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
value class SeriesId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(seriesId: String?): SeriesId {
            ensureNotNull(seriesId) { EmptySeriesId }
            ensure(seriesId.isNotEmpty()) { EmptySeriesId }
            return SeriesId(ensureNotNull(Uuid.parseOrNull(seriesId)) { InvalidSeriesId })
        }

        operator fun invoke(seriesId: Uuid?) = either {
            ensureNotNull(seriesId) { EmptySeriesId }
            SeriesId(seriesId)
        }

        operator fun invoke(seriesId: UUID?) = either {
            ensureNotNull(seriesId) { EmptySeriesId }
            SeriesId(seriesId.toKotlinUuid())
        }

        fun fromRaw(value: Uuid) = SeriesId(value)

        fun fromRaw(value: UUID) = SeriesId(value.toKotlinUuid())

        fun fromRaw(value: String) = SeriesId(Uuid.parse(value))

        val adapter = object : UuidAdapter<SeriesId>(::fromRaw) {}
    }
}
