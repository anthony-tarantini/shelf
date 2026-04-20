package io.tarantini.shelf.catalog.series.domain

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.processing.storage.StoragePath
import kotlinx.serialization.Serializable

@Serializable
data class SeriesSummary(
    val id: SeriesId,
    val name: String,
    val coverPath: StoragePath? = null,
    val bookCount: Int = 0,
    val ebookCount: Int = 0,
)

@Serializable data class SeriesRequest(val id: String? = null, val title: String? = null)

context(_: RaiseContext)
fun SeriesRequest.toCreateCommand(): CreateSeriesCommand =
    CreateSeriesCommand(title = SeriesTitle(title))

context(_: RaiseContext)
fun SeriesRequest.toUpdateCommand(id: String): UpdateSeriesCommand =
    UpdateSeriesCommand(id = SeriesId(id), title = title?.let { SeriesTitle(it) })

@Serializable
data class SeriesPage(
    val items: List<SeriesSummary>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)
