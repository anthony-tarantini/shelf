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
import io.tarantini.shelf.observability.Observability
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
    val libraryService: LibraryService,
    val storageService: StorageService,
    val activityService: ActivityService,
    val importService: ImportService,
    val stagedBookService: StagedBookService,
    val koreaderSyncService: KoreaderSyncService,
    val koreaderAuthService: KoreaderAuthService,
    val opdsService: OpdsService,
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
            io.tarantini.shelf.observability.ObservabilityConfig(
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
        val metadataService =
            metadataService(externalMetadataProvider, metadataProcessor, metadataRepository)

        val valkeyUrl = env.valkey.url
        var workerValkeyConnection: StatefulRedisConnection<String, String>? = null
        var inMemoryChannel: Channel<BookId>? = null

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
                val channel = Channel<BookId>(Channel.UNLIMITED)
                inMemoryChannel = channel

                Triple(inMemoryStagedBookStore(), InMemoryAuthCache(), InMemoryJobQueue(channel))
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
            )
        val libraryService = libraryService(libraryQueries, bookQueries)
        val searchService = searchService(bookQueries, authorQueries, seriesQueries)
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
                epubWriter = epubWriter,
                storageService = storageService,
                valkeyConnection = workerValkeyConnection,
                inMemoryChannel = inMemoryChannel,
            )
        worker.start()

        return Dependencies(
            checks,
            userService,
            tokenService,
            authorService,
            bookService,
            metadataService,
            seriesService,
            searchService,
            libraryService,
            storageService,
            activityService,
            importService,
            stagedBookService,
            koreaderSyncService,
            koreaderAuthService,
            opdsService,
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
