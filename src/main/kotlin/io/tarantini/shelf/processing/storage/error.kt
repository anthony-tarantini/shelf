package io.tarantini.shelf.processing.storage

import io.tarantini.shelf.app.AppError

sealed interface StorageError : AppError

object FileNotFound : StorageError

object DiskFull : StorageError

object UnauthorizedAccess : StorageError

object StorageBackendError : StorageError
