package io.tarantini.shelf

import io.kotest.core.config.AbstractProjectConfig
import java.nio.file.Path
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName

object ProjectConfig : AbstractProjectConfig() {

    private val customImage =
        ImageFromDockerfile("shelf-postgres:test", false)
            .withFileFromPath("Dockerfile", Path.of("database.Dockerfile").toAbsolutePath())
            .withDockerfile(Path.of("database.Dockerfile").toAbsolutePath())

    val postgres =
        PostgreSQLContainer(
                DockerImageName.parse(customImage.get()).asCompatibleSubstituteFor("postgres")
            )
            .withDatabaseName("shelf")
            .withUsername("shelf")
            .withPassword("shelf")
            .withInitScript("init-db.sql")

    override suspend fun beforeProject() {
        println("Starting PostgreSQL TestContainer (building image if necessary)...")
        postgres.start()
        println("PostgreSQL TestContainer started at ${postgres.jdbcUrl}")
    }

    override suspend fun afterProject() {
        postgres.stop()
    }
}
