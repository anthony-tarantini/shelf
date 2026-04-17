package io.tarantini.shelf.integration.koreader.domain

import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Instant
import kotlinx.serialization.SerialName
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
    val document: String?,
    val percentage: Double?,
    val progress: String?,
    val device: String?,
    @SerialName("device_id") val deviceId: String?,
)
