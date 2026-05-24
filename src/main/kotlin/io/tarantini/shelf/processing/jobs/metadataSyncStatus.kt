package io.tarantini.shelf.processing.jobs

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.processing.jobs.persistence.MetadataSyncStatusQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
enum class MetadataSyncState {
    NONE,
    PENDING,
    SUCCEEDED,
    FAILED;

    companion object {
        fun fromRaw(raw: String): MetadataSyncState? = entries.find { it.name == raw }
    }
}

data class MetadataSyncStatus(
    val bookId: BookId,
    val status: MetadataSyncState,
    val errorMessage: String?,
    val updatedAtMs: Long,
)

interface MetadataSyncStatusRepository {
    context(_: RaiseContext)
    suspend fun get(bookId: BookId): MetadataSyncStatus?

    context(_: RaiseContext)
    suspend fun markPending(bookId: BookId)

    context(_: RaiseContext)
    suspend fun markSucceeded(bookId: BookId)

    context(_: RaiseContext)
    suspend fun markFailed(bookId: BookId, errorMessage: String?)
}

fun metadataSyncStatusRepository(queries: MetadataSyncStatusQueries): MetadataSyncStatusRepository =
    object : MetadataSyncStatusRepository {
        context(_: RaiseContext)
        override suspend fun get(bookId: BookId): MetadataSyncStatus? =
            withContext(Dispatchers.IO) {
                queries.selectMetadataSyncStatusByBookId(bookId).executeAsOneOrNull()?.let { row ->
                    val status = MetadataSyncState.fromRaw(row.status) ?: MetadataSyncState.FAILED
                    MetadataSyncStatus(
                        bookId = row.book_id,
                        status = status,
                        errorMessage = row.error_message,
                        updatedAtMs = row.updated_at_ms,
                    )
                }
            }

        context(_: RaiseContext)
        override suspend fun markPending(bookId: BookId) =
            upsert(bookId, MetadataSyncState.PENDING, null)

        context(_: RaiseContext)
        override suspend fun markSucceeded(bookId: BookId) =
            upsert(bookId, MetadataSyncState.SUCCEEDED, null)

        context(_: RaiseContext)
        override suspend fun markFailed(bookId: BookId, errorMessage: String?) =
            upsert(bookId, MetadataSyncState.FAILED, errorMessage)

        private suspend fun upsert(
            bookId: BookId,
            status: MetadataSyncState,
            errorMessage: String?,
        ) {
            withContext(Dispatchers.IO) {
                queries.upsertMetadataSyncStatus(
                    bookId = bookId,
                    status = status.name,
                    errorMessage = errorMessage,
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        }
    }
