package io.tarantini.shelf.integration.koreader

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookNotFound
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.integration.koreader.domain.KoReaderProgress
import io.tarantini.shelf.integration.koreader.domain.KoreaderProgressReadCommand
import io.tarantini.shelf.integration.koreader.domain.KoreaderProgressUpdateCommand
import io.tarantini.shelf.integration.koreader.persistence.KoreaderQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface KoreaderSyncService {
    context(_: RaiseContext)
    suspend fun getProgress(userId: UserId, command: KoreaderProgressReadCommand): KoReaderProgress?

    context(_: RaiseContext)
    suspend fun updateProgress(userId: UserId, command: KoreaderProgressUpdateCommand)
}

fun koreaderSyncService(koreaderQueries: KoreaderQueries, repository: MetadataRepository) =
    object : KoreaderSyncService {
        context(_: RaiseContext)
        override suspend fun getProgress(
            userId: UserId,
            command: KoreaderProgressReadCommand,
        ): KoReaderProgress? =
            withContext(Dispatchers.IO) {
                koreaderQueries
                    .selectProgress(userId, command.documentId)
                    .executeAsOneOrNull()
                    ?.let {
                        KoReaderProgress(
                            userId = it.user_id,
                            editionId = it.edition_id,
                            documentHash = it.document_hash,
                            progressData = it.progress_data,
                            updatedAt =
                                Instant.fromEpochMilliseconds(
                                    it.updated_at.toInstant().toEpochMilli()
                                ),
                        )
                    }
            }

        context(_: RaiseContext)
        override suspend fun updateProgress(
            userId: UserId,
            command: KoreaderProgressUpdateCommand,
        ) =
            withContext(Dispatchers.IO) {
                val payload = command.payload
                val edition =
                    repository.selectEditionByFileHash(payload.document ?: "")
                        ?: raise(BookNotFound)

                val progressJson = Json.encodeToString(payload)
                koreaderQueries.upsertProgress(
                    userId = userId,
                    editionId = edition.id.id,
                    documentHash = payload.document ?: "",
                    progressData = progressJson,
                )
                Unit
            }
    }
