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
