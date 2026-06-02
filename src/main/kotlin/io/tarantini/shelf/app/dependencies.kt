package io.tarantini.shelf.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import arrow.fx.coroutines.ResourceScope
import com.apollographql.apollo.ApolloClient
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.Database
import io.tarantini.shelf.catalog.author.AuthorService
import io.tarantini.shelf.catalog.author.authorService
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.catalog.book.bookService
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.MetadataService
import io.tarantini.shelf.catalog.metadata.metadataProcessor
import io.tarantini.shelf.catalog.metadata.metadataRepository
import io.tarantini.shelf.catalog.metadata.metadataService
import io.tarantini.shelf.catalog.opds.OpdsService
import io.tarantini.shelf.catalog.opds.opdsService
import io.tarantini.shelf.catalog.podcast.PodcastService
import io.tarantini.shelf.catalog.podcast.podcastFeedFetchService
import io.tarantini.shelf.catalog.podcast.podcastService
import io.tarantini.shelf.catalog.podcast.rss.PodcastRssService
import io.tarantini.shelf.catalog.podcast.rss.podcastRssService
import io.tarantini.shelf.catalog.search.SearchService
import io.tarantini.shelf.catalog.search.searchService
import io.tarantini.shelf.catalog.series.SeriesService
import io.tarantini.shelf.catalog.series.seriesService
import io.tarantini.shelf.integration.core.ExternalMetadataProvider
import io.tarantini.shelf.integration.hardcover.hardcover
import io.tarantini.shelf.integration.koreader.KoreaderAuthService
import io.tarantini.shelf.integration.koreader.KoreaderSyncService
import io.tarantini.shelf.integration.koreader.koreaderAuthService
import io.tarantini.shelf.integration.koreader.koreaderSyncService
import io.tarantini.shelf.integration.koreader.stats.KoreaderStatsService
import io.tarantini.shelf.integration.koreader.stats.koreaderStatsRepository
import io.tarantini.shelf.integration.koreader.stats.koreaderStatsService
import io.tarantini.shelf.integration.podcast.feed.episodeAudioFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.episodeImageFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.feedFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.feedParser
import io.tarantini.shelf.integration.podcast.libation.LibationManifestParser
import io.tarantini.shelf.integration.podcast.libation.LibationScanner
import io.tarantini.shelf.integration.podcast.podcastCredentialService
import io.tarantini.shelf.integration.security.EncryptionService
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.observability.ObservabilityConfig
import io.tarantini.shelf.observability.observability
import io.tarantini.shelf.organization.library.LibraryService
import io.tarantini.shelf.organization.library.libraryService
import io.tarantini.shelf.organization.settings.SettingsService
import io.tarantini.shelf.organization.settings.settingsService
import io.tarantini.shelf.processing.audiobook.audiobookParser
import io.tarantini.shelf.processing.epub.epubParser
import io.tarantini.shelf.processing.epub.epubWriter
import io.tarantini.shelf.processing.import.ImportService
import io.tarantini.shelf.processing.import.importService
import io.tarantini.shelf.processing.import.staging.StagedBookService
import io.tarantini.shelf.processing.import.staging.inMemoryStagedBookStore
import io.tarantini.shelf.processing.import.staging.stagedBookService
import io.tarantini.shelf.processing.import.staging.valkeyStagedBookStore
import io.tarantini.shelf.processing.jobs.*
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.processing.storage.localStorageService
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.activity.activityService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtService
import io.tarantini.shelf.user.identity.TokenService
import io.tarantini.shelf.user.identity.UserService
import io.tarantini.shelf.user.identity.userService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel

private val logger = KotlinLogging.logger {}

