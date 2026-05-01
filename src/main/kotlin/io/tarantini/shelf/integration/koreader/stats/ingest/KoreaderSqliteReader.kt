package io.tarantini.shelf.integration.koreader.stats.ingest

import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderPageStatRow
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderSourceBook
import io.tarantini.shelf.integration.koreader.stats.domain.Md5Hash
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

interface KoreaderSqliteReader : AutoCloseable {
    fun listBooks(): List<KoreaderSourceBook>

    fun pageStatRowsForBook(bookId: Long): List<KoreaderPageStatRow>
}

fun openKoreaderSqlite(path: Path): KoreaderSqliteReader {
    Class.forName("org.sqlite.JDBC")
    val url = "jdbc:sqlite:file:${path.toAbsolutePath()}?mode=ro&immutable=1"
    val connection = DriverManager.getConnection(url)
    return JdbcKoreaderSqliteReader(connection)
}

private class JdbcKoreaderSqliteReader(private val connection: Connection) : KoreaderSqliteReader {
    override fun listBooks(): List<KoreaderSourceBook> {
        val sql =
            "SELECT id, title, authors, series, language, pages, md5, " +
                "total_read_time, total_read_pages, last_open " +
                "FROM book"
        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val books = mutableListOf<KoreaderSourceBook>()
                while (rs.next()) {
                    books +=
                        KoreaderSourceBook(
                            sourceId = rs.getLong("id"),
                            title = rs.getString("title") ?: "",
                            authors = rs.getString("authors"),
                            series = rs.getString("series"),
                            language = rs.getString("language"),
                            pages = rs.getOptionalInt("pages"),
                            md5 = Md5Hash.fromRawOrNull(rs.getString("md5")),
                            totalReadTime = rs.getOptionalLong("total_read_time"),
                            totalReadPages = rs.getOptionalInt("total_read_pages"),
                            lastOpen = rs.getOptionalLong("last_open"),
                        )
                }
                return books
            }
        }
    }

    override fun pageStatRowsForBook(bookId: Long): List<KoreaderPageStatRow> {
        val sql =
            "SELECT page, start_time, duration, total_pages " +
                "FROM page_stat_data " +
                "WHERE id_book = ? " +
                "ORDER BY start_time ASC"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, bookId)
            stmt.executeQuery().use { rs ->
                val rows = mutableListOf<KoreaderPageStatRow>()
                while (rs.next()) {
                    rows +=
                        KoreaderPageStatRow(
                            page = rs.getInt("page"),
                            startTimeEpoch = rs.getLong("start_time"),
                            durationSeconds = rs.getInt("duration"),
                            totalPagesAtRead = rs.getOptionalInt("total_pages"),
                        )
                }
                return rows
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun java.sql.ResultSet.getOptionalInt(column: String): Int? {
        val v = getInt(column)
        return if (wasNull()) null else v
    }

    private fun java.sql.ResultSet.getOptionalLong(column: String): Long? {
        val v = getLong(column)
        return if (wasNull()) null else v
    }
}
