package io.tarantini.shelf.user.activity.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ActivityCommandTest :
    StringSpec({
        "reading progress command normalizes payload" {
            val command =
                ReadingProgress(
                        positionSeconds = -12.0,
                        durationSeconds = -1.0,
                        progressPercent = 2.5,
                    )
                    .toCommand()
            command.progress.kind shouldBe ReadingProgressKind.AUDIOBOOK
            command.progress.positionSeconds shouldBe 0.0
            command.progress.durationSeconds shouldBe null
            command.progress.progressPercent shouldBe 1.0
        }

        "read status request maps to save command" {
            val command = ReadStatusRequest(ReadStatus.FINISHED).toCommand()
            command.status shouldBe ReadStatus.FINISHED
        }
    })
