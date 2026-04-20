package io.tarantini.shelf.integration.hardcover

import io.tarantini.shelf.app.AppError

sealed interface HardcoverError : AppError

data object BookSearchError : AppError

data object FetchBooksError : AppError

data object AuthorSearchError : AppError
