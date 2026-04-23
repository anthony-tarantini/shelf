package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.integration.podcast.PodcastCredentialService
import io.tarantini.shelf.integration.podcast.audible.*
import io.tarantini.shelf.integration.podcast.audio.FfmpegAdapter
import io.tarantini.shelf.integration.podcast.feed.ParsedEpisode
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

interface AudibleContentFetchService {
    /**
     * Syncs a specific Audible title (Audiobook or Podcast).
     */
    context(_: RaiseContext)
    suspend fun fetchAudibleContent(podcastId: PodcastId)
}

fun audibleContentFetchService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
    podcastQueries: PodcastQueries,
    bookQueries: BookQueries,
    metadataQueries: MetadataQueries,
    storageService: StorageService,
    credentialService: PodcastCredentialService,
    audibleAdapter: AudibleAdapter,
    ffmpegAdapter: FfmpegAdapter,
): AudibleContentFetchService =
    DefaultAudibleContentFetchService(
        readRepository = readRepository,
        mutationRepository = mutationRepository,
        podcastQueries = podcastQueries,
        bookQueries = bookQueries,
        metadataQueries = metadataQueries,
        storageService = storageService,
        credentialService = credentialService,
        audibleAdapter = audibleAdapter,
        ffmpegAdapter = ffmpegAdapter,
    )

private class DefaultAudibleContentFetchService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
    private val podcastQueries: PodcastQueries,
    private val bookQueries: BookQueries,
    private val metadataQueries: MetadataQueries,
    private val storageService: StorageService,
    private val credentialService: PodcastCredentialService,
    private val audibleAdapter: AudibleAdapter,
    private val ffmpegAdapter: FfmpegAdapter,
) : AudibleContentFetchService {
    @OptIn(ExperimentalUuidApi::class)
    context(_: RaiseContext)
    override suspend fun fetchAudibleContent(podcastId: PodcastId) {
        val podcast = mutationRepository.getPodcastById(podcastId)
        // For Audible, the feedUrl will be an Audible ASIN (e.g. audible://ASIN)
        val asin = podcast.feedUrl.value.removePrefix("audible://")
        
        val cookieCred = credentialService.getFeedCredentials(podcastId) as? io.tarantini.shelf.integration.podcast.feed.FeedFetchCredentials.AudibleCookie
            ?: return logger.warn { "No Audible cookies for podcast $podcastId" }
        
        val activationBytes = credentialService.getFeedCredentials(podcastId) as? io.tarantini.shelf.integration.podcast.feed.FeedFetchCredentials.AudibleActivationBytes
        
        val audibleCreds = AudibleCredentials(cookieCred.cookie, activationBytes?.bytes)
        val parsed = audibleAdapter.getPodcastFeed(asin, audibleCreds)

        var ingested = 0
        parsed.episodes.forEach { episode ->
            val result = either { ingestAudibleEpisode(podcastId, episode, audibleCreds) }
            result.fold(
                { err -> logger.warn { "Audible episode ingestion failed: $err" } },
                { inserted -> if (inserted) ingested += 1 }
            )
        }

        mutationRepository.markFetched(podcastId, Clock.System.now())
        if (ingested > 0) {
            mutationRepository.bumpVersion(podcastId)
        }
    }

    context(_: RaiseContext)
    private suspend fun ingestAudibleEpisode(
        podcastId: PodcastId, 
        episode: ParsedEpisode, 
        creds: AudibleCredentials
    ): Boolean {
        if (readRepository.guidExists(podcastId, episode.guid)) return false
        
        // TODO: Download encrypted file, decrypt via ffmpegAdapter, then save to storageService
        // This is a complex multi-step process that we'll implement in the next turn
        return false
    }
}
