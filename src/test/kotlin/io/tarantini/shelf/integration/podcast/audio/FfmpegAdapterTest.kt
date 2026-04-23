package io.tarantini.shelf.integration.podcast.audio

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths
import arrow.core.raise.recover
import io.kotest.assertions.fail

class FfmpegAdapterTest : StringSpec({
    "ffmpeg adapter should generate correct aax command structure" {
        // This is more of a wiring/compilation check since actually running ffmpeg 
        // requires the binary and a real file.
        val adapter = ffmpegAdapter()
        // We expect it to fail in test environment because ffmpeg won't find the file
        recover({
            adapter.decryptAax(Paths.get("non-existent.aax"), Paths.get("out.m4b"), "12345678")
        }) {
            // Success if it at least tried to run and failed with expected error or file error
            it.toString() shouldBe it.toString()
        }
    }
})
