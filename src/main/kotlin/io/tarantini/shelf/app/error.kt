package io.tarantini.shelf.app

import arrow.core.raise.context.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorAlreadyExists
import io.tarantini.shelf.catalog.author.domain.AuthorError
import io.tarantini.shelf.catalog.author.domain.AuthorImageTooLarge
import io.tarantini.shelf.catalog.author.domain.AuthorNotFound
import io.tarantini.shelf.catalog.author.domain.EmptyAuthorFirstName
import io.tarantini.shelf.catalog.author.domain.EmptyAuthorId
import io.tarantini.shelf.catalog.author.domain.InvalidAuthorId
import io.tarantini.shelf.catalog.author.domain.InvalidAuthorImage
import io.tarantini.shelf.catalog.author.domain.InvalidAuthorImageUrl
import io.tarantini.shelf.catalog.book.domain.BookAlreadyExists
import io.tarantini.shelf.catalog.book.domain.BookCoverNotFound
import io.tarantini.shelf.catalog.book.domain.BookError
import io.tarantini.shelf.catalog.book.domain.BookNotFound
import io.tarantini.shelf.catalog.book.domain.DuplicateBookAuthors
import io.tarantini.shelf.catalog.book.domain.DuplicateBookGenres
import io.tarantini.shelf.catalog.book.domain.DuplicateBookMoods
import io.tarantini.shelf.catalog.book.domain.DuplicateBookSeries
import io.tarantini.shelf.catalog.book.domain.DuplicateSelectedAuthorIdMapping
import io.tarantini.shelf.catalog.book.domain.EmptyBookAuthorName
import io.tarantini.shelf.catalog.book.domain.EmptyBookGenre
import io.tarantini.shelf.catalog.book.domain.EmptyBookId
import io.tarantini.shelf.catalog.book.domain.EmptyBookMood
import io.tarantini.shelf.catalog.book.domain.EmptyBookPublisher
import io.tarantini.shelf.catalog.book.domain.EmptyBookSeriesName
import io.tarantini.shelf.catalog.book.domain.EmptyBookTitle
import io.tarantini.shelf.catalog.book.domain.InvalidBookCoverUrl
import io.tarantini.shelf.catalog.book.domain.InvalidBookId
import io.tarantini.shelf.catalog.book.domain.InvalidBookPublishDate
import io.tarantini.shelf.catalog.book.domain.SeriesRequiresAuthors
import io.tarantini.shelf.catalog.book.domain.UnknownSelectedAuthorMapping
import io.tarantini.shelf.catalog.metadata.domain.EditionNotFound
import io.tarantini.shelf.catalog.metadata.domain.EmptyASIN
import io.tarantini.shelf.catalog.metadata.domain.EmptyChapterId
import io.tarantini.shelf.catalog.metadata.domain.EmptyISBN
import io.tarantini.shelf.catalog.metadata.domain.EmptyISBN13
import io.tarantini.shelf.catalog.metadata.domain.EmptyMetadataId
import io.tarantini.shelf.catalog.metadata.domain.EmptySearchQuery
import io.tarantini.shelf.catalog.metadata.domain.InvalidChapterId
import io.tarantini.shelf.catalog.metadata.domain.InvalidMetadataId
import io.tarantini.shelf.catalog.metadata.domain.LongASIN
import io.tarantini.shelf.catalog.metadata.domain.LongISBN
import io.tarantini.shelf.catalog.metadata.domain.LongISBN13
import io.tarantini.shelf.catalog.metadata.domain.MetadataError
import io.tarantini.shelf.catalog.metadata.domain.MetadataNotFound
import io.tarantini.shelf.catalog.metadata.domain.ShortASIN
import io.tarantini.shelf.catalog.metadata.domain.ShortISBN
import io.tarantini.shelf.catalog.metadata.domain.ShortISBN13
import io.tarantini.shelf.catalog.podcast.domain.EmptyFeedUrl
import io.tarantini.shelf.catalog.podcast.domain.EmptyPodcastId
import io.tarantini.shelf.catalog.podcast.domain.FeedAuthRequired
import io.tarantini.shelf.catalog.podcast.domain.FeedFetchFailed
import io.tarantini.shelf.catalog.podcast.domain.FeedParseFailed
import io.tarantini.shelf.catalog.podcast.domain.FeedRateLimited
import io.tarantini.shelf.catalog.podcast.domain.InvalidEpisodeIndex
import io.tarantini.shelf.catalog.podcast.domain.InvalidFeedToken
import io.tarantini.shelf.catalog.podcast.domain.InvalidFeedUrl
import io.tarantini.shelf.catalog.podcast.domain.InvalidFetchInterval
import io.tarantini.shelf.catalog.podcast.domain.InvalidPodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastAlreadyExists
import io.tarantini.shelf.catalog.podcast.domain.PodcastError
import io.tarantini.shelf.catalog.podcast.domain.PodcastFeedAlreadySubscribed
import io.tarantini.shelf.catalog.podcast.domain.PodcastNotFound
import io.tarantini.shelf.catalog.series.domain.EmptySeriesId
import io.tarantini.shelf.catalog.series.domain.EmptySeriesSlug
import io.tarantini.shelf.catalog.series.domain.EmptySeriesTitle
import io.tarantini.shelf.catalog.series.domain.InvalidSeriesId
import io.tarantini.shelf.catalog.series.domain.SeriesAlreadyExists
import io.tarantini.shelf.catalog.series.domain.SeriesCoverNotFound
import io.tarantini.shelf.catalog.series.domain.SeriesError
import io.tarantini.shelf.catalog.series.domain.SeriesFuzzySearchDisabled
import io.tarantini.shelf.catalog.series.domain.SeriesNotFound
import io.tarantini.shelf.organization.library.domain.EmptyLibraryId
import io.tarantini.shelf.organization.library.domain.EmptyLibrarySlug
import io.tarantini.shelf.organization.library.domain.EmptyLibraryTitle
import io.tarantini.shelf.organization.library.domain.InvalidLibraryId
import io.tarantini.shelf.organization.library.domain.LibraryAlreadyExists
import io.tarantini.shelf.organization.library.domain.LibraryError
import io.tarantini.shelf.organization.library.domain.LibraryNotFound
import io.tarantini.shelf.processing.audiobook.AudioMetadataReadFailed
import io.tarantini.shelf.processing.audiobook.AudiobookError
import io.tarantini.shelf.processing.audiobook.InvalidAudioFile
import io.tarantini.shelf.processing.audiobook.MissingAudioMetadata
import io.tarantini.shelf.processing.epub.EpubError
import io.tarantini.shelf.processing.epub.InvalidContainerXML
import io.tarantini.shelf.processing.epub.InvalidOPF
import io.tarantini.shelf.processing.epub.MissingContainerXML
import io.tarantini.shelf.processing.epub.MissingOPF
import io.tarantini.shelf.processing.import.domain.BatchAlreadyRunning
import io.tarantini.shelf.processing.import.domain.DirectoryNotFound
import io.tarantini.shelf.processing.import.domain.ImportError
import io.tarantini.shelf.processing.import.domain.ImportFailed
import io.tarantini.shelf.processing.import.domain.MissingFile
import io.tarantini.shelf.processing.import.domain.ScanFailed
import io.tarantini.shelf.processing.import.domain.StagedBookNotFound
import io.tarantini.shelf.processing.import.domain.StagedCoverNotFound
import io.tarantini.shelf.processing.import.domain.UnsupportedFormat
import io.tarantini.shelf.processing.sanitization.domain.EmptySanitizationJobId
import io.tarantini.shelf.processing.sanitization.domain.FfmpegExecutionFailed
import io.tarantini.shelf.processing.sanitization.domain.InvalidAdSegment
import io.tarantini.shelf.processing.sanitization.domain.InvalidAudioTimestamp
import io.tarantini.shelf.processing.sanitization.domain.InvalidSanitizationJobId
import io.tarantini.shelf.processing.sanitization.domain.InvalidSanitizationTransition
import io.tarantini.shelf.processing.sanitization.domain.OriginalFileNotFound
import io.tarantini.shelf.processing.sanitization.domain.SanitizationError
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobNotFound
import io.tarantini.shelf.processing.sanitization.domain.TranscriptionFailed
import io.tarantini.shelf.processing.storage.DiskFull
import io.tarantini.shelf.processing.storage.FileNotFound
import io.tarantini.shelf.processing.storage.ImageTooLarge
import io.tarantini.shelf.processing.storage.InvalidImage
import io.tarantini.shelf.processing.storage.InvalidImageUrl
import io.tarantini.shelf.processing.storage.StorageBackendError
import io.tarantini.shelf.processing.storage.StorageError
import io.tarantini.shelf.processing.storage.UnauthorizedAccess
import io.tarantini.shelf.user.identity.domain.EmailAlreadyExists
import io.tarantini.shelf.user.identity.domain.EmptyEmail
import io.tarantini.shelf.user.identity.domain.EmptyPassword
import io.tarantini.shelf.user.identity.domain.EmptyTokenId
import io.tarantini.shelf.user.identity.domain.EmptyUserId
import io.tarantini.shelf.user.identity.domain.EmptyUsername
import io.tarantini.shelf.user.identity.domain.IncorrectPassword
import io.tarantini.shelf.user.identity.domain.InvalidEmail
import io.tarantini.shelf.user.identity.domain.InvalidTokenId
import io.tarantini.shelf.user.identity.domain.InvalidUserId
import io.tarantini.shelf.user.identity.domain.JwtGeneration
import io.tarantini.shelf.user.identity.domain.JwtInvalid
import io.tarantini.shelf.user.identity.domain.JwtMissing
import io.tarantini.shelf.user.identity.domain.SetupAlreadyComplete
import io.tarantini.shelf.user.identity.domain.TooShortPassword
import io.tarantini.shelf.user.identity.domain.TooShortUsername
import io.tarantini.shelf.user.identity.domain.UserError
import io.tarantini.shelf.user.identity.domain.UserNotFound
import io.tarantini.shelf.user.identity.domain.UsernameAlreadyExists
import kotlinx.serialization.Serializable

