@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series

import arrow.core.raise.either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.series.domain.*
import kotlin.uuid.Uuid

class SeriesServiceTest :
    StringSpec({
        "createSeries orchestrates decide then persist" {
            val repository = mockk<SeriesMutationRepository>()
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val saved = SeriesRoot.fromRaw(seriesId, "The Stormlight Archive")

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { repository.insertSeries("The Stormlight Archive") }
            } coAnswers
                {
                    calls += "persist"
                    saved
                }

            val service =
                seriesService(
                    seriesQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    storageService = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.createSeries(
                    CreateSeriesCommand(title = SeriesTitle.fromRaw("The Stormlight Archive"))
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe saved })
            calls shouldBe listOf("persist")
        }

        "updateSeries orchestrates load decide persist" {
            val repository = mockk<SeriesMutationRepository>()
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val existing = SeriesRoot.fromRaw(seriesId, "Stormlight")
            val updated = SeriesRoot.fromRaw(seriesId, "The Stormlight Archive")

            val calls = mutableListOf<String>()
            coEvery { with(any<RaiseContext>()) { repository.getSeriesById(seriesId) } } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) {
                    repository.updateSeries("The Stormlight Archive", seriesId)
                }
            } coAnswers
                {
                    calls += "persist"
                    updated
                }

            val service =
                seriesService(
                    seriesQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    storageService = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.updateSeries(
                    UpdateSeriesCommand(
                        id = seriesId,
                        title = SeriesTitle.fromRaw("The Stormlight Archive"),
                    )
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe updated })
            calls shouldBe listOf("load", "persist")
        }

        "updateSeries with null title preserves existing" {
            val repository = mockk<SeriesMutationRepository>()
            val seriesId = SeriesId.fromRaw(Uuid.random())
            val existing = SeriesRoot.fromRaw(seriesId, "The Stormlight Archive")
            val preserved = SeriesRoot.fromRaw(seriesId, "The Stormlight Archive")

            val calls = mutableListOf<String>()
            coEvery { with(any<RaiseContext>()) { repository.getSeriesById(seriesId) } } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) {
                    repository.updateSeries("The Stormlight Archive", seriesId)
                }
            } coAnswers
                {
                    calls += "persist"
                    preserved
                }

            val service =
                seriesService(
                    seriesQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    storageService = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.updateSeries(UpdateSeriesCommand(id = seriesId, title = null))
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe preserved })
            calls shouldBe listOf("load", "persist")
        }

        "searchSeriesFuzzy returns typed error while disabled" {
            val service =
                seriesService(
                    seriesQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    storageService = mockk(relaxed = true),
                    mutationRepository = mockk(relaxed = true),
                )

            val result = either { service.searchSeriesFuzzy("stormlight") }
            result.fold({ it shouldBe SeriesFuzzySearchDisabled }, { fail("Should have failed") })
        }
    })
