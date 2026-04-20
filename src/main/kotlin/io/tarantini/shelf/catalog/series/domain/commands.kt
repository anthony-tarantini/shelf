package io.tarantini.shelf.catalog.series.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext

@JvmInline
value class SeriesTitle private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): SeriesTitle {
            ensure(!raw.isNullOrBlank()) { EmptySeriesTitle }
            return SeriesTitle(raw.trim())
        }

        fun fromRaw(value: String): SeriesTitle = SeriesTitle(value)
    }
}

data class CreateSeriesCommand(val title: SeriesTitle)

data class UpdateSeriesCommand(val id: SeriesId, val title: SeriesTitle?)