interface AppError

object DataError : AppError

object AccessDenied : AppError

@Serializable
data class ErrorResponse(val type: String, val message: String, val code: String? = null)

suspend inline fun <reified A : Any> RoutingContext.respond(
    function:
        suspend context(RaiseContext)
        () -> A,
    status: HttpStatusCode = HttpStatusCode.OK,
): Unit =
    either { function() }
        .fold(
            { err: AppError -> respond(err) },
            { result -> call.respond(status, Response(result)) },
        )

suspend inline fun RoutingContext.respondError(
    code: HttpStatusCode,
    message: String,
    type: String = "Error",
) {
    call.respond(code, ErrorResponse(type, message))
}

suspend fun RoutingContext.respond(error: AppError) {
    val (status, message) = error.toHttpResponse()
    respondError(status, message, error::class.simpleName ?: "Error")
}

fun AppError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        is UserError -> toHttpResponse()
        is BookError -> toHttpResponse()
        is AuthorError -> toHttpResponse()
        is SeriesError -> toHttpResponse()
        is LibraryError -> toHttpResponse()
        is StorageError -> toHttpResponse()
        is EpubError -> toHttpResponse()
        is AudiobookError -> toHttpResponse()
        is ImportError -> toHttpResponse()
        is MetadataError -> toHttpResponse()
        is PodcastError -> toHttpResponse()
        is SanitizationError -> toHttpResponse()
        is DataError -> HttpStatusCode.InternalServerError to "Database error"
        is AccessDenied -> HttpStatusCode.Forbidden to "Access denied"
        else -> HttpStatusCode.InternalServerError to "Internal server error"
    }

