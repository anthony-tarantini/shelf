package io.tarantini.shelf.app

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.closeable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.tarantini.shelf.Database
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.persistence.Authors
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.persistence.Books
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.ChapterId
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.catalog.metadata.domain.ISBN10
import io.tarantini.shelf.catalog.metadata.domain.ISBN13
import io.tarantini.shelf.catalog.metadata.domain.MetadataId
import io.tarantini.shelf.catalog.metadata.persistence.Chapters
import io.tarantini.shelf.catalog.metadata.persistence.Editions
import io.tarantini.shelf.catalog.metadata.persistence.Metadata
import io.tarantini.shelf.catalog.persistence.Book_authors
import io.tarantini.shelf.catalog.persistence.Book_genres
import io.tarantini.shelf.catalog.persistence.Book_moods
import io.tarantini.shelf.catalog.persistence.Library_books
import io.tarantini.shelf.catalog.persistence.Series_authors
import io.tarantini.shelf.catalog.persistence.Series_books
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.persistence.Episode_guids
import io.tarantini.shelf.catalog.podcast.persistence.Podcast_episodes
import io.tarantini.shelf.catalog.podcast.persistence.Podcasts
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.Series
import io.tarantini.shelf.integration.koreader.persistence.Koreader_progress
import io.tarantini.shelf.integration.koreader.persistence.Koreader_stat_books
import io.tarantini.shelf.integration.koreader.persistence.Koreader_stat_pages
import io.tarantini.shelf.integration.koreader.persistence.Koreader_stat_sessions
import io.tarantini.shelf.integration.koreader.persistence.Koreader_users
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookId
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderSessionId
import io.tarantini.shelf.integration.persistence.Integration_credentials
import io.tarantini.shelf.integration.persistence.Libation_import_records
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.organization.library.persistence.Libraries
import io.tarantini.shelf.organization.settings.persistence.User_settings
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobId
import io.tarantini.shelf.processing.sanitization.persistence.Sanitization_jobs
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.activity.persistence.Book_read_status
import io.tarantini.shelf.user.activity.persistence.Reading_progress
import io.tarantini.shelf.user.identity.domain.HashedPassword
import io.tarantini.shelf.user.identity.domain.Salt
import io.tarantini.shelf.user.identity.domain.TokenHash
import io.tarantini.shelf.user.identity.domain.TokenId
import io.tarantini.shelf.user.identity.domain.UserEmail
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.UserName
import io.tarantini.shelf.user.identity.domain.UserRole
import io.tarantini.shelf.user.identity.persistence.Api_tokens
import io.tarantini.shelf.user.identity.persistence.Users
import javax.sql.DataSource

suspend fun ResourceScope.hikari(env: Env.DataSource): HikariDataSource = autoCloseable {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.url
            username = env.username
            password = env.password
            driverClassName = env.driver
        }
    )
}

