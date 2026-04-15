package io.tarantini.shelf.integration.koreader.domain

import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class KoReaderProgress(
    val userId: UserId,
    val editionId: EditionId,
    val documentHash: String,
    val progressData: String, // Storing the raw JSON payload
    val updatedAt: Instant,
)

@Serializable
data class ProgressPayload(
    val document: String,
    val progress: String,
    val device: String,
    val device_id: String,
    val timestamp: Long,
)
