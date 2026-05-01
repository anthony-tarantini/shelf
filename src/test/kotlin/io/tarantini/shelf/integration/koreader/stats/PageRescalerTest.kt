package io.tarantini.shelf.integration.koreader.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderPageStatRow
import io.tarantini.shelf.integration.koreader.stats.domain.PageRescaler

class PageRescalerTest :
    StringSpec({
        "row without totalPagesAtRead passes through" {
            val row =
                KoreaderPageStatRow(
                    page = 5,
                    startTimeEpoch = 1,
                    durationSeconds = 30,
                    totalPagesAtRead = null,
                )
            PageRescaler.rescale(row, currentPages = 100).toList() shouldBe listOf(row)
        }

        "matching totals passes through unchanged" {
            val row =
                KoreaderPageStatRow(
                    page = 5,
                    startTimeEpoch = 1,
                    durationSeconds = 30,
                    totalPagesAtRead = 100,
                )
            PageRescaler.rescale(row, currentPages = 100).toList() shouldBe listOf(row)
        }

        "expansion fans page across new range" {
            val row =
                KoreaderPageStatRow(
                    page = 1,
                    startTimeEpoch = 0,
                    durationSeconds = 60,
                    totalPagesAtRead = 50,
                )
            val result = PageRescaler.rescale(row, currentPages = 100).toList()
            result.size shouldBe 2
            result[0].page shouldBe 1
            result[1].page shouldBe 2
            result.sumOf { it.durationSeconds } shouldBe 60
        }

        "compression maps multiple old pages to single new page" {
            val row =
                KoreaderPageStatRow(
                    page = 4,
                    startTimeEpoch = 0,
                    durationSeconds = 30,
                    totalPagesAtRead = 100,
                )
            val result = PageRescaler.rescale(row, currentPages = 50).toList()
            result.size shouldBe 1
            result[0].page shouldBe 2
        }
    })
