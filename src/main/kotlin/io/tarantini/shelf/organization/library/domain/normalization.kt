package io.tarantini.shelf.organization.library.domain

private val whitespaceRegex = Regex("\\s+")

fun canonicalizeLibraryTitle(raw: String): String =
    raw.trim().replace(whitespaceRegex, " ").lowercase()
