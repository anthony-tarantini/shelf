@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.getAllSeries
import io.tarantini.shelf.catalog.series.getSeriesById
import kotlin.uuid.ExperimentalUuidApi

class SeriesPersistenceTest :
    IntegrationSpec({
        "createSeries and getSeriesById" {
            testWithDeps { deps ->
                val queries = deps.database.seriesQueries
                recover({
                    val id = queries.createSeries("Foundation")
                    val series = queries.getSeriesById(id)
                    series.name shouldBe "Foundation"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getAllSeries should return all series" {
            testWithDeps { deps ->
                val queries = deps.database.seriesQueries
                recover({
                    val initialCount = queries.getAllSeries().size
                    queries.createSeries("Series 1")
                    queries.createSeries("Series 2")
                    val seriesList = queries.getAllSeries()
                    seriesList.size shouldBe initialCount + 2
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "createSeries allows duplicate titles with different IDs" {
            testWithDeps { deps ->
                val queries = deps.database.seriesQueries
                recover({
                    val firstId = queries.createSeries("Duplicate Title")
                    val secondId = queries.createSeries("Duplicate Title")
                    (firstId == secondId) shouldBe false
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
