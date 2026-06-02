package io.tarantini.shelf.integration.koreader

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val rehashLogger = KotlinLogging.logger {}

data class RehashResult(val total: Int, val updated: Int, val unchanged: Int, val skipped: Int)

private enum class RehashOutcome {
    SKIPPED,
    UNCHANGED,
    UPDATED,
}

interface KoreaderRehashService {
    suspend fun recomputeAllEbookHashes(): RehashResult
}

fun koreaderRehashService(
    metadataQueries: MetadataQueries,
    storageService: StorageService,
): KoreaderRehashService = DefaultKoreaderRehashService(metadataQueries, storageService)

private class DefaultKoreaderRehashService(
    private val metadataQueries: MetadataQueries,
    private val storageService: StorageService,
) : KoreaderRehashService {
    override suspend fun recomputeAllEbookHashes(): RehashResult =
        withContext(Dispatchers.IO) {
            val editions = metadataQueries.selectAllEbookEditions().executeAsList()
            var updated = 0
            var unchanged = 0
            var skipped = 0
            for (row in editions) {
                when (rehashOne(row.path, row.file_hash, row.id)) {
                    RehashOutcome.SKIPPED -> skipped += 1
                    RehashOutcome.UNCHANGED -> unchanged += 1
                    RehashOutcome.UPDATED -> updated += 1
                }
            }
            rehashLogger.info {
                "KOReader rehash run completed total=${editions.size} updated=$updated " +
                    "unchanged=$unchanged skipped=$skipped"
            }
            RehashResult(
                total = editions.size,
                updated = updated,
                unchanged = unchanged,
                skipped = skipped,
            )
        }

    private suspend fun rehashOne(
        storagePath: StoragePath,
        currentHash: String?,
        editionId: EditionId,
    ): RehashOutcome {
        val resolved = either { storageService.resolve(storagePath) }.getOrNull()
        if (resolved == null || !Files.exists(resolved)) return RehashOutcome.SKIPPED
        val newHash = resolved.koreaderHash() ?: return RehashOutcome.SKIPPED
        if (currentHash == newHash) return RehashOutcome.UNCHANGED
        metadataQueries.updateEditionFileHashById(newHash, editionId)
        metadataQueries.insertEditionFileHashHistory(editionId, newHash)
        return RehashOutcome.UPDATED
    }
}
