package io.tarantini.shelf.catalog.metadata.domain

import kotlinx.serialization.Serializable

@Serializable
data class ExternalMetadata(
    val id: String,
    val title: String,
    val contributors: List<ExternalContributor> = emptyList(),
    val description: String? = null,
    val publisher: ExternalPublisher? = null,
    val releaseYear: Int? = null,
    val imageUrl: String? = null,
    val genres: List<ExternalGenre> = emptyList(),
    val seriesName: List<ExternalSeries> = emptyList(),
    val defaultEbook: ExternalBook.ExternalEbook? = null,
    val defaultAudiobook: ExternalBook.ExternalAudiobook? = null,
)

@Serializable data class ExternalGenre(val id: String, val name: String)

@Serializable
sealed class ExternalBook {
    abstract val isbn10: String?
    abstract val isbn13: String?
    abstract val publisher: ExternalPublisher?
    abstract val contributors: List<ExternalContributor>
    abstract val asin: String?

    @Serializable
    class ExternalEbook(
        override val isbn10: String? = null,
        override val isbn13: String? = null,
        override val publisher: ExternalPublisher? = null,
        override val contributors: List<ExternalContributor> = emptyList(),
        override val asin: String? = null,
        val pages: Int? = null,
    ) : ExternalBook()

    @Serializable
    class ExternalAudiobook(
        override val isbn10: String? = null,
        override val isbn13: String? = null,
        override val publisher: ExternalPublisher? = null,
        override val contributors: List<ExternalContributor> = emptyList(),
        override val asin: String? = null,
        val seconds: Int? = null,
    ) : ExternalBook()
}

@Serializable data class ExternalPublisher(val id: String, val name: String)

@Serializable data class ExternalRatings(val count: Int, val rating: Double)

@Serializable
data class ExternalSeries(
    val id: String,
    val name: String,
    val description: String? = null,
    val position: Double? = null,
)

@Serializable
sealed class ExternalContributor {
    @Serializable
    data class ExternalAuthor(val id: String, val name: String) : ExternalContributor()

    @Serializable
    data class ExternalNarrator(val id: String, val name: String) : ExternalContributor()

    @Serializable
    data class ExternalTranslator(val id: String, val name: String) : ExternalContributor()
}
