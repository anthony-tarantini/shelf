@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.app

import app.cash.sqldelight.ColumnAdapter
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

interface UuidValueClass {
    val value: Uuid
}

interface StringValueClass {
    val value: String
}

interface ByteArrayValueClass {
    val value: ByteArray
}

open class UuidAdapter<T : UuidValueClass>(private val factory: (UUID) -> T) :
    ColumnAdapter<T, UUID> {
    override fun decode(databaseValue: UUID): T {
        return factory(databaseValue)
    }

    override fun encode(value: T): UUID = value.value.toJavaUuid()
}

open class StringAdapter<T : StringValueClass>(private val factory: (String) -> T) :
    ColumnAdapter<T, String> {
    override fun decode(databaseValue: String): T {
        return factory(databaseValue)
    }

    override fun encode(value: T): String = value.value
}

open class ByteArrayAdapter<T : ByteArrayValueClass>(private val factory: (ByteArray) -> T) :
    ColumnAdapter<T, ByteArray> {
    override fun decode(databaseValue: ByteArray): T {
        return factory(databaseValue)
    }

    override fun encode(value: T): ByteArray = value.value
}
