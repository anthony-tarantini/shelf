@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats.domain

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.UuidAdapter
import io.tarantini.shelf.app.UuidValueClass
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class KoreaderBookId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(id: String?): KoreaderBookId {
            ensureNotNull(id) { EmptyKoreaderBookId }
            ensure(id.isNotEmpty()) { EmptyKoreaderBookId }
            return KoreaderBookId(ensureNotNull(Uuid.parseOrNull(id)) { InvalidKoreaderBookId })
        }

        fun fromRaw(value: Uuid) = KoreaderBookId(value)

        fun fromRaw(value: UUID) = KoreaderBookId(value.toKotlinUuid())

        fun random(): KoreaderBookId = KoreaderBookId(Uuid.random())

        val adapter = object : UuidAdapter<KoreaderBookId>(::fromRaw) {}
    }
}

@JvmInline
@Serializable
value class KoreaderSessionId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        fun fromRaw(value: Uuid) = KoreaderSessionId(value)

        fun fromRaw(value: UUID) = KoreaderSessionId(value.toKotlinUuid())

        fun random(): KoreaderSessionId = KoreaderSessionId(Uuid.random())

        val adapter = object : UuidAdapter<KoreaderSessionId>(::fromRaw) {}
    }
}

@JvmInline
value class Md5Hash private constructor(val value: String) {
    companion object {
        private val HEX_REGEX = Regex("^[0-9a-f]{32}$")

        fun fromRawOrNull(value: String?): Md5Hash? {
            if (value == null) return null
            val normalized = value.trim().lowercase()
            return if (HEX_REGEX.matches(normalized)) Md5Hash(normalized) else null
        }
    }
}
