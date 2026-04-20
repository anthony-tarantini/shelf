package io.tarantini.shelf.processing.import.staging

import arrow.core.raise.context.ensureNotNull
import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.processing.import.domain.StagedBook
import io.tarantini.shelf.processing.import.domain.StagedBookNotFound
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface StagedBookStore {
    context(_: RaiseContext)
    fun get(id: String): StagedBook

    context(_: RaiseContext)
    fun put(stagedBook: StagedBook)

    context(_: RaiseContext)
    fun remove(id: String)

    context(_: RaiseContext)
    fun getAll(): Map<String, StagedBook>
}

private const val STAGED_BOOK_KEY_PREFIX = "staged_book:"

fun valkeyStagedBookStore(connection: StatefulRedisConnection<String, String>) =
    object : StagedBookStore {
        private val commands = connection.sync()

        context(_: RaiseContext)
        override fun get(id: String): StagedBook {
            val json =
                ensureNotNull(commands.get("$STAGED_BOOK_KEY_PREFIX$id")) { StagedBookNotFound }
            return Json.decodeFromString(json)
        }

        context(_: RaiseContext)
        override fun put(stagedBook: StagedBook) {
            val json = Json.encodeToString(stagedBook)
            commands.set("$STAGED_BOOK_KEY_PREFIX${stagedBook.id}", json)
        }

        context(_: RaiseContext)
        override fun remove(id: String) {
            val deleted = commands.del("$STAGED_BOOK_KEY_PREFIX$id")
            ensureNotNull(if (deleted > 0) true else null) { StagedBookNotFound }
        }

        context(_: RaiseContext)
        override fun getAll(): Map<String, StagedBook> {
            val keys = commands.keys("$STAGED_BOOK_KEY_PREFIX*")
            if (keys.isEmpty()) return emptyMap()
            return keys.associate { key ->
                val id = key.removePrefix(STAGED_BOOK_KEY_PREFIX)
                val json = commands.get(key)
                id to Json.decodeFromString<StagedBook>(json)
            }
        }
    }

fun inMemoryStagedBookStore() =
    object : StagedBookStore {
        private val data = mutableMapOf<String, StagedBook>()

        context(_: RaiseContext)
        override fun get(id: String) = ensureNotNull(data[id]) { StagedBookNotFound }

        context(_: RaiseContext)
        override fun put(stagedBook: StagedBook) {
            data[stagedBook.id] = stagedBook
        }

        context(_: RaiseContext)
        override fun remove(id: String) {
            ensureNotNull(data.remove(id)) { StagedBookNotFound }
        }

        context(_: RaiseContext)
        override fun getAll() = data.toMap()
    }
