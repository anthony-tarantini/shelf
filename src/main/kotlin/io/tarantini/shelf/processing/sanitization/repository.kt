@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.processing.sanitization.domain.NewSanitizationJob
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobId
import io.tarantini.shelf.processing.sanitization.domain.SanitizationResult
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.sanitization.domain.SavedSanitizationJob
import io.tarantini.shelf.processing.sanitization.persistence.SanitizationQueries
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SanitizationMutationRepository {
    context(_: RaiseContext)
    suspend fun getJobById(id: SanitizationJobId): SavedSanitizationJob

    context(_: RaiseContext)
    suspend fun getJobByEditionId(editionId: EditionId): SavedSanitizationJob

    context(_: RaiseContext)
    suspend fun getJobsByBookId(bookId: BookId): List<SavedSanitizationJob>

    context(_: RaiseContext)
    suspend fun getPendingJobs(limit: Long): List<SavedSanitizationJob>

    context(_: RaiseContext)
    suspend fun createJob(job: NewSanitizationJob): SavedSanitizationJob

    context(_: RaiseContext)
    suspend fun updateStatus(
        id: SanitizationJobId,
        status: SanitizationStatus,
        errorMessage: String? = null,
    ): SavedSanitizationJob

    context(_: RaiseContext)
    suspend fun updateResult(
        id: SanitizationJobId,
        result: SanitizationResult,
    ): SavedSanitizationJob

    context(_: RaiseContext)
    suspend fun deleteJobsByBookId(bookId: BookId)
}

fun sanitizationMutationRepository(queries: SanitizationQueries): SanitizationMutationRepository =
    SqlDelightSanitizationMutationRepository(queries)

private class SqlDelightSanitizationMutationRepository(private val queries: SanitizationQueries) :
    SanitizationMutationRepository {
    context(_: RaiseContext)
    override suspend fun getJobById(id: SanitizationJobId): SavedSanitizationJob =
        withContext(Dispatchers.IO) { queries.getJobById(id) }

    context(_: RaiseContext)
    override suspend fun getJobByEditionId(editionId: EditionId): SavedSanitizationJob =
        withContext(Dispatchers.IO) { queries.getJobByEditionId(editionId) }

    context(_: RaiseContext)
    override suspend fun getJobsByBookId(bookId: BookId): List<SavedSanitizationJob> =
        withContext(Dispatchers.IO) { queries.getJobsByBookId(bookId) }

    context(_: RaiseContext)
    override suspend fun getPendingJobs(limit: Long): List<SavedSanitizationJob> =
        withContext(Dispatchers.IO) { queries.getPendingJobs(limit) }

    context(_: RaiseContext)
    override suspend fun createJob(job: NewSanitizationJob): SavedSanitizationJob =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                val id = queries.createJob(job)
                queries.getJobById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun updateStatus(
        id: SanitizationJobId,
        status: SanitizationStatus,
        errorMessage: String?,
    ): SavedSanitizationJob =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.updateJobStatus(id = id, status = status, errorMessage = errorMessage)
                queries.getJobById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun updateResult(
        id: SanitizationJobId,
        result: SanitizationResult,
    ): SavedSanitizationJob =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.updateJobResult(id = id, result = result)
                queries.getJobById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun deleteJobsByBookId(bookId: BookId) {
        withContext(Dispatchers.IO) { queries.deleteJobsByBookId(bookId) }
    }
}
