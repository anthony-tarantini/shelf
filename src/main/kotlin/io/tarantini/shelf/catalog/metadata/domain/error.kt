package io.tarantini.shelf.catalog.metadata.domain

import io.tarantini.shelf.app.AppError

sealed interface MetadataError : AppError

data object MetadataNotFound : MetadataError

data object EditionNotFound : MetadataError

data object EmptyMetadataId : MetadataError

data object InvalidMetadataId : MetadataError

data object EmptyChapterId : MetadataError

data object InvalidChapterId : MetadataError

data object EmptySearchQuery : MetadataError

data object EmptyISBN : MetadataError

data object ShortISBN : MetadataError

data object LongISBN : MetadataError

data object EmptyISBN13 : MetadataError

data object ShortISBN13 : MetadataError

data object LongISBN13 : MetadataError

data object EmptyASIN : MetadataError

data object ShortASIN : MetadataError

data object LongASIN : MetadataError