@Suppress("LongParameterList")
class Dependencies(
    val healthCheck: HealthCheckRegistry,
    val userService: UserService,
    val tokenService: TokenService,
    val authorService: AuthorService,
    val bookService: BookService,
    val metadataService: MetadataService,
    val seriesService: SeriesService,
    val searchService: SearchService,
    val podcastService: PodcastService,
    val podcastRssService: PodcastRssService,
    val podcastUpstreamFeedService:
        io.tarantini.shelf.catalog.podcast.upstream.PodcastUpstreamFeedService,
    val podcastMappingService: io.tarantini.shelf.catalog.podcast.upstream.PodcastMappingService,
    val podcastReadRepository: io.tarantini.shelf.catalog.podcast.PodcastReadRepository,
    val libraryService: LibraryService,
    val storageService: StorageService,
    val activityService: ActivityService,
    val importService: ImportService,
    val stagedBookService: StagedBookService,
    val koreaderSyncService: KoreaderSyncService,
    val koreaderAuthService: KoreaderAuthService,
    val koreaderStatsService: KoreaderStatsService,
    val opdsService: OpdsService,
    val podcastLibationService: io.tarantini.shelf.catalog.podcast.PodcastLibationService,
    val minusPodAdapter: io.tarantini.shelf.integration.podcast.sanitization.MinusPodAdapter,
    val jwtService: JwtService,
    val authCache: AuthCache,
    val storagePath: String,
    val database: Database,
    val sqlDriver: SqlDriver,
    val env: Env,
    val externalMetadataProvider: ExternalMetadataProvider,
    val observability: Observability,
    val settingsService: SettingsService,
    val jobQueue: JobQueue,
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
    val hikari = hikari(env.dataSource)
    val sqlDelight = sqlDelight(hikari)
    val checks =
        HealthCheckRegistry(Dispatchers.Default) {
            register(HikariConnectionsHealthCheck(hikari, 1))
        }
    val jwtService = jwtService(env.auth)
    val observability =
        observability(
            ObservabilityConfig(
                enabled = env.observability.enabled,
                serviceName = env.observability.serviceName,
                environment = env.observability.environment,
                tracingEnabled = env.observability.tracingEnabled,
                traceOwner = env.observability.traceOwner,
                metricsEnabled = env.observability.metricsEnabled,
                metricsPath = env.observability.metricsPath,
                metricsPrometheusEnabled = env.observability.metricsPrometheusEnabled,
                metricsOtlpEnabled = env.observability.metricsOtlpEnabled,
                otlpEndpoint = env.observability.otlpEndpoint,
                otlpProtocol = env.observability.otlpProtocol,
                samplingRatio = env.observability.samplingRatio,
                jsonLogsEnabled = env.observability.jsonLogsEnabled,
            )
        )
    onRelease { observability.close() }

    with(sqlDelight) {
        val userService = userService(userQueries, jwtService)
        val tokenService = io.tarantini.shelf.user.identity.tokenService(tokensQueries)
        val storagePath = env.storage.path
        val storageService = localStorageService(storagePath, observability)
        val settingsService = settingsService(settingsQueries, observability)
        val seriesService = seriesService(seriesQueries, bookQueries, storageService)
        val authorService = authorService(hikari.asJdbcDriver(), authorQueries, bookQueries)
        val epubParser = epubParser()
        val epubWriter = epubWriter()
        val audiobookParser = audiobookParser()
        val metadataProcessor = metadataProcessor(epubParser, audiobookParser)
        val hardcoverApolloClient by lazy {
            ApolloClient.Builder()
                .serverUrl(env.hardcover.url)
                .addHttpHeader("authorization", "Bearer ${env.hardcover.apiKey}")
                .build()
        }
        val externalMetadataProvider = hardcover(hardcoverApolloClient)
        val metadataRepository = metadataRepository(metadataQueries)
        val metadataSyncStatusRepository = metadataSyncStatusRepository(metadataSyncStatusQueries)
        val koreaderStatsRepository = koreaderStatsRepository(koreaderStatsQueries)
        val koreaderStatsService = koreaderStatsService(koreaderStatsRepository, metadataRepository)
        val metadataService =
            metadataService(
                externalMetadataProvider,
                metadataProcessor,
                metadataRepository,
                relinkPort = koreaderStatsService,
            )

        val valkeyUrl = env.valkey.url
        var workerValkeyConnection: StatefulRedisConnection<String, String>? = null
        var inMemoryChannel: Channel<BookId>? = null
        var inMemoryPodcastChannel: Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>? =
            null
        var inMemoryBackfillCoversChannel:
            Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>? =
            null

        val (stagedStore, authCache, jobQueue) =
            if (valkeyUrl != null) {
                logger.info { "Valkey detected at $valkeyUrl, initializing distributed stores." }
                val redisClient = RedisClient.create(valkeyUrl)
                onRelease { redisClient.shutdown() }
                val appConnection = redisClient.connect()
                onRelease { appConnection.close() }
                val workerConnection = redisClient.connect()
                onRelease { workerConnection.close() }
                workerValkeyConnection = workerConnection

                Triple(
                    valkeyStagedBookStore(appConnection),
                    ValkeyAuthCache(appConnection),
                    ValkeyJobQueue(appConnection),
                )
            } else {
                logger.info { "Valkey not configured, falling back to in-memory stores." }
                val metadataChannel = Channel<BookId>(Channel.UNLIMITED)
                val podcastChannel =
                    Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>(Channel.UNLIMITED)
                val backfillCoversChannel =
                    Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>(Channel.UNLIMITED)
                inMemoryChannel = metadataChannel
                inMemoryPodcastChannel = podcastChannel
                inMemoryBackfillCoversChannel = backfillCoversChannel

                Triple(
                    inMemoryStagedBookStore(),
                    InMemoryAuthCache(),
                    InMemoryJobQueue(metadataChannel, podcastChannel, backfillCoversChannel),
                )
            }

        val bookService =
            bookService(
                bookQueries,
                authorService,
                seriesService,
                metadataService,
                authorQueries,
                seriesQueries,
                storageService,
                metadataRepository,
                settingsService,
                jobQueue,
                metadataSyncStatusRepository,
            )
        val libraryService = libraryService(libraryQueries, bookQueries)
        val searchService = searchService(bookQueries, authorQueries, seriesQueries)

        if (env.integration.encryptionSecret == "insecure-local-default-change-me") {
            logger.warn {
                "ENCRYPTION_SECRET is not set — using insecure default. Set ENCRYPTION_SECRET for production."
            }
        }
        if (env.integration.minuspodAdminPassword == null) {
            logger.warn {
                "MINUSPOD_ADMIN_PASSWORD is not set — MinusPod requests will not include auth."
            }
        }
        val encryptionService = EncryptionService(env.integration.encryptionSecret)
        val credentialService = podcastCredentialService(credentialsQueries, encryptionService)
        val minusPodAdapter =
            io.tarantini.shelf.integration.podcast.sanitization.minusPodAdapter(
                env.integration.minuspodUrl,
                env.integration.minuspodAdminPassword,
            )
        val podcastReadRepository =
            io.tarantini.shelf.catalog.podcast.podcastReadRepository(
                podcastQueries,
                credentialsQueries,
            )
        val podcastMutationRepository =
            io.tarantini.shelf.catalog.podcast.podcastMutationRepository(podcastQueries)

        val podcastMappingService =
            io.tarantini.shelf.catalog.podcast.upstream.podcastMappingService(
                readRepository = podcastReadRepository,
                mutationRepository = podcastMutationRepository,
            )

        val podcastLibationService =
            io.tarantini.shelf.catalog.podcast.podcastLibationService(
                enabled = env.integration.libationImportEnabled,
                dropDirectory = env.integration.libationDropDir,
                scanner = LibationScanner(LibationManifestParser()),
                libationImportQueries = libationImportQueries,
                seriesQueries = seriesQueries,
                podcastQueries = podcastQueries,
                storageService = storageService,
                mappingService = podcastMappingService,
            )

        val podcastService =
            podcastService(
                readRepository = podcastReadRepository,
                mutationRepository = podcastMutationRepository,
                credentialService = credentialService,
                podcastQueries = podcastQueries,
                storageService = storageService,
            )

        val sharedFeedFetchAdapter = feedFetchAdapter()
        val sharedFeedParser = feedParser()

        val podcastFeedFetchService =
            podcastFeedFetchService(
                readRepository = podcastReadRepository,
                mutationRepository = podcastMutationRepository,
                podcastQueries = podcastQueries,
                storageService = storageService,
                credentialService = credentialService,
                feedFetchAdapter = sharedFeedFetchAdapter,
                feedParser = sharedFeedParser,
                audioFetchAdapter = episodeAudioFetchAdapter(),
                imageFetchAdapter = episodeImageFetchAdapter(),
            )

        val podcastUpstreamFeedService =
            io.tarantini.shelf.catalog.podcast.upstream.podcastUpstreamFeedService(
                readRepository = podcastReadRepository,
                mutationRepository = podcastMutationRepository,
                credentialService = credentialService,
                feedFetchAdapter = sharedFeedFetchAdapter,
                feedParser = sharedFeedParser,
            )

        val podcastRssService =
            podcastRssService(
                readRepository = podcastReadRepository,
                podcastQueries = podcastQueries,
                storageService = storageService,
                publicRootUrl = env.http.publicRootUrl,
            )

        val activityService = activityService(activityQueries)
        val koreaderSyncService = koreaderSyncService(koreaderQueries, metadataRepository)
        val koreaderAuthService = koreaderAuthService(koreaderQueries, userService, tokenService)

        // Create a managed CoroutineScope for background workers
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        onRelease { job.cancel() }

        val importService =
            importService(
                storageService,
                metadataProcessor,
                stagedStore,
                env.storage.scanRoots,
                scope,
                observability,
            )

        val stagedBookService =
            stagedBookService(
                stagedStore,
                storageService,
                bookQueries,
                authorQueries,
                seriesQueries,
                metadataRepository,
                scope,
                sqlDelight,
            )

        val rootUrl = env.http.publicRootUrl
        val opdsService =
            opdsService(
                bookService,
                bookService,
                authorService,
                seriesService,
                searchService,
                baseUrl = "$rootUrl/api/opds/v1.2",
                rootUrl = rootUrl,
            )

        // Start background worker
        val worker =
            SyncMetadataWorker(
                scope = scope,
                bookAggregateProvider = bookService,
                metadataRepository = metadataRepository,
                epubWriter = epubWriter,
                storageService = storageService,
                valkeyConnection = workerValkeyConnection,
                inMemoryChannel = inMemoryChannel,
                metadataSyncStatusRepository = metadataSyncStatusRepository,
            )
        worker.start()

        startPodcastWorkers(
            scope = scope,
            feedFetchService = podcastFeedFetchService,
            jobQueue = jobQueue,
            workerValkeyConnection = workerValkeyConnection,
            inMemoryPodcastChannel = inMemoryPodcastChannel,
            inMemoryBackfillCoversChannel = inMemoryBackfillCoversChannel,
            scheduleIntervalSeconds = env.integration.podcastScheduleIntervalSeconds,
        )

        val libationScheduler =
            LibationScanScheduler(
                scope = scope,
                libationService = podcastLibationService,
                intervalSeconds = env.integration.libationScanIntervalSeconds,
            )
        libationScheduler.start()

        return Dependencies(
            checks,
            userService,
            tokenService,
            authorService,
            bookService,
            metadataService,
            seriesService,
            searchService,
            podcastService,
            podcastRssService,
            podcastUpstreamFeedService,
            podcastMappingService,
            podcastReadRepository,
            libraryService,
            storageService,
            activityService,
            importService,
            stagedBookService,
            koreaderSyncService,
            koreaderAuthService,
            koreaderStatsService,
            opdsService,
            podcastLibationService,
            minusPodAdapter,
            jwtService,
            authCache,
            storagePath,
            sqlDelight,
            hikari.asJdbcDriver(),
            env,
            externalMetadataProvider,
            observability,
            settingsService,
            jobQueue,
        )
    }
}

