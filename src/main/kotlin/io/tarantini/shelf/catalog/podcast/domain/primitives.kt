@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.domain

import arrow.core.raise.catch
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.StringAdapter
import io.tarantini.shelf.app.StringValueClass
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import java.net.URI
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PodcastId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(podcastId: String?): PodcastId {
            ensureNotNull(podcastId) { EmptyPodcastId }
            ensure(podcastId.isNotEmpty()) { EmptyPodcastId }
            return PodcastId(ensureNotNull(Uuid.parseOrNull(podcastId)) { InvalidPodcastId })
        }

        fun fromRaw(value: Uuid) = PodcastId(value)

        fun fromRaw(value: UUID) = PodcastId(value.toKotlinUuid())

        fun fromRaw(value: String) = PodcastId(Uuid.parse(value))

        val adapter = object : UuidAdapter<PodcastId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class PodcastEpisodeId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): PodcastEpisodeId {
            ensureNotNull(raw) { InvalidEpisodeIndex }
            ensure(raw.isNotEmpty()) { InvalidEpisodeIndex }
            return PodcastEpisodeId(ensureNotNull(Uuid.parseOrNull(raw)) { InvalidEpisodeIndex })
        }

        fun fromRaw(value: Uuid) = PodcastEpisodeId(value)

        fun fromRaw(value: UUID) = PodcastEpisodeId(value.toKotlinUuid())

        fun fromRaw(value: String) = PodcastEpisodeId(Uuid.parse(value))

        val adapter = object : UuidAdapter<PodcastEpisodeId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class FeedUrl private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): FeedUrl {
            ensureNotNull(raw) { EmptyFeedUrl }
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyFeedUrl }

            val uri = catch({ URI(normalized) }) { raise(InvalidFeedUrl) }
            ensure(uri.scheme?.lowercase() in listOf("https", "http")) { InvalidFeedUrl }
            ensure(!uri.host.isNullOrBlank()) { InvalidFeedUrl }
            return FeedUrl(normalized)
        }

        fun fromRaw(raw: String) = FeedUrl(raw)

        val adapter = object : StringAdapter<FeedUrl>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class FeedToken private constructor(override val value: String) : StringValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String?): FeedToken {
            ensureNotNull(raw) { InvalidFeedToken }
            ensure(raw.isNotBlank()) { InvalidFeedToken }
            ensure(Uuid.parseOrNull(raw) != null) { InvalidFeedToken }
            return FeedToken(raw)
        }

        fun generate(): FeedToken = FeedToken(Uuid.random().toString())

        fun fromRaw(raw: String) = FeedToken(raw)

        val adapter = object : StringAdapter<FeedToken>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class Season private constructor(val value: Int) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Int?): Season {
            val season = raw ?: 0
            ensure(season in 0..999) { InvalidEpisodeIndex }
            return Season(season)
        }

        fun fromRaw(value: Int) = Season(value)
    }
}

@JvmInline
@Serializable
value class EpisodeNumber private constructor(val value: Int) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Int?): EpisodeNumber {
            ensureNotNull(raw) { InvalidEpisodeIndex }
            ensure(raw in 0..99999) { InvalidEpisodeIndex }
            return EpisodeNumber(raw)
        }

        fun fromRaw(value: Int) = EpisodeNumber(value)
    }
}
