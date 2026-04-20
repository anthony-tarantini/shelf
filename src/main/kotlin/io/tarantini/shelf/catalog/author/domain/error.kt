package io.tarantini.shelf.catalog.author.domain

import io.tarantini.shelf.app.AppError

sealed interface AuthorError : AppError

sealed interface AuthorPersistenceError : AuthorError

object AuthorNotFound : AuthorPersistenceError

object AuthorAlreadyExists : AuthorPersistenceError

sealed interface AuthorValidationError : AuthorError

object EmptyAuthorFirstName : AuthorValidationError

object EmptyAuthorId : AuthorValidationError

object InvalidAuthorId : AuthorValidationError

object InvalidAuthorImage : AuthorValidationError

object AuthorImageTooLarge : AuthorValidationError

object InvalidAuthorImageUrl : AuthorValidationError