private fun PodcastError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        PodcastNotFound -> HttpStatusCode.NotFound to "Podcast not found"
        PodcastAlreadyExists -> HttpStatusCode.Conflict to "Podcast already exists"
        PodcastFeedAlreadySubscribed ->
            HttpStatusCode.Conflict to "Feed URL is already subscribed by another podcast"
        EmptyPodcastId -> HttpStatusCode.BadRequest to "Podcast id is required"
        InvalidPodcastId -> HttpStatusCode.BadRequest to "Invalid podcast id"
        EmptyFeedUrl -> HttpStatusCode.BadRequest to "Feed URL is required"
        InvalidFeedUrl -> HttpStatusCode.BadRequest to "Invalid feed URL"
        InvalidFeedToken -> HttpStatusCode.BadRequest to "Invalid feed token"
        InvalidEpisodeIndex -> HttpStatusCode.BadRequest to "Invalid episode index"
        InvalidFetchInterval ->
            HttpStatusCode.BadRequest to "Fetch interval must be between 1 and 10080 minutes"
        FeedFetchFailed -> HttpStatusCode.BadGateway to "Failed to fetch podcast feed"
        FeedParseFailed -> HttpStatusCode.UnprocessableEntity to "Failed to parse podcast feed"
        FeedAuthRequired -> HttpStatusCode.Unauthorized to "Feed authentication required"
        is FeedRateLimited ->
            HttpStatusCode.TooManyRequests to
                (retryAfterSeconds?.let { "Feed rate limited. Retry after $it seconds" }
                    ?: "Feed rate limited")
    }

