@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.integration.koreader.stats.domain.IngestKoreaderStatsCommand
import io.tarantini.shelf.user.identity.createUser
import io.tarantini.shelf.user.identity.domain.HashedPassword
import io.tarantini.shelf.user.identity.domain.NewUser
import io.tarantini.shelf.user.identity.domain.Salt
import io.tarantini.shelf.user.identity.domain.UserEmail
import io.tarantini.shelf.user.identity.domain.UserName
import io.tarantini.shelf.user.identity.domain.UserRole
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.uuid.ExperimentalUuidApi

private fun buildFixtureDb(target: Path) {
    Class.forName("org.sqlite.JDBC")
    DriverManager.getConnection("jdbc:sqlite:${target.toAbsolutePath()}").use { conn ->
        createFixtureSchema(conn)
        insertFixtureBooks(conn)
        insertFixturePageStats(conn)
    }
}

private fun createFixtureSchema(conn: java.sql.Connection) {
    conn.createStatement().use { stmt ->
        stmt.execute(
            """
            CREATE TABLE book(
                id INTEGER PRIMARY KEY,
                title TEXT,
                authors TEXT,
                series TEXT,
                language TEXT,
                pages INTEGER,
                md5 TEXT,
                total_read_time INTEGER,
                total_read_pages INTEGER,
                last_open INTEGER
            )
            """
                .trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE page_stat_data(
                id_book INTEGER,
                page INTEGER,
                start_time INTEGER,
                duration INTEGER,
                total_pages INTEGER
            )
            """
                .trimIndent()
        )
    }
}

private fun insertFixtureBooks(conn: java.sql.Connection) {
    val sql =
        "INSERT INTO book(id,title,authors,series,language,pages,md5," +
            "total_read_time,total_read_pages,last_open) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?)"
    conn.prepareStatement(sql).use { ps ->
        ps.setLong(1, 1)
        ps.setString(2, "Book One")
        ps.setString(3, "Author A")
        ps.setString(4, null)
        ps.setString(5, "en")
        ps.setInt(6, 100)
        ps.setString(7, "a".repeat(32))
        ps.setLong(8, 600)
        ps.setInt(9, 30)
        ps.setLong(10, 1700000000L)
        ps.executeUpdate()
        ps.setLong(1, 2)
        ps.setString(2, "Book Two (no md5)")
        ps.setString(3, "Author B")
        ps.setString(4, null)
        ps.setString(5, "en")
        ps.setInt(6, 50)
        ps.setNull(7, java.sql.Types.VARCHAR)
        ps.setLong(8, 120)
        ps.setInt(9, 5)
        ps.setLong(10, 1700001000L)
        ps.executeUpdate()
    }
}

private fun insertFixturePageStats(conn: java.sql.Connection) {
    val sql =
        "INSERT INTO page_stat_data(id_book,page,start_time,duration,total_pages) " +
            "VALUES(?,?,?,?,?)"
    conn.prepareStatement(sql).use { ps ->
        val rows =
            listOf(
                Triple(1L, 1, 1700000000L) to (60 to 100),
                Triple(1L, 2, 1700000060L) to (60 to 100),
                Triple(1L, 3, 1700000200L) to (60 to 100),
                Triple(1L, 4, 1700020000L) to (60 to 100),
                Triple(2L, 1, 1700001000L) to (30 to 50),
                Triple(2L, 2, 1700001100L) to (30 to 50),
            )
        rows.forEach { (key, val2) ->
            val (idBook, page, start) = key
            val (duration, totalPages) = val2
            ps.setLong(1, idBook)
            ps.setInt(2, page)
            ps.setLong(3, start)
            ps.setInt(4, duration)
            ps.setInt(5, totalPages)
            ps.executeUpdate()
        }
    }
}

class KoreaderStatsIngestTest :
    IntegrationSpec({
        "ingest creates books, pages, and sessions per gap algorithm" {
            testWithDeps { deps ->
                val tmp = Files.createTempDirectory("koreader-stats-")
                val sqliteFile = tmp.resolve("statistics.sqlite")
                buildFixtureDb(sqliteFile)

                recover({
                    val user =
                        deps.database.userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("kostats-1@example.com"),
                                username = UserName.fromRaw("kostats1"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    val summary =
                        deps.koreaderStatsService.ingest(
                            IngestKoreaderStatsCommand(user.id.id, sqliteFile)
                        )

                    summary.booksSeen shouldBe 2
                    summary.booksUnmatched shouldBe 2
                    summary.booksMatched shouldBe 0
                    summary.pagesUpserted shouldBe 6
                    // Book 1: pages 1,2,3 within gap (one session) + page 4 past gap (second
                    // session) = 2
                    // Book 2: pages 1,2 within gap = 1
                    summary.sessionsInserted shouldBe 3

                    // Idempotency: re-ingest same file
                    val second =
                        deps.koreaderStatsService.ingest(
                            IngestKoreaderStatsCommand(user.id.id, sqliteFile)
                        )
                    second.booksSeen shouldBe 2
                    second.sessionsInserted shouldBe 3

                    // Book 1 (with md5) deduplicates by (user, md5); book 2 (NULL md5) gets a
                    // fresh surrogate per ingest — 1 + 2 = 3 rows after two passes.
                    val books = deps.koreaderStatsService.listBooks(user.id.id)
                    books.size shouldBe 3

                    val unmatched = deps.koreaderStatsService.listUnmatchedBooks(user.id.id)
                    unmatched.size shouldBe 3
                }) {
                    fail("Should not have failed: $it")
                }

                tmp.toFile().deleteRecursively()
            }
        }
    })
