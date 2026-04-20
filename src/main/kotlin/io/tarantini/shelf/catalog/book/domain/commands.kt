package io.tarantini.shelf.catalog.book.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.ISBN10
import io.tarantini.shelf.catalog.metadata.domain.ISBN13
import java.net.URI
import java.time.Year

@JvmInline
value class BookTitle private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): BookTitle {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookTitle }
            return BookTitle(normalized)
        }

        fun fromRaw(raw: String): BookTitle = BookTitle(raw)
    }
}

@JvmInline
value class AuthorName private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): AuthorName {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookAuthorName }
            return AuthorName(normalized)
        }

        fun fromRaw(raw: String): AuthorName = AuthorName(raw)
    }
}

@JvmInline
value class SeriesName private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): SeriesName {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookSeriesName }
            return SeriesName(normalized)
        }

        fun fromRaw(raw: String): SeriesName = SeriesName(raw)
    }
}

@JvmInline
value class PublisherName private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): PublisherName {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookPublisher }
            return PublisherName(normalized)
        }

        fun fromRaw(raw: String): PublisherName = PublisherName(raw)
    }
}

@JvmInline
value class Genre private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): Genre {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookGenre }
            return Genre(normalized)
        }

        fun fromRaw(raw: String): Genre = Genre(raw)
    }
}

@JvmInline
value class Mood private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): Mood {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyBookMood }
            return Mood(normalized)
        }

        fun fromRaw(raw: String): Mood = Mood(raw)
    }
}

@JvmInline
value class PublishYear private constructor(val value: Int) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Int): PublishYear {
            val upperBound = Year.now().value + 1
            ensure(raw in 1..upperBound) { InvalidBookPublishDate }
            return PublishYear(raw)
        }

        fun fromRaw(raw: Int): PublishYear = PublishYear(raw)
    }
}

@JvmInline
value class CoverSourceUrl private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): CoverSourceUrl {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { InvalidBookCoverUrl }
            val uri = runCatching { URI(normalized) }.getOrNull()
            ensure(uri?.scheme?.lowercase() == "https") { InvalidBookCoverUrl }
            ensure(!uri.host.isNullOrBlank()) { InvalidBookCoverUrl }
            return CoverSourceUrl(normalized)
        }

        fun fromRaw(raw: String): CoverSourceUrl = CoverSourceUrl(raw)
    }
}

sealed interface AuthorRelinkIntent {
    data class UseExisting(val authorId: AuthorId) : AuthorRelinkIntent

    data class UpsertByName(val name: AuthorName) : AuthorRelinkIntent
}

sealed interface SeriesRelinkIntent {
    data class AuthorScopedUpsertByName(val name: SeriesName, val index: Double? = null) :
        SeriesRelinkIntent
}

data class EditionIdentifiersCommand(
    val isbn10: ISBN10? = null,
    val isbn13: ISBN13? = null,
    val asin: ASIN? = null,
    val narrator: String? = null,
)

data class UpdateBookMetadataCommand(
    val title: BookTitle? = null,
    val description: String? = null,
    val authors: List<AuthorRelinkIntent>? = null,
    val publisher: PublisherName? = null,
    val publishYear: PublishYear? = null,
    val genres: List<Genre> = emptyList(),
    val moods: List<Mood> = emptyList(),
    val series: List<SeriesRelinkIntent.AuthorScopedUpsertByName>? = null,
    val ebookMetadata: EditionIdentifiersCommand? = null,
    val audiobookMetadata: EditionIdentifiersCommand? = null,
    val coverUrl: CoverSourceUrl? = null,
)
