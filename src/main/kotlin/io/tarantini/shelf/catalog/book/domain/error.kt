package io.tarantini.shelf.catalog.book.domain

import io.tarantini.shelf.app.AppError

sealed interface BookError : AppError

sealed interface BookPersistenceError : BookError

object BookNotFound : BookPersistenceError

object BookAlreadyExists : BookPersistenceError

data object BookCoverNotFound : BookError

sealed interface BookValidationError : BookError

object EmptyBookTitle : BookValidationError

object EmptyBookId : BookValidationError

object InvalidBookId : BookValidationError

object InvalidBookPublishDate : BookValidationError

data object EmptyBookAuthorName : BookValidationError

data object EmptyBookSeriesName : BookValidationError

data object EmptyBookGenre : BookValidationError

data object EmptyBookMood : BookValidationError

data object EmptyBookPublisher : BookValidationError

data object InvalidBookCoverUrl : BookValidationError

data object SeriesRequiresAuthors : BookValidationError

data object UnknownSelectedAuthorMapping : BookValidationError

data object DuplicateBookAuthors : BookValidationError

data object DuplicateBookSeries : BookValidationError

data object DuplicateBookGenres : BookValidationError

data object DuplicateBookMoods : BookValidationError

data object DuplicateSelectedAuthorIdMapping : BookValidationError
