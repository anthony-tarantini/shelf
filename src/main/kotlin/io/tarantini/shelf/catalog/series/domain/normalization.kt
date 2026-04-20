package io.tarantini.shelf.catalog.series.domain

private val whitespaceRegex = Regex("\\s+")

fun canonicalizeSeriesTitle(raw: String): String =
    raw.trim().replace(whitespaceRegex, " ").lowercase()
