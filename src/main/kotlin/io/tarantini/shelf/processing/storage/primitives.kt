package io.tarantini.shelf.processing.storage

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.StringAdapter
import io.tarantini.shelf.app.StringValueClass
import java.nio.file.Path
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class StoragePath private constructor(override val value: String) : StringValueClass {
    fun parent(): StoragePath? {
        val lastIndex = value.lastIndexOf('/')
        return if (lastIndex != -1) fromRaw(value.substring(0, lastIndex)) else null
    }

    fun resolve(child: String): StoragePath {
        val normalizedBase = value.removeSuffix("/")
        val normalizedChild = child.removePrefix("/")
        return fromRaw("$normalizedBase/$normalizedChild")
    }

    fun resolveCover(): StoragePath? {
        return parent()?.resolve("cover.jpg")
    }

    fun thumbnail(): StoragePath = fromRaw(value.substringBeforeLast(".") + "_thumb.jpg")

    fun extension(): String = value.substringAfterLast('.', "")

    companion object {
        context(_: RaiseContext)
        operator fun invoke(path: String?): StoragePath {
            ensureNotNull(path) { StorageBackendError }
            ensure(path.isNotEmpty()) { StorageBackendError }
            ensure(!Path.of(path.replace('\\', '/')).isAbsolute) { UnauthorizedAccess }

            val normalized = normalize(path)
            ensure(normalized.isNotEmpty()) { StorageBackendError }
            ensure(!normalized.startsWith("../")) { UnauthorizedAccess }

            return StoragePath(normalized)
        }

        fun fromRaw(value: String) = StoragePath(value)

        fun safeSegment(value: String?, fallback: String = "unknown"): String {
            val sanitized =
                value
                    ?.trim()
                    ?.replace('\\', '-')
                    ?.replace('/', '-')
                    ?.replace(Regex("[\\p{Cntrl}]"), "")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim(' ', '.')
                    ?.take(120)
                    ?.ifBlank { fallback } ?: fallback

            return sanitized.ifBlank { fallback }
        }

        private fun normalize(path: String): String {
            val unixStyle = path.replace('\\', '/').trim()
            val normalized = Path.of(unixStyle).normalize().toString().replace('\\', '/')
            return normalized.removePrefix("./")
        }

        val adapter = object : StringAdapter<StoragePath>(::fromRaw) {}
    }
}

@JvmInline value class FileBytes(val value: ByteArray)
