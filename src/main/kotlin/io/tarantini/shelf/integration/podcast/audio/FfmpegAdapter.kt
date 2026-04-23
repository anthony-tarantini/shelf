package io.tarantini.shelf.integration.podcast.audio

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.processing.sanitization.domain.FfmpegExecutionFailed
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FfmpegAdapter {
    /**
     * Decrypts an Audible AAX file using activation bytes.
     */
    context(_: RaiseContext)
    suspend fun decryptAax(input: Path, output: Path, activationBytes: String)

    /**
     * Decrypts an Audible AAXC file using a key and IV.
     */
    context(_: RaiseContext)
    suspend fun decryptAaxc(input: Path, output: Path, key: String, iv: String)
}

fun ffmpegAdapter(): FfmpegAdapter = DefaultFfmpegAdapter()

private class DefaultFfmpegAdapter : FfmpegAdapter {
    context(_: RaiseContext)
    override suspend fun decryptAax(input: Path, output: Path, activationBytes: String) {
        val command = listOf(
            "ffmpeg", "-y",
            "-activation_bytes", activationBytes,
            "-i", input.absolutePathString(),
            "-codec:a", "copy",
            "-vn",
            output.absolutePathString()
        )
        runCommand(command)
    }

    context(_: RaiseContext)
    override suspend fun decryptAaxc(input: Path, output: Path, key: String, iv: String) {
        val command = listOf(
            "ffmpeg", "-y",
            "-audible_key", key,
            "-audible_iv", iv,
            "-i", input.absolutePathString(),
            "-codec:a", "copy",
            "-vn",
            output.absolutePathString()
        )
        runCommand(command)
    }

    context(_: RaiseContext)
    private suspend fun runCommand(command: List<String>) {
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                raise(FfmpegExecutionFailed)
            }
            
            if (process.exitValue() != 0) {
                raise(FfmpegExecutionFailed)
            }
        }
    }
}
