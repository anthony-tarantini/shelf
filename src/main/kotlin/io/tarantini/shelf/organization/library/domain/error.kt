package io.tarantini.shelf.organization.library.domain

import io.tarantini.shelf.app.AppError

sealed interface LibraryError : AppError

sealed interface LibraryPersistenceError : LibraryError

object LibraryNotFound : LibraryPersistenceError

object LibraryAlreadyExists : LibraryPersistenceError

sealed interface LibraryValidationError : LibraryError

object EmptyLibraryTitle : LibraryValidationError

object EmptyLibraryId : LibraryValidationError

object InvalidLibraryId : LibraryValidationError

object EmptyLibrarySlug : LibraryValidationError
