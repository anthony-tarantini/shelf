package io.tarantini.shelf.catalog.author.domain

private val whitespaceRegex = Regex("\\s+")

fun canonicalizeAuthorName(raw: String): String =
    raw.trim().replace(whitespaceRegex, " ").lowercase()
