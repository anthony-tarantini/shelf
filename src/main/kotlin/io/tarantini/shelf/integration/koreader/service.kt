package io.tarantini.shelf.integration.koreader

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.integration.koreader.domain.KoReaderProgress
import io.tarantini.shelf.integration.koreader.domain.ProgressPayload
import io.tarantini.shelf.integration.koreader.persistence.KoreaderQueries
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface KoreaderSyncService {
    context(_: RaiseContext)
    suspend fun getProgress(userId: UserId, documentHash: String): KoReaderProgress?

    context(_: RaiseContext)
    suspend fun updateProgress(userId: UserId, payload: ProgressPayload)
}

fun koreaderSyncService(koreaderQueries: KoreaderQueries, metadataQueries: MetadataQueries) =
    object : KoreaderSyncService {
        context(_: RaiseContext)
        override suspend fun getProgress(userId: UserId, documentHash: String): KoReaderProgress? =
            withContext(Dispatchers.IO) {
                koreaderQueries.selectProgress(userId, documentHash).executeAsOneOrNull()?.let {
                    KoReaderProgress(
                        userId = it.user_id,
                        editionId = it.edition_id,
                        documentHash = it.document_hash,
                        progressData = it.progress_data,
                        updatedAt =
                            Instant.fromEpochMilliseconds(it.updated_at.toInstant().toEpochMilli()),
                    )
                }
            }

        context(_: RaiseContext)
        override suspend fun updateProgress(userId: UserId, payload: ProgressPayload) =
            withContext(Dispatchers.IO) {
                val edition =
                    metadataQueries.selectEditionByFileHash(payload.document).executeAsOneOrNull()
                        ?: return@withContext // Ignore if book not found in Shelf

                val progressJson = Json.encodeToString(payload)
                koreaderQueries.upsertProgress(
                    userId = userId,
                    editionId = edition.id,
                    documentHash = payload.document,
                    progressData = progressJson,
                )
            }
    }
