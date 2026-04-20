package io.tarantini.shelf.catalog.book.domain

fun canonicalizeBookRelationName(raw: String): String =
    raw.trim().lowercase().replace(Regex("\\s+"), " ")
