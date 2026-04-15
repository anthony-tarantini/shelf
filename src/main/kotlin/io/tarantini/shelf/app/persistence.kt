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
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.Series
import io.tarantini.shelf.integration.koreader.persistence.Koreader_progress
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.organization.library.persistence.Libraries
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
            "users",
            "api_tokens",
            "koreader_progress",
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
    )
}

private class JsonAdapter : app.cash.sqldelight.ColumnAdapter<String, String> {
    override fun decode(databaseValue: String): String = databaseValue

    override fun encode(value: String): String = value
}
