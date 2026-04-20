package io.tarantini.shelf.processing.import.domain

import io.tarantini.shelf.app.AppError

sealed interface ImportError : AppError

data object MissingFile : ImportError

data object UnsupportedFormat : ImportError

data object ImportFailed : ImportError

data object StagedBookNotFound : ImportError

data object StagedCoverNotFound : ImportError

data object DirectoryNotFound : ImportError

data object ScanFailed : ImportError

data object BatchAlreadyRunning : ImportError
