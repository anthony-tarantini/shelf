import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    alias(libs.plugins.apollo)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.assert)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktor)
    alias(libs.plugins.spotless)
    alias(libs.plugins.vcu)
}

kotlin {
    jvmToolchain(21)
}

group = providers.gradleProperty("projects.group").get()
version = rootDir.resolve("version.txt").readText().trim()

application {
    mainClass = "io.tarantini.shelf.MainKt"
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/baseline.xml")
}

sqldelight {
    databases {
        create("Database") {
            packageName = "io.tarantini.shelf"
            dialect("app.cash.sqldelight:postgresql-dialect:2.3.2")
        }
    }
}

apollo {
    service("hardcover") {
        packageName.set("io.tarantini.shelf.providers.hardcover")
        introspection {
            endpointUrl.set("https://api.hardcover.app/v1/graphql")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
            headers.put("Authorization", "Bearer ${System.getenv("HARDCOVER_API_KEY") ?: ""}")
        }
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors = true
        }
        kotlin.compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
    }

    withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            sarif.required.set(true)
        }
    }

    test {
        useJUnitPlatform()
    }
}

ktor {
    docker {
        jreVersion = JavaVersion.VERSION_21
        localImageName = "shelf"
    }
}

spotless {
    kotlin {
        targetExclude("**/build/**")
        ktfmt().kotlinlangStyle().configure {
            it.setRemoveUnusedImports(true)
        }
    }
}

dependencies {
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.cohort)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.suspendapp)
    implementation(libs.bundles.sqldelight)

    implementation(libs.apollo)
    implementation(libs.hikari)
    implementation(libs.klogging)
    implementation(libs.kjwt.core)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.serialization.xml)
    implementation(libs.lettuce.core)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.ktor)
    implementation(libs.opentelemetry.sdk.extension.autoconfigure)
    implementation(libs.postgresql)
    implementation(libs.slugify)
    implementation(libs.sqlite.jdbc)
    implementation(libs.tasks)
    implementation(libs.bundles.images)
    implementation(libs.xmlutil.serialization)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.ktor.client)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.ktor.server.tests)
}
