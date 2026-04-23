package io.tarantini.shelf.integration.podcast.libation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

class LibationScanner(private val parser: LibationManifestParser) {
    fun scan(dropDirectory: Path): LibationScanResult {
        if (!Files.exists(dropDirectory) || !Files.isDirectory(dropDirectory)) {
            return LibationScanResult(
                discoveredCount = 0,
                validManifestCount = 0,
                invalidManifestCount = 0,
            )
        }

        var discovered = 0
        var valid = 0
        var invalid = 0
        val manifests = mutableListOf<LibationResolvedManifest>()

        Files.walk(dropDirectory).use { stream ->
            stream
                .asSequence()
                .filter { it.isRegularFile() && it.extension.lowercase() == "json" }
                .forEach { path ->
                    discovered += 1
                    runCatching { parser.parse(path) }
                        .mapCatching { manifest ->
                            toResolvedManifest(manifest, dropDirectory, path)
                        }
                        .onSuccess { resolved ->
                            valid += 1
                            manifests += resolved
                        }
                        .onFailure { invalid += 1 }
                }
        }

        return LibationScanResult(
            discoveredCount = discovered,
            validManifestCount = valid,
            invalidManifestCount = invalid,
            manifests = manifests,
        )
    }

    private fun toResolvedManifest(
        manifest: LibationManifest,
        dropDirectory: Path,
        manifestPath: Path,
    ): LibationResolvedManifest {
        if (
            manifest.asin.isNullOrBlank() ||
                manifest.title.isNullOrBlank() ||
                manifest.audioFile.isNullOrBlank()
        ) {
            throw IllegalArgumentException("Manifest is missing required fields.")
        }

        val normalizedDropDirectory = dropDirectory.normalize()
        val candidateAudioPath = normalizedDropDirectory.resolve(manifest.audioFile).normalize()
        if (!candidateAudioPath.startsWith(normalizedDropDirectory)) {
            throw IllegalArgumentException("Manifest audio path escapes drop directory.")
        }

        if (!Files.exists(candidateAudioPath) || !Files.isRegularFile(candidateAudioPath)) {
            throw IllegalArgumentException("Manifest audio file does not exist.")
        }

        // Reject direct symlink targets so manifests cannot point outside the import root.
        if (Files.isSymbolicLink(candidateAudioPath)) {
            throw IllegalArgumentException("Manifest audio path cannot be a symlink.")
        }

        val realDropDirectory = normalizedDropDirectory.toRealPath()
        val realAudioPath = candidateAudioPath.toRealPath()
        if (!realAudioPath.startsWith(realDropDirectory)) {
            throw IllegalArgumentException("Resolved audio path escapes drop directory.")
        }

        // Reject mistaken self-references where audioFile points back to manifest JSON.
        if (
            realAudioPath.extension.lowercase() == "json" || realAudioPath.name == manifestPath.name
        ) {
            throw IllegalArgumentException("Manifest audio path must reference an audio file.")
        }

        val normalizedTitle = manifest.title.trim()
        val publishedYear = parsePublishedYear(manifest.publishedAt)
        if (manifest.durationSeconds != null && manifest.durationSeconds < 0.0) {
            throw IllegalArgumentException("Manifest durationSeconds cannot be negative.")
        }
        return LibationResolvedManifest(
            asin = manifest.asin.trim(),
            title = normalizedTitle,
            seriesTitle =
                manifest.seriesTitle?.trim().takeUnless { it.isNullOrBlank() } ?: normalizedTitle,
            description = manifest.description?.trim()?.ifBlank { null },
            publishedYear = publishedYear,
            durationSeconds = manifest.durationSeconds,
            manifestPath = manifestPath,
            audioPath = realAudioPath,
        )
    }

    private fun parsePublishedYear(raw: String?): Int? {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        val yearText = candidate.take(4)
        val year =
            yearText.toIntOrNull() ?: throw IllegalArgumentException("Invalid publishedAt year.")
        if (year !in 1000..9999) throw IllegalArgumentException("Invalid publishedAt year.")
        return year
    }
}
