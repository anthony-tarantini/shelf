package io.tarantini.shelf.user.activity

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.domain.SavedBookAggregate
import io.tarantini.shelf.user.activity.domain.ActivityMutationDecider
import io.tarantini.shelf.user.activity.domain.BookUserState
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.activity.domain.SaveReadStatusCommand
import io.tarantini.shelf.user.activity.domain.SaveReadingProgressCommand
import io.tarantini.shelf.user.activity.persistence.ActivityQueries
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.identity.domain.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ActivityService {
    context(_: RaiseContext)
    suspend fun getProgress(userId: UserId, bookId: BookId): ReadingProgress?

    context(_: RaiseContext)
    suspend fun saveProgress(userId: UserId, bookId: BookId, command: SaveReadingProgressCommand)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getReadStatus(bookId: BookId): ReadStatus

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getReadStatuses(bookIds: List<BookId>): Map<BookId, BookUserState>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun saveReadStatus(bookId: BookId, command: SaveReadStatusCommand)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun enrichBookAggregate(book: SavedBookAggregate): SavedBookAggregate

    context(_: RaiseContext, auth: JwtContext)
    suspend fun enrichBookAggregates(books: List<SavedBookAggregate>): List<SavedBookAggregate>

    context(_: RaiseContext, auth: JwtContext)
    suspend fun enrichBookSummaries(books: List<BookSummary>): List<BookSummary>
}

fun activityService(activityQueries: ActivityQueries): ActivityService =
    UserActivityService(activityQueries)

private class UserActivityService(private val activityQueries: ActivityQueries) : ActivityService {
    context(_: RaiseContext)
    override suspend fun getProgress(userId: UserId, bookId: BookId) =
        withContext(Dispatchers.IO) { activityQueries.getReadingProgress(userId, bookId) }

    context(_: RaiseContext)
    override suspend fun saveProgress(
        userId: UserId,
        bookId: BookId,
        command: SaveReadingProgressCommand,
    ) {
        withContext(Dispatchers.IO) {
            val existing = activityQueries.getReadingProgress(userId, bookId)
            val merged = ActivityMutationDecider.decideProgressUpdate(existing, command)
            activityQueries.saveReadingProgress(userId, bookId, merged)
        }
    }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun getReadStatus(bookId: BookId): ReadStatus =
        withContext(Dispatchers.IO) { activityQueries.getReadStatus(auth.userId, bookId) }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun getReadStatuses(bookIds: List<BookId>): Map<BookId, BookUserState> =
        withContext(Dispatchers.IO) { activityQueries.getReadStatuses(auth.userId, bookIds) }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun saveReadStatus(bookId: BookId, command: SaveReadStatusCommand) {
        withContext(Dispatchers.IO) {
            val decision = ActivityMutationDecider.decideReadStatusUpdate(command)
            activityQueries.saveReadStatus(auth.userId, bookId, decision)
        }
    }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun enrichBookAggregate(book: SavedBookAggregate): SavedBookAggregate =
        book.copy(
            userState = getReadStatuses(listOf(book.book.id.id))[book.book.id.id] ?: BookUserState()
        )

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun enrichBookAggregates(
        books: List<SavedBookAggregate>
    ): List<SavedBookAggregate> {
        val statuses = getReadStatuses(books.map { it.book.id.id })
        return books.map { it.copy(userState = statuses[it.book.id.id] ?: BookUserState()) }
    }

    context(_: RaiseContext, auth: JwtContext)
    override suspend fun enrichBookSummaries(books: List<BookSummary>): List<BookSummary> {
        val statuses = getReadStatuses(books.map { it.id })
        return books.map { it.copy(userState = statuses[it.id] ?: BookUserState()) }
    }
}
