package io.tarantini.shelf.app

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant

fun OffsetDateTime?.toKotlinInstant(): Instant? =
    this?.let { Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli()) }

@JvmName("nullableToOffsetDateTimeUtc")
fun Instant?.toOffsetDateTimeUtc(): OffsetDateTime? =
    this?.let {
        OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()),
            ZoneOffset.UTC,
        )
    }

fun Instant.toOffsetDateTimeUtc(): OffsetDateTime =
    OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), ZoneOffset.UTC)
