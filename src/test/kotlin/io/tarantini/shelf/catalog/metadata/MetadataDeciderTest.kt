@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.processing.storage.StoragePath

class MetadataDeciderTest :
    StringSpec({
        "planAggregate should convert ProcessedMetadata to NewMetadataAggregate correctly" {
            val bookId = BookId.fromRaw(kotlin.uuid.Uuid.random())
            val metadata =
                MetadataRoot<io.tarantini.shelf.app.PersistenceState.Unsaved>(
                    id = Identity.Unsaved,
                    bookId = bookId,
                    title = "Test Book",
                )
            val edition =
                Edition<io.tarantini.shelf.app.PersistenceState.Unsaved>(
                    id = Identity.Unsaved,
                    bookId = bookId,
                    format = BookFormat.EBOOK,
                    path = StoragePath.fromRaw("test.epub"),
                    size = 123,
                )
            val chapters =
                listOf(
                    Chapter<io.tarantini.shelf.app.PersistenceState.Unsaved>(
                        id = Identity.Unsaved,
                        editionId = Identity.Unsaved,
                        title = "Chapter 1",
                    )
                )

            val processed =
                ProcessedMetadata(metadata = metadata, edition = edition, chapters = chapters)

            val aggregate = DefaultMetadataDecider.planAggregate(processed)

            aggregate.metadata shouldBe metadata
            aggregate.editions.size shouldBe 1
            aggregate.editions.first().edition shouldBe edition
            aggregate.editions.first().chapters shouldBe chapters
        }
    })
