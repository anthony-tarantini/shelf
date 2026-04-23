package io.tarantini.shelf.app

import java.time.OffsetDateTime
import kotlin.time.Instant

fun OffsetDateTime?.toKotlinInstant(): Instant? =
    this?.let { Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli()) }