private fun SanitizationError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        SanitizationJobNotFound -> HttpStatusCode.NotFound to "Sanitization job not found"
        EmptySanitizationJobId -> HttpStatusCode.BadRequest to "Sanitization job id is required"
        InvalidSanitizationJobId -> HttpStatusCode.BadRequest to "Invalid sanitization job id"
        InvalidAudioTimestamp -> HttpStatusCode.BadRequest to "Invalid audio timestamp"
        InvalidAdSegment -> HttpStatusCode.BadRequest to "Invalid ad segment"
        InvalidSanitizationTransition ->
            HttpStatusCode.BadRequest to "Invalid sanitization status transition"
        FfmpegExecutionFailed -> HttpStatusCode.BadGateway to "FFmpeg execution failed"
        TranscriptionFailed -> HttpStatusCode.BadGateway to "Transcription failed"
        OriginalFileNotFound -> HttpStatusCode.NotFound to "Original audio file not found"
    }

private fun ImportError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        MissingFile -> HttpStatusCode.BadRequest to "Missing file"
        UnsupportedFormat -> HttpStatusCode.UnsupportedMediaType to "Unsupported format"
        ImportFailed -> HttpStatusCode.InternalServerError to "Import failed"
        StagedBookNotFound -> HttpStatusCode.NotFound to "Staged book not found"
        StagedCoverNotFound -> HttpStatusCode.NotFound to "Staged cover not found"
        DirectoryNotFound -> HttpStatusCode.NotFound to "Directory not found"
        ScanFailed -> HttpStatusCode.InternalServerError to "Scan failed"
        BatchAlreadyRunning -> HttpStatusCode.Conflict to "A batch operation is already running"
    }

private fun UserError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        JwtGeneration -> HttpStatusCode.InternalServerError to "Failed to generate JWT token"
        JwtInvalid -> HttpStatusCode.Unauthorized to "Invalid JWT token"
        JwtMissing -> HttpStatusCode.Unauthorized to "JWT token is missing"
        EmailAlreadyExists -> HttpStatusCode.Conflict to "Email already exists"
        UsernameAlreadyExists -> HttpStatusCode.Conflict to "Username already exists"
        SetupAlreadyComplete -> HttpStatusCode.Conflict to "Setup already complete"
        IncorrectPassword -> HttpStatusCode.Unauthorized to "Incorrect password"
        UserNotFound -> HttpStatusCode.NotFound to "User not found"
        EmptyEmail -> HttpStatusCode.BadRequest to "Email is required"
        EmptyPassword -> HttpStatusCode.BadRequest to "Password is required"
        EmptyUserId -> HttpStatusCode.BadRequest to "User id is required"
        EmptyUsername -> HttpStatusCode.BadRequest to "Username is required"
        InvalidEmail -> HttpStatusCode.BadRequest to "Invalid email"
        InvalidUserId -> HttpStatusCode.BadRequest to "Invalid user id"
        EmptyTokenId -> HttpStatusCode.BadRequest to "Token id is required"
        InvalidTokenId -> HttpStatusCode.BadRequest to "Invalid token id"
        TooShortPassword ->
            HttpStatusCode.UnprocessableEntity to "Password must be at least 8 characters long"

        TooShortUsername ->
            HttpStatusCode.UnprocessableEntity to "Username must be at least 3 characters long"
    }

