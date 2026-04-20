package io.tarantini.shelf.catalog.metadata.domain

import io.tarantini.shelf.catalog.book.domain.BookId
import java.nio.file.Path

sealed interface MetadataDomainCommand

data class SaveMetadataAggregateCommand(val aggregate: NewMetadataAggregate) : MetadataDomainCommand

data class ProcessMetadataCommand(val sourcePath: Path, val fileName: String, val bookId: BookId) :
    MetadataDomainCommand
