package io.tarantini.shelf.catalog.series.domain

import io.tarantini.shelf.app.AppError

sealed interface SeriesError : AppError

sealed interface SeriesPersistenceError : SeriesError

object SeriesNotFound : SeriesPersistenceError

object SeriesAlreadyExists : SeriesPersistenceError

object SeriesCoverNotFound : SeriesPersistenceError

object SeriesFuzzySearchDisabled : SeriesPersistenceError

sealed interface SeriesValidationError : SeriesError

object EmptySeriesTitle : SeriesValidationError

object EmptySeriesId : SeriesValidationError

object InvalidSeriesId : SeriesValidationError

object EmptySeriesSlug : SeriesValidationError
