package io.tarantini.shelf.catalog.metadata.domain

interface MetadataDecider {
    fun planAggregate(processed: ProcessedMetadata): NewMetadataAggregate
}

object DefaultMetadataDecider : MetadataDecider {
    override fun planAggregate(processed: ProcessedMetadata): NewMetadataAggregate =
        NewMetadataAggregate(
            metadata = processed.metadata,
            editions =
                listOf(
                    EditionWithChapters(edition = processed.edition, chapters = processed.chapters)
                ),
        )
}
