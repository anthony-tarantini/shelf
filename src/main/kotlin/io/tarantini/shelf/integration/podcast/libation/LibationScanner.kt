package io.tarantini.shelf.integration.podcast.libation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
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
        val jsonResolvedAudioPaths = mutableSetOf<Path>()
        val normalizedDropDirectory = dropDirectory.normalize()
        val realDropDirectory = normalizedDropDirectory.toRealPath()

        Files.walk(dropDirectory).use { stream ->
            val files = stream.asSequence().filter { it.isRegularFile() }.toList()
            files
                .filter { it.extension.lowercase() == "json" }
                .forEach { path ->
                    discovered += 1
                    runCatching { parser.parse(path) }
                        .mapCatching { manifest ->
                            toResolvedManifest(manifest, dropDirectory, path)
                        }
                        .onSuccess { resolved ->
                            valid += 1
                            manifests += resolved
                            jsonResolvedAudioPaths.add(resolved.audioPath)
                        }
                        .onFailure { invalid += 1 }
                }

            if (discovered == 0) {
                files
                    .filter { it.extension.lowercase() in AUDIO_EXTENSIONS }
                    .forEach { audioPath ->
                        if (
                            jsonResolvedAudioPaths.contains(audioPath.toAbsolutePath().normalize())
                        ) {
                            return@forEach
                        }
                        discovered += 1
                        runCatching {
                                toFallbackManifest(
                                    audioPath = audioPath,
                                    normalizedDropDirectory = normalizedDropDirectory,
                                    realDropDirectory = realDropDirectory,
                                )
                            }
                            .onSuccess { resolved ->
                                valid += 1
                                manifests += resolved
                            }
                            .onFailure { invalid += 1 }
                    }
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

    private fun toFallbackManifest(
        audioPath: Path,
        normalizedDropDirectory: Path,
        realDropDirectory: Path,
    ): LibationResolvedManifest {
        val normalizedAudioPath = audioPath.toAbsolutePath().normalize()
        if (!normalizedAudioPath.startsWith(normalizedDropDirectory)) {
            throw IllegalArgumentException("Audio path escapes drop directory.")
        }
        if (Files.isSymbolicLink(normalizedAudioPath)) {
            throw IllegalArgumentException("Audio path cannot be a symlink.")
        }

        val realAudioPath = normalizedAudioPath.toRealPath()
        if (!realAudioPath.startsWith(realDropDirectory)) {
            throw IllegalArgumentException("Resolved audio path escapes drop directory.")
        }

        val title = normalizeTitle(realAudioPath.nameWithoutExtension)
        val parentSeriesTitle =
            realAudioPath.parent?.takeIf { it != realDropDirectory }?.fileName?.toString()?.trim()
        val seriesTitle = parentSeriesTitle?.let(::normalizeTitle).takeUnless { it.isNullOrBlank() }
        val fileAsin = extractAsin(realAudioPath.name)
        val parentAsin = parentSeriesTitle?.let(::extractAsin)
        val asin =
            fileAsin
                ?: parentAsin
                ?: extractAsin(realAudioPath.pathString)
                ?: throw IllegalArgumentException("Unable to infer ASIN from audio path.")

        return LibationResolvedManifest(
            asin = asin,
            title = title,
            seriesTitle = seriesTitle ?: title,
            description = null,
            publishedYear = null,
            durationSeconds = null,
            manifestPath = realAudioPath,
            audioPath = realAudioPath,
        )
    }

    private fun normalizeTitle(raw: String): String = raw.replace(ASIN_SUFFIX_REGEX, "").trim()

    private fun extractAsin(raw: String): String? =
        ASIN_REGEX.findAll(raw).map { it.groupValues[1].uppercase() }.firstOrNull()

    private companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "m4b", "m4a", "aax", "aaxc")
        val ASIN_REGEX = Regex("""\[([A-Za-z0-9]{10})]""")
        val ASIN_SUFFIX_REGEX = Regex("""\s*\[[A-Za-z0-9]{10}]$""")
    }
}
