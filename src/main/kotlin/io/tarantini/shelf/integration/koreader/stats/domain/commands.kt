package io.tarantini.shelf.integration.koreader.stats.domain

import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Path
import kotlin.time.Instant

data class IngestKoreaderStatsCommand(val userId: UserId, val sourcePath: Path)

data class KoreaderStatsDateRange(val from: Instant, val to: Instant)

data class KoreaderSourceBook(
    val sourceId: Long,
    val title: String,
    val authors: String?,
    val series: String?,
    val language: String?,
    val pages: Int?,
    val md5: Md5Hash?,
    val totalReadTime: Long?,
    val totalReadPages: Int?,
    val lastOpen: Long?,
)
