package io.tarantini.shelf.user.activity

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.activity.persistence.ActivityQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ActivityMutationRepository {
    context(_: RaiseContext)
    suspend fun getReadingProgress(userId: UserId, bookId: BookId): ReadingProgress?

    context(_: RaiseContext)
    suspend fun saveReadingProgress(userId: UserId, bookId: BookId, progress: ReadingProgress)

    context(_: RaiseContext)
    suspend fun saveReadStatus(userId: UserId, bookId: BookId, status: ReadStatus)
}

fun activityMutationRepository(queries: ActivityQueries): ActivityMutationRepository =
    SqlDelightActivityMutationRepository(queries)

private class SqlDelightActivityMutationRepository(private val queries: ActivityQueries) :
    ActivityMutationRepository {
    context(_: RaiseContext)
    override suspend fun getReadingProgress(userId: UserId, bookId: BookId): ReadingProgress? =
        withContext(Dispatchers.IO) { queries.getReadingProgress(userId, bookId) }

    context(_: RaiseContext)
    override suspend fun saveReadingProgress(
        userId: UserId,
        bookId: BookId,
        progress: ReadingProgress,
    ) {
        withContext(Dispatchers.IO) { queries.saveReadingProgress(userId, bookId, progress) }
    }

    context(_: RaiseContext)
    override suspend fun saveReadStatus(userId: UserId, bookId: BookId, status: ReadStatus) {
        withContext(Dispatchers.IO) { queries.saveReadStatus(userId, bookId, status) }
    }
}
