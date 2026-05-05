package io.tarantini.shelf.integration.podcast.libation

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
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
                    parseAndResolve(path, dropDirectory)
                        .fold(
                            ifLeft = { invalid += 1 },
                            ifRight = { resolved ->
                                valid += 1
                                manifests += resolved
                                jsonResolvedAudioPaths.add(resolved.audioPath)
                            },
                        )
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
                        toFallbackManifest(
                                audioPath = audioPath,
                                normalizedDropDirectory = normalizedDropDirectory,
                                realDropDirectory = realDropDirectory,
                            )
                            .fold(
                                ifLeft = { invalid += 1 },
                                ifRight = { resolved ->
                                    valid += 1
                                    manifests += resolved
                                },
                            )
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

    private fun parseAndResolve(
        path: Path,
        dropDirectory: Path,
    ): Either<LibationScanError, LibationResolvedManifest> = either {
        val manifest =
            catch({ parser.parse(path) }) { raise(LibationScanError("Invalid manifest JSON.")) }
        toResolvedManifest(manifest, dropDirectory, path).bind()
    }

    private fun toResolvedManifest(
        manifest: LibationManifest,
        dropDirectory: Path,
        manifestPath: Path,
    ): Either<LibationScanError, LibationResolvedManifest> = either {
        ensure(
            !manifest.asin.isNullOrBlank() &&
                !manifest.title.isNullOrBlank() &&
                !manifest.audioFile.isNullOrBlank()
        ) {
            LibationScanError("Manifest is missing required fields.")
        }

        val normalizedDropDirectory = dropDirectory.normalize()
        val candidateAudioPath =
            normalizedDropDirectory
                .resolve(
                    ensureNotNull(manifest.audioFile) {
                        LibationScanError("Manifest audio file is missing.")
                    }
                )
                .normalize()
        ensure(candidateAudioPath.startsWith(normalizedDropDirectory)) {
            LibationScanError("Manifest audio path escapes drop directory.")
        }

        ensure(Files.exists(candidateAudioPath) && Files.isRegularFile(candidateAudioPath)) {
            LibationScanError("Manifest audio file does not exist.")
        }

        // Reject direct symlink targets so manifests cannot point outside the import root.
        ensure(!Files.isSymbolicLink(candidateAudioPath)) {
            LibationScanError("Manifest audio path cannot be a symlink.")
        }

        val realDropDirectory =
            catch({ normalizedDropDirectory.toRealPath() }) {
                raise(LibationScanError("Drop directory is inaccessible."))
            }
        val realAudioPath =
            catch({ candidateAudioPath.toRealPath() }) {
                raise(LibationScanError("Manifest audio file does not exist."))
            }
        ensure(realAudioPath.startsWith(realDropDirectory)) {
            LibationScanError("Resolved audio path escapes drop directory.")
        }

        // Reject mistaken self-references where audioFile points back to manifest JSON.
        ensure(
            realAudioPath.extension.lowercase() != "json" && realAudioPath.name != manifestPath.name
        ) {
            LibationScanError("Manifest audio path must reference an audio file.")
        }

        val normalizedTitle =
            ensureNotNull(manifest.title) { LibationScanError("Manifest title is missing.") }.trim()
        val publishedYear = parsePublishedYear(manifest.publishedAt).bind()
        ensure(manifest.durationSeconds == null || manifest.durationSeconds >= 0.0) {
            LibationScanError("Manifest durationSeconds cannot be negative.")
        }
        LibationResolvedManifest(
            asin =
                ensureNotNull(manifest.asin) { LibationScanError("Manifest asin is missing.") }
                    .trim(),
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

    private fun parsePublishedYear(raw: String?): Either<LibationScanError, Int?> = either {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return@either null
        val yearText = candidate.take(4)
        val year =
            ensureNotNull(yearText.toIntOrNull()) { LibationScanError("Invalid publishedAt year.") }
        ensure(year in 1000..9999) { LibationScanError("Invalid publishedAt year.") }
        year
    }

    private fun toFallbackManifest(
        audioPath: Path,
        normalizedDropDirectory: Path,
        realDropDirectory: Path,
    ): Either<LibationScanError, LibationResolvedManifest> = either {
        val normalizedAudioPath = audioPath.toAbsolutePath().normalize()
        ensure(normalizedAudioPath.startsWith(normalizedDropDirectory)) {
            LibationScanError("Audio path escapes drop directory.")
        }
        ensure(!Files.isSymbolicLink(normalizedAudioPath)) {
            LibationScanError("Audio path cannot be a symlink.")
        }

        val realAudioPath =
            catch({ normalizedAudioPath.toRealPath() }) {
                raise(LibationScanError("Audio file does not exist."))
            }
        ensure(realAudioPath.startsWith(realDropDirectory)) {
            LibationScanError("Resolved audio path escapes drop directory.")
        }

        val title = normalizeTitle(realAudioPath.nameWithoutExtension)
        val parentSeriesTitle =
            realAudioPath.parent?.takeIf { it != realDropDirectory }?.fileName?.toString()?.trim()
        val seriesTitle = parentSeriesTitle?.let(::normalizeTitle).takeUnless { it.isNullOrBlank() }
        val fileAsin = extractAsin(realAudioPath.name)
        val parentAsin = parentSeriesTitle?.let(::extractAsin)
        val asin =
            ensureNotNull(fileAsin ?: parentAsin ?: extractAsin(realAudioPath.pathString)) {
                LibationScanError("Unable to infer ASIN from audio path.")
            }

        LibationResolvedManifest(
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

private data class LibationScanError(val message: String)