private fun BookError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        BookNotFound -> HttpStatusCode.NotFound to "Book not found"
        BookCoverNotFound -> HttpStatusCode.NotFound to "Book cover not found"
        BookAlreadyExists -> HttpStatusCode.Conflict to "Book already exists"
        EmptyBookTitle -> HttpStatusCode.BadRequest to "Title is required"
        EmptyBookId -> HttpStatusCode.BadRequest to "Book id is required"
        EmptyBookAuthorName -> HttpStatusCode.BadRequest to "Author name is required"
        EmptyBookSeriesName -> HttpStatusCode.BadRequest to "Series name is required"
        EmptyBookGenre -> HttpStatusCode.BadRequest to "Genre is required"
        EmptyBookMood -> HttpStatusCode.BadRequest to "Mood is required"
        EmptyBookPublisher -> HttpStatusCode.BadRequest to "Publisher is required"
        InvalidBookId -> HttpStatusCode.BadRequest to "Invalid book id"
        InvalidBookPublishDate -> HttpStatusCode.BadRequest to "Invalid book publish date"
        InvalidBookCoverUrl -> HttpStatusCode.BadRequest to "Invalid cover URL"
        SeriesRequiresAuthors -> HttpStatusCode.BadRequest to "Series updates require authors"
        UnknownSelectedAuthorMapping ->
            HttpStatusCode.BadRequest to "selectedAuthorIds contains unknown author keys"
        DuplicateBookAuthors -> HttpStatusCode.BadRequest to "Duplicate authors are not allowed"
        DuplicateBookSeries -> HttpStatusCode.BadRequest to "Duplicate series are not allowed"
        DuplicateBookGenres -> HttpStatusCode.BadRequest to "Duplicate genres are not allowed"
        DuplicateBookMoods -> HttpStatusCode.BadRequest to "Duplicate moods are not allowed"
        DuplicateSelectedAuthorIdMapping ->
            HttpStatusCode.BadRequest to "selectedAuthorIds contains duplicate author id values"
    }

private fun AuthorError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        AuthorNotFound -> HttpStatusCode.NotFound to "Author not found"
        AuthorAlreadyExists -> HttpStatusCode.Conflict to "Author already exists"
        EmptyAuthorFirstName -> HttpStatusCode.BadRequest to "First name is required"
        EmptyAuthorId -> HttpStatusCode.BadRequest to "Author id is required"
        InvalidAuthorId -> HttpStatusCode.BadRequest to "Invalid author id"
        InvalidAuthorImage ->
            HttpStatusCode.BadRequest to "Author image must be a valid PNG, JPEG, or WEBP image"
        AuthorImageTooLarge ->
            HttpStatusCode.PayloadTooLarge to "Author image exceeds the maximum allowed size"
        InvalidAuthorImageUrl ->
            HttpStatusCode.BadRequest to "Author image URL must point to a valid HTTPS image"
    }

