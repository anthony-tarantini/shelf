package io.tarantini.shelf.user.activity.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReadingProgressKind {
    @SerialName("EBOOK") EBOOK,
    @SerialName("AUDIOBOOK") AUDIOBOOK,
}

@Serializable
data class ReadingProgress(
    val kind: ReadingProgressKind? = null,
    val cfi: String? = null,
    val positionSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val progressPercent: Double? = null,
) {
    fun normalized(): ReadingProgress =
        when {
            !cfi.isNullOrBlank() -> ReadingProgress(kind = ReadingProgressKind.EBOOK, cfi = cfi)
            positionSeconds != null || durationSeconds != null || progressPercent != null ->
                ReadingProgress(
                    kind = ReadingProgressKind.AUDIOBOOK,
                    positionSeconds = (positionSeconds ?: 0.0).coerceAtLeast(0.0),
                    durationSeconds = durationSeconds?.takeIf { it.isFinite() && it > 0.0 },
                    progressPercent = progressPercent?.coerceIn(0.0, 1.0),
                )
            else -> ReadingProgress(kind = kind)
        }

    fun mergeWith(existing: ReadingProgress?): ReadingProgress {
        val normalized = normalized()
        val current = existing ?: ReadingProgress()

        val hasEbookUpdate =
            normalized.kind == ReadingProgressKind.EBOOK || !normalized.cfi.isNullOrBlank()
        val hasAudiobookUpdate =
            normalized.kind == ReadingProgressKind.AUDIOBOOK ||
                normalized.positionSeconds != null ||
                normalized.durationSeconds != null ||
                normalized.progressPercent != null

        return current.copy(
            cfi = if (hasEbookUpdate) normalized.cfi else current.cfi,
            positionSeconds =
                if (hasAudiobookUpdate) normalized.positionSeconds else current.positionSeconds,
            durationSeconds =
                if (hasAudiobookUpdate) normalized.durationSeconds else current.durationSeconds,
            progressPercent =
                if (hasAudiobookUpdate) normalized.progressPercent else current.progressPercent,
        )
    }

    companion object {
        fun ebook(cfi: String) = ReadingProgress(kind = ReadingProgressKind.EBOOK, cfi = cfi)
    }
}

@Serializable
enum class ReadStatus {
    @SerialName("UNREAD") UNREAD,
    @SerialName("READING") READING,
    @SerialName("FINISHED") FINISHED,
    @SerialName("ABANDONED") ABANDONED,
    @SerialName("QUEUED") QUEUED,
}

@Serializable data class BookUserState(val readStatus: ReadStatus = ReadStatus.UNREAD)

@Serializable data class ReadStatusRequest(val status: ReadStatus)
