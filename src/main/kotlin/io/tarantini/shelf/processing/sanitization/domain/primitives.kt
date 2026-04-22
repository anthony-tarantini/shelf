@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization.domain

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import java.util.UUID
import kotlin.ConsistentCopyVisibility
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SanitizationJobId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(jobId: String?): SanitizationJobId {
            ensureNotNull(jobId) { EmptySanitizationJobId }
            ensure(jobId.isNotEmpty()) { EmptySanitizationJobId }
            return SanitizationJobId(
                ensureNotNull(Uuid.parseOrNull(jobId)) { InvalidSanitizationJobId }
            )
        }

        fun fromRaw(value: Uuid) = SanitizationJobId(value)

        fun fromRaw(value: UUID) = SanitizationJobId(value.toKotlinUuid())

        fun fromRaw(value: String) = SanitizationJobId(Uuid.parse(value))

        val adapter = object : UuidAdapter<SanitizationJobId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class AudioTimestamp private constructor(val seconds: Double) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Double?): AudioTimestamp {
            ensureNotNull(raw) { InvalidAudioTimestamp }
            ensure(raw.isFinite()) { InvalidAudioTimestamp }
            ensure(raw >= 0.0) { InvalidAudioTimestamp }
            return AudioTimestamp(raw)
        }

        fun fromRaw(value: Double) = AudioTimestamp(value)
    }
}

@ConsistentCopyVisibility
@Serializable
data class AdSegment
private constructor(val start: AudioTimestamp, val end: AudioTimestamp, val confidence: Double) {
    val durationSeconds: Double = end.seconds - start.seconds

    companion object {
        context(_: RaiseContext)
        operator fun invoke(
            start: AudioTimestamp,
            end: AudioTimestamp,
            confidence: Double,
        ): AdSegment {
            ensure(end.seconds > start.seconds) { InvalidAdSegment }
            ensure(confidence.isFinite()) { InvalidAdSegment }
            ensure(confidence in 0.0..1.0) { InvalidAdSegment }
            return AdSegment(start, end, confidence)
        }
    }
}