private fun startPodcastWorkers(
    scope: kotlinx.coroutines.CoroutineScope,
    feedFetchService: io.tarantini.shelf.catalog.podcast.PodcastFeedFetchService,
    jobQueue: io.tarantini.shelf.processing.jobs.JobQueue,
    workerValkeyConnection: StatefulRedisConnection<String, String>?,
    inMemoryPodcastChannel: Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>?,
    inMemoryBackfillCoversChannel: Channel<io.tarantini.shelf.catalog.podcast.domain.PodcastId>?,
    scheduleIntervalSeconds: Long,
) {
    PodcastFeedWorker(
            scope = scope,
            feedFetchService = feedFetchService,
            valkeyConnection = workerValkeyConnection,
            inMemoryChannel = inMemoryPodcastChannel,
        )
        .start()
    io.tarantini.shelf.processing.jobs
        .PodcastCoverBackfillWorker(
            scope = scope,
            feedFetchService = feedFetchService,
            valkeyConnection = workerValkeyConnection,
            inMemoryChannel = inMemoryBackfillCoversChannel,
        )
        .start()
    PodcastFeedScheduler(
            scope = scope,
            feedFetchService = feedFetchService,
            jobQueue = jobQueue,
            intervalSeconds = scheduleIntervalSeconds,
        )
        .start()
}