suspend fun ResourceScope.sqlDelight(dataSource: DataSource): Database {
    val driver = closeable { dataSource.asJdbcDriver() }
    Database.Schema.create(driver)
    listOf(
            "authors",
            "book_authors",
            "book_genres",
            "book_moods",
            "books",
            "chapters",
            "editions",
            "libraries",
            "library_books",
            "metadata",
            "book_read_status",
            "reading_progress",
            "series",
            "series_authors",
            "series_books",
            "podcasts",
            "users",
            "api_tokens",
            "koreader_progress",
            "koreader_users",
            "user_settings",
        )
        .forEach { table ->
            driver.execute(null, "SELECT enable_temporal_versioning('$table');", 0)
        }

    // MVP Migration: Ensure the role column exists for older databases,
    // and upgrade the first user to ADMIN if no admin exists.
    driver.execute(
        null,
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(255) NOT NULL DEFAULT 'USER';",
        0,
    )
    driver.execute(
        null,
        "UPDATE users SET role = 'ADMIN' WHERE id = (SELECT id FROM users " +
            "ORDER BY sys_period ASC LIMIT 1) AND NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN');",
        0,
    )

    // Series model migration: title is no longer globally unique.
    driver.execute(null, "ALTER TABLE series DROP CONSTRAINT IF EXISTS series_title_key;", 0)
    driver.execute(
        null,
        "ALTER TABLE libation_import_records ADD COLUMN IF NOT EXISTS episode_id UUID;",
        0,
    )
    driver.execute(
        null,
        "ALTER TABLE libation_import_records ADD COLUMN IF NOT EXISTS canonical_series_key TEXT;",
        0,
    )
    driver.execute(
        null,
        "UPDATE libation_import_records " +
            "SET canonical_series_key = LOWER(REGEXP_REPLACE(TRIM(COALESCE(asin, '')), '\\\\s+', ' ', 'g')) " +
            "WHERE canonical_series_key IS NULL;",
        0,
    )
    driver.execute(
        null,
        "ALTER TABLE libation_import_records ALTER COLUMN canonical_series_key SET NOT NULL;",
        0,
    )
    driver.execute(
        null,
        "CREATE INDEX IF NOT EXISTS idx_libation_import_records_canonical_series_key " +
            "ON libation_import_records(canonical_series_key);",
        0,
    )

    return Database(
        driver,
        authorsAdapter = Authors.Adapter(AuthorId.adapter),
        book_authorsAdapter = Book_authors.Adapter(BookId.adapter, AuthorId.adapter),
        booksAdapter = Books.Adapter(BookId.adapter, StoragePath.adapter),
        chaptersAdapter = Chapters.Adapter(ChapterId.adapter, EditionId.adapter),
        editionsAdapter =
            Editions.Adapter(
                idAdapter = EditionId.adapter,
                book_idAdapter = BookId.adapter,
                formatAdapter = EnumColumnAdapter<BookFormat>(),
                pathAdapter = StoragePath.adapter,
                isbn_10Adapter = ISBN10.adapter,
                isbn_13Adapter = ISBN13.adapter,
                asinAdapter = ASIN.adapter,
            ),
        librariesAdapter = Libraries.Adapter(LibraryId.adapter, UserId.adapter),
        library_booksAdapter = Library_books.Adapter(LibraryId.adapter, BookId.adapter),
        metadataAdapter = Metadata.Adapter(MetadataId.adapter, BookId.adapter),
        seriesAdapter = Series.Adapter(SeriesId.adapter),
        series_authorsAdapter = Series_authors.Adapter(SeriesId.adapter, AuthorId.adapter),
        series_booksAdapter = Series_books.Adapter(SeriesId.adapter, BookId.adapter),
        podcastsAdapter =
            Podcasts.Adapter(
                idAdapter = PodcastId.adapter,
                series_idAdapter = SeriesId.adapter,
                feed_urlAdapter = FeedUrl.adapter,
                feed_tokenAdapter = FeedToken.adapter,
                feed_token_previousAdapter = FeedToken.adapter,
            ),
        podcast_episodesAdapter =
            Podcast_episodes.Adapter(
                idAdapter = PodcastEpisodeId.adapter,
                podcast_idAdapter = PodcastId.adapter,
                cover_pathAdapter = StoragePath.adapter,
                audio_pathAdapter = StoragePath.adapter,
            ),
        episode_guidsAdapter = Episode_guids.Adapter(PodcastId.adapter, PodcastEpisodeId.adapter),
        usersAdapter =
            Users.Adapter(
                idAdapter = UserId.adapter,
                emailAdapter = UserEmail.adapter,
                usernameAdapter = UserName.adapter,
                roleAdapter = EnumColumnAdapter<UserRole>(),
                saltAdapter = Salt.adapter,
                hashed_passwordAdapter = HashedPassword.adapter,
            ),
        book_genresAdapter = Book_genres.Adapter(BookId.adapter),
        book_moodsAdapter = Book_moods.Adapter(BookId.adapter),
        reading_progressAdapter = Reading_progress.Adapter(UserId.adapter, BookId.adapter),
        book_read_statusAdapter = Book_read_status.Adapter(UserId.adapter, BookId.adapter),
        api_tokensAdapter = Api_tokens.Adapter(TokenId.adapter, UserId.adapter, TokenHash.adapter),
        koreader_progressAdapter = Koreader_progress.Adapter(UserId.adapter, EditionId.adapter),
        koreader_usersAdapter = Koreader_users.Adapter(UserId.adapter),
        koreader_stat_booksAdapter =
            Koreader_stat_books.Adapter(
                idAdapter = KoreaderBookId.adapter,
                user_idAdapter = UserId.adapter,
                edition_idAdapter = EditionId.adapter,
            ),
        koreader_stat_pagesAdapter =
            Koreader_stat_pages.Adapter(
                user_idAdapter = UserId.adapter,
                book_surrogate_idAdapter = KoreaderBookId.adapter,
            ),
        koreader_stat_sessionsAdapter =
            Koreader_stat_sessions.Adapter(
                idAdapter = KoreaderSessionId.adapter,
                user_idAdapter = UserId.adapter,
                book_surrogate_idAdapter = KoreaderBookId.adapter,
            ),
        sanitization_jobsAdapter =
            Sanitization_jobs.Adapter(
                idAdapter = SanitizationJobId.adapter,
                book_idAdapter = BookId.adapter,
                edition_idAdapter = EditionId.adapter,
                original_pathAdapter = StoragePath.adapter,
                sanitized_pathAdapter = StoragePath.adapter,
                transcript_pathAdapter = StoragePath.adapter,
            ),
        integration_credentialsAdapter = Integration_credentials.Adapter(PodcastId.adapter),
        libation_import_recordsAdapter =
            Libation_import_records.Adapter(
                series_idAdapter = SeriesId.adapter,
                podcast_idAdapter = PodcastId.adapter,
                episode_idAdapter = PodcastEpisodeId.adapter,
                book_idAdapter = BookId.adapter,
            ),
        user_settingsAdapter = User_settings.Adapter(UserId.adapter),
    )
}
