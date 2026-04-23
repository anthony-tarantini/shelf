package io.tarantini.shelf.integration.podcast.libation

import java.nio.file.Path
import kotlinx.serialization.Serializable

@Serializable
data class LibationManifest(
    val asin: String? = null,
    val title: String? = null,
    val seriesTitle: String? = null,
    val description: String? = null,
    val publishedAt: String? = null,
    val durationSeconds: Double? = null,
    val audioFile: String? = null,
)

data class LibationResolvedManifest(
    val asin: String,
    val title: String,
    val seriesTitle: String,
    val description: String?,
    val publishedYear: Int?,
    val durationSeconds: Double?,
    val manifestPath: Path,
    val audioPath: Path,
)

data class LibationScanResult(
    val discoveredCount: Int,
    val validManifestCount: Int,
    val invalidManifestCount: Int,
    val manifests: List<LibationResolvedManifest> = emptyList(),
)
