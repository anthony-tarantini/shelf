@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.series.domain.SavedSeriesRoot
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SeriesMutationRepository {
    context(_: RaiseContext)
    suspend fun getSeriesById(id: SeriesId): SavedSeriesRoot

    context(_: RaiseContext)
    suspend fun insertSeries(title: String): SavedSeriesRoot

    context(_: RaiseContext)
    suspend fun updateSeries(title: String, id: SeriesId): SavedSeriesRoot
}

fun seriesMutationRepository(seriesQueries: SeriesQueries): SeriesMutationRepository =
    SqlDelightSeriesMutationRepository(seriesQueries)

private class SqlDelightSeriesMutationRepository(private val seriesQueries: SeriesQueries) :
    SeriesMutationRepository {
    context(_: RaiseContext)
    override suspend fun getSeriesById(id: SeriesId) =
        withContext(Dispatchers.IO) { seriesQueries.getSeriesById(id) }

    context(_: RaiseContext)
    override suspend fun insertSeries(title: String) =
        withContext(Dispatchers.IO) {
            seriesQueries.transactionWithResult {
                val id = seriesQueries.createSeries(title)
                seriesQueries.getSeriesById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun updateSeries(title: String, id: SeriesId) =
        withContext(Dispatchers.IO) {
            seriesQueries.transactionWithResult {
                seriesQueries.updateSeries(title, id)
                seriesQueries.getSeriesById(id)
            }
        }
}