private fun SeriesError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        SeriesNotFound -> HttpStatusCode.NotFound to "Series not found"
        SeriesCoverNotFound -> HttpStatusCode.NotFound to "Series cover not found"
        SeriesFuzzySearchDisabled ->
            HttpStatusCode.NotImplemented to "Fuzzy series search is temporarily disabled"
        SeriesAlreadyExists -> HttpStatusCode.Conflict to "Series already exists"
        EmptySeriesTitle -> HttpStatusCode.BadRequest to "Title is required"
        EmptySeriesId -> HttpStatusCode.BadRequest to "Series id is required"
        InvalidSeriesId -> HttpStatusCode.BadRequest to "Invalid series id"
        EmptySeriesSlug -> HttpStatusCode.BadRequest to "Slug is required"
    }

private fun LibraryError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        LibraryNotFound -> HttpStatusCode.NotFound to "Library not found"
        LibraryAlreadyExists -> HttpStatusCode.Conflict to "Library already exists"
        EmptyLibraryTitle -> HttpStatusCode.BadRequest to "Title is required"
        EmptyLibraryId -> HttpStatusCode.BadRequest to "Library id is required"
        InvalidLibraryId -> HttpStatusCode.BadRequest to "Invalid library id"
        EmptyLibrarySlug -> HttpStatusCode.BadRequest to "Slug is required"
    }

private fun StorageError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        FileNotFound -> HttpStatusCode.NotFound to "File not found"
        DiskFull -> HttpStatusCode.InsufficientStorage to "Disk is full"
        UnauthorizedAccess -> HttpStatusCode.Forbidden to "Unauthorized storage access"
        StorageBackendError -> HttpStatusCode.InternalServerError to "Storage backend error"
        InvalidImageUrl -> HttpStatusCode.BadRequest to "Invalid image URL"
        InvalidImage -> HttpStatusCode.BadRequest to "Invalid image"
        ImageTooLarge -> HttpStatusCode.BadRequest to "Image too large"
    }

private fun EpubError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        InvalidContainerXML -> HttpStatusCode.UnprocessableEntity to "Invalid container.xml"
        MissingContainerXML -> HttpStatusCode.UnprocessableEntity to "Missing container.xml"
        MissingOPF -> HttpStatusCode.UnprocessableEntity to "Missing OPF file"
        InvalidOPF -> HttpStatusCode.UnprocessableEntity to "Invalid OPF file"
    }

private fun AudiobookError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        InvalidAudioFile -> HttpStatusCode.UnprocessableEntity to "Invalid audio file"
        MissingAudioMetadata -> HttpStatusCode.UnprocessableEntity to "Missing audio metadata"
        AudioMetadataReadFailed ->
            HttpStatusCode.UnprocessableEntity to "Failed to read audio metadata"
    }

private fun MetadataError.toHttpResponse(): Pair<HttpStatusCode, String> =
    when (this) {
        MetadataNotFound -> HttpStatusCode.NotFound to "Metadata not found"
        EditionNotFound -> HttpStatusCode.NotFound to "Edition not found"
        EmptyMetadataId -> HttpStatusCode.BadRequest to "Metadata id is required"
        InvalidMetadataId -> HttpStatusCode.BadRequest to "Invalid metadata id"
        EmptyChapterId -> HttpStatusCode.BadRequest to "Chapter id is required"
        InvalidChapterId -> HttpStatusCode.BadRequest to "Invalid chapter id"
        EmptySearchQuery -> HttpStatusCode.BadRequest to "Search query is required"
        EmptyISBN -> HttpStatusCode.BadRequest to "ISBN is required"
        ShortISBN -> HttpStatusCode.BadRequest to "ISBN is too short"
        LongISBN -> HttpStatusCode.BadRequest to "ISBN is too long"
        EmptyISBN13 -> HttpStatusCode.BadRequest to "ISBN13 is required"
        ShortISBN13 -> HttpStatusCode.BadRequest to "ISBN13 is too short"
        LongISBN13 -> HttpStatusCode.BadRequest to "ISBN13 is too long"
        EmptyASIN -> HttpStatusCode.BadRequest to "ASIN is required"
        ShortASIN -> HttpStatusCode.BadRequest to "ASIN is too short"
        LongASIN -> HttpStatusCode.BadRequest to "ASIN is too long"
    }
