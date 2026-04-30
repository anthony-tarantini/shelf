package io.tarantini.shelf.integration.podcast.libation

private val separatorRegex = Regex("[\\p{Punct}\\s]+")

fun canonicalSeriesKey(raw: String): String {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isEmpty()) return "untitled-series"
    return trimmed.replace(separatorRegex, " ").trim()
}
