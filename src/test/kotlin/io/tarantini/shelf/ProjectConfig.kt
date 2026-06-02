package io.tarantini.shelf

import io.kotest.core.config.AbstractProjectConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object ProjectConfig : AbstractProjectConfig() {

    val postgres =
        PostgreSQLContainer(
                DockerImageName.parse("shelf-postgres:test").asCompatibleSubstituteFor("postgres")
            )
            .withDatabaseName("shelf")
            .withUsername("shelf")
            .withPassword("shelf")
            .withInitScript("init-db.sql")

    override suspend fun beforeProject() {
        println("Starting PostgreSQL TestContainer...")
        postgres.start()
        println("PostgreSQL TestContainer started at ${postgres.jdbcUrl}")
    }

    override suspend fun afterProject() {
        postgres.stop()
    }
}
