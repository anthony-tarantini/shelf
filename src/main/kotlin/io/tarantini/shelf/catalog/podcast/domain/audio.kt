package io.tarantini.shelf.catalog.podcast.domain

private val MIME_BY_EXTENSION =
    mapOf(
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "m4b" to "audio/x-m4b",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
    )

private val EXTENSION_BY_MIME =
    mapOf(
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/mp4" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/x-m4b" to "m4b",
        "audio/aac" to "aac",
        "audio/ogg" to "ogg",
        "audio/flac" to "flac",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
    )

fun audioMimeType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return MIME_BY_EXTENSION[ext] ?: "application/octet-stream"
}

fun audioExtensionFromMime(contentType: String): String? =
    EXTENSION_BY_MIME[contentType.lowercase().substringBefore(';').trim()]
