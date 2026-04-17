@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.activity

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.user.activity.domain.BookUserState
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.activity.persistence.ActivityQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.Json

private val activityJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

context(_: RaiseContext)
fun ActivityQueries.getReadingProgress(userId: UserId, bookId: BookId): ReadingProgress? =
    selectProgress(userId, bookId).executeAsOneOrNull()?.let { raw ->
        if (raw.trimStart().startsWith("{")) {
            activityJson.decodeFromString<ReadingProgress>(raw)
        } else {
            ReadingProgress.ebook(raw)
        }
    }

context(_: RaiseContext)
fun ActivityQueries.saveReadingProgress(userId: UserId, bookId: BookId, progress: ReadingProgress) {
    upsertProgress(userId, bookId, activityJson.encodeToString(progress))
}

context(_: RaiseContext)
fun ActivityQueries.getReadStatus(userId: UserId, bookId: BookId): ReadStatus =
    selectReadStatus(userId, bookId).executeAsOneOrNull()?.let(ReadStatus::valueOf)
        ?: ReadStatus.UNREAD

context(_: RaiseContext)
fun ActivityQueries.getReadStatuses(
    userId: UserId,
    bookIds: List<BookId>,
): Map<BookId, BookUserState> {
    if (bookIds.isEmpty()) return emptyMap()
    val stored =
        selectReadStatuses(userId, bookIds) { bookId, status ->
                bookId to BookUserState(ReadStatus.valueOf(status))
            }
            .executeAsList()
            .toMap()
    return bookIds.associateWith { stored[it] ?: BookUserState() }
}

context(_: RaiseContext)
fun ActivityQueries.saveReadStatus(userId: UserId, bookId: BookId, status: ReadStatus) {
    if (status == ReadStatus.UNREAD) {
        deleteReadStatus(userId, bookId)
    } else {
        upsertReadStatus(userId, bookId, status.name)
    }
}
