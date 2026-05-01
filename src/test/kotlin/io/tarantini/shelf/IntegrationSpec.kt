package io.tarantini.shelf

import arrow.fx.coroutines.resourceScope
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.tarantini.shelf.app.Dependencies
import io.tarantini.shelf.app.Env
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.dependencies
import io.tarantini.shelf.user.identity.domain.UserRequest
import io.tarantini.shelf.user.identity.domain.UserWithToken
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json

abstract class IntegrationSpec(body: IntegrationSpec.() -> Unit = {}) : StringSpec() {
    val tempStorageRoot = Files.createTempDirectory("shelf-test-storage-")
    private val testEnv =
        Env(
            dataSource =
                Env.DataSource(
                    url = ProjectConfig.postgres.jdbcUrl,
                    username = ProjectConfig.postgres.username,
                    password = ProjectConfig.postgres.password,
                    driver = "org.postgresql.Driver",
                ),
            http = Env.Http("localhost", 8080, listOf("localhost:5173"), "http://localhost:8080"),
            auth = Env.Auth("test-secret", "Test", 30.days),
            hardcover = Env.Hardcover("http://localhost:8080/graphql", "test-key"),
            storage =
                Env.Storage(
                    tempStorageRoot.absolutePathString(),
                    listOf(tempStorageRoot.absolutePathString()),
                ),
            valkey = Env.Valkey(null),
            observability =
                Env.Observability(
                    enabled = true,
                    serviceName = "shelf-test",
                    environment = "test",
                    tracingEnabled = false,
                    traceOwner = "app",
                    metricsEnabled = true,
                    metricsPath = "/metrics",
                    metricsPrometheusEnabled = true,
                    metricsOtlpEnabled = false,
                    otlpEndpoint = null,
                    otlpProtocol = "grpc",
                    samplingRatio = 1.0,
                    jsonLogsEnabled = false,
                ),
        )

    init {
        body()
    }

    suspend fun testWithDeps(block: suspend (Dependencies) -> Unit) {
        resourceScope {
            val deps = dependencies(testEnv)
            block(deps)
        }
    }

    suspend fun testWithContext(
        block:
            suspend context(Dependencies)
            () -> Unit
    ) {
        resourceScope {
            val deps = dependencies(testEnv)
            with(deps) { block() }
        }
    }

    suspend fun testApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        resourceScope {
            val deps = dependencies(testEnv)
            testApplication {
                application { app(deps) }

                val client = createClient {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                isLenient = true
                                ignoreUnknownKeys = true
                                explicitNulls = false
                                encodeDefaults = true
                            }
                        )
                    }
                }

                block(client)
            }
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        tempStorageRoot.toFile().deleteRecursively()
    }

    suspend fun registerUser(
        client: HttpClient,
        email: String,
        username: String,
        password: String = "password123",
    ): String =
        client
            .post("/api/users") {
                contentType(ContentType.Application.Json)
                setBody(
                    Request(UserRequest(email = email, username = username, password = password))
                )
            }
            .body<Response<UserWithToken>>()
            .data
            .token
            .value
}
