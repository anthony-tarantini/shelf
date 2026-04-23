package io.tarantini.shelf.integration.podcast.libation

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json

class LibationManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(path: Path): LibationManifest =
        json.decodeFromString(LibationManifest.serializer(), Files.readString(path))
}
