package io.tarantini.shelf.integration.hardcover

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import com.apollographql.apollo.ApolloClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.metadata.domain.ExternalBook
import io.tarantini.shelf.catalog.metadata.domain.ExternalContributor
import io.tarantini.shelf.catalog.metadata.domain.ExternalGenre
import io.tarantini.shelf.catalog.metadata.domain.ExternalMetadata
import io.tarantini.shelf.catalog.metadata.domain.ExternalPublisher
import io.tarantini.shelf.catalog.metadata.domain.ExternalSeries
import io.tarantini.shelf.integration.core.ExternalAuthorResult
import io.tarantini.shelf.integration.core.ExternalMetadataProvider
import io.tarantini.shelf.providers.hardcover.FetchAuthorsByIdsQuery
import io.tarantini.shelf.providers.hardcover.FetchMetadataByBookIdsQuery
import io.tarantini.shelf.providers.hardcover.SearchAuthorsQuery
import io.tarantini.shelf.providers.hardcover.SearchBooksQuery

private val logger = KotlinLogging.logger {}

fun hardcover(apolloClient: ApolloClient) =
    object : ExternalMetadataProvider {
        context(_: RaiseContext)
        override suspend fun searchAuthorsByName(name: String): List<ExternalAuthorResult> {
            logger.info { "Hardcover author search: query=\"$name\"" }
            val searchResponse = apolloClient.query(SearchAuthorsQuery(name)).execute()
            if (searchResponse.hasErrors()) {
                logger.warn { "Hardcover author search errors: ${searchResponse.errors}" }
                ensure(false) { AuthorSearchError }
            }
            val ids =
                searchResponse.data?.search?.ids?.filterNotNull()
                    ?: run {
                        logger.info { "Hardcover author search: no ids returned" }
                        return emptyList()
                    }
            if (ids.isEmpty()) {
                logger.info { "Hardcover author search: empty id list" }
                return emptyList()
            }

            logger.info { "Hardcover author search: fetching ${ids.size} author(s) by id" }
            val authorsResponse = apolloClient.query(FetchAuthorsByIdsQuery(ids)).execute()
            if (authorsResponse.hasErrors()) {
                logger.warn { "Hardcover fetch authors errors: ${authorsResponse.errors}" }
                ensure(false) { AuthorSearchError }
            }
            val authors =
                authorsResponse.data?.authors
                    ?: run {
                        logger.info { "Hardcover author search: no authors returned" }
                        return emptyList()
                    }
            logger.info { "Hardcover author search: returning ${authors.size} author(s)" }
            return authors.map { author ->
                ExternalAuthorResult(
                    id = author.id.toString(),
                    name = author.name,
                    imageUrl = author.image?.url,
                )
            }
        }

        context(_: RaiseContext)
        override suspend fun searchBookMetadataByName(name: String): List<ExternalMetadata> {
            logger.info { "Hardcover book search: query=\"$name\"" }
            val searchResponse = apolloClient.query(SearchBooksQuery(name)).execute()
            if (searchResponse.hasErrors()) {
                logger.warn { "Hardcover book search errors: ${searchResponse.errors}" }
                ensure(false) { BookSearchError }
            }
            val ids =
                ensureNotNull(searchResponse.data?.search?.ids?.filterNotNull()) {
                    return emptyList()
                }
            ensure(ids.isNotEmpty()) {
                return emptyList()
            }
            logger.info { "Hardcover book search: found ${ids.size} id(s), fetching metadata" }
            val bookMetadataResponse =
                apolloClient.query(FetchMetadataByBookIdsQuery(ids)).execute()
            if (bookMetadataResponse.hasErrors()) {
                logger.warn { "Hardcover fetch metadata errors: ${bookMetadataResponse.errors}" }
                ensure(false) { FetchBooksError }
            }
            val books =
                ensureNotNull(bookMetadataResponse.data?.books) {
                    return emptyList()
                }
            ensure(books.isNotEmpty()) {
                return emptyList()
            }
            logger.info { "Hardcover fetch metadata: returning ${books.size} book(s)" }
            return books.mapNotNull { it.toMetadata() }
        }
    }

private fun FetchMetadataByBookIdsQuery.Book.toMetadata(): ExternalMetadata? {
    val title = this.title ?: return null
    return ExternalMetadata(
        id = id.toString(),
        title = title,
        contributors =
            contributions.mapNotNull { it ->
                val author = it.author ?: return@mapNotNull null
                mapContribution(author.id.toString(), author.name, it.contribution)
            },
        description = description,
        publisher =
            default_ebook_edition?.publisher?.let { mapPublisher(it.id, it.name) }
                ?: default_audio_edition?.publisher?.let { mapPublisher(it.id, it.name) },
        releaseYear = release_year,
        imageUrl = image?.url,
        genres =
            genres.map { genre ->
                val tag = genre.tag
                ExternalGenre(id = tag.id.toString(), name = tag.tag)
            },
        seriesName =
            book_series.mapNotNull { bookSeries ->
                val series = bookSeries.series ?: return@mapNotNull null
                ExternalSeries(
                    id = series.id.toString(),
                    name = series.name,
                    description = series.description,
                    position = bookSeries.position?.let { (it as Int).toDouble() },
                )
            },
        defaultEbook =
            default_ebook_edition?.let {
                ExternalBook.ExternalEbook(
                    isbn10 = it.isbn_10,
                    isbn13 = it.isbn_13,
                    publisher = it.publisher?.let { pub -> mapPublisher(pub.id, pub.name) },
                    contributors =
                        it.contributions.mapNotNull { contrib ->
                            val author = contrib.author ?: return@mapNotNull null
                            mapContribution(author.id.toString(), author.name, contrib.contribution)
                        },
                    asin = it.asin,
                    pages = it.pages,
                )
            },
        defaultAudiobook =
            default_audio_edition?.let {
                ExternalBook.ExternalAudiobook(
                    isbn10 = it.isbn_10,
                    isbn13 = it.isbn_13,
                    publisher = it.publisher?.let { pub -> mapPublisher(pub.id, pub.name) },
                    contributors =
                        it.contributions.mapNotNull { contrib ->
                            val author = contrib.author ?: return@mapNotNull null
                            mapContribution(author.id.toString(), author.name, contrib.contribution)
                        },
                    asin = it.asin,
                    seconds = it.audio_seconds,
                )
            },
    )
}

private fun mapContribution(
    authorId: String,
    authorName: String,
    contribution: String?,
): ExternalContributor {
    return when (contribution?.lowercase()) {
        "author",
        null -> ExternalContributor.ExternalAuthor(authorId, authorName)
        "narrator" -> ExternalContributor.ExternalNarrator(authorId, authorName)
        "translator" -> ExternalContributor.ExternalTranslator(authorId, authorName)
        else -> ExternalContributor.ExternalAuthor(authorId, authorName)
    }
}

private fun mapPublisher(id: Any, name: String?): ExternalPublisher? {
    val pubName = name ?: return null
    return ExternalPublisher(id = id.toString(), name = pubName)
}
