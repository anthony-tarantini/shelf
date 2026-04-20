@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.either
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.AuthorName
import io.tarantini.shelf.catalog.book.domain.AuthorRelinkIntent
import kotlin.uuid.Uuid

class BookAuthorResolverTest :
    StringSpec({
        "resolveAuthorIds reuses canonical name match without creating author" {
            val existingId = AuthorId.fromRaw(Uuid.random())
            val store =
                FakeAuthorResolutionStore(
                    authors = mutableListOf(ExistingAuthor(id = existingId, name = "Isaac Asimov"))
                )
            val resolver = SqlDelightBookAuthorResolver(store)

            val result = either {
                resolver.resolveAuthorIds(
                    listOf(AuthorRelinkIntent.UpsertByName(AuthorName.fromRaw("  isaac   asimov ")))
                )
            }

            result.getOrNull() shouldBe listOf(existingId)
            store.createdNames shouldBe emptyList()
        }

        "resolveAuthorIds validates and returns existing author when intent is UseExisting" {
            val existingId = AuthorId.fromRaw(Uuid.random())
            val store =
                FakeAuthorResolutionStore(authors = mutableListOf(ExistingAuthor(existingId, "A")))
            val resolver = SqlDelightBookAuthorResolver(store)

            val result = either {
                resolver.resolveAuthorIds(listOf(AuthorRelinkIntent.UseExisting(existingId)))
            }

            result.getOrNull() shouldBe listOf(existingId)
            store.requiredIds shouldBe listOf(existingId)
        }

        "resolveAuthorIds creates author when canonical name does not exist" {
            val createdId = AuthorId.fromRaw(Uuid.random())
            val store =
                FakeAuthorResolutionStore(
                    authors = mutableListOf(),
                    createIdFactory = { _, _ -> createdId },
                )
            val resolver = SqlDelightBookAuthorResolver(store)

            val result = either {
                resolver.resolveAuthorIds(
                    listOf(AuthorRelinkIntent.UpsertByName(AuthorName.fromRaw("Octavia Butler")))
                )
            }

            result.getOrNull() shouldBe listOf(createdId)
            store.createdNames shouldBe listOf("Octavia Butler")
        }

        "deleteOrphanedAuthors delegates to store" {
            val store = FakeAuthorResolutionStore(mutableListOf())
            val resolver = SqlDelightBookAuthorResolver(store)

            either { resolver.deleteOrphanedAuthors() }

            store.deletedOrphans shouldBe true
        }
    })

private class FakeAuthorResolutionStore(
    private val authors: MutableList<ExistingAuthor>,
    private val createIdFactory: (String, Int) -> AuthorId = { _, _ ->
        AuthorId.fromRaw(Uuid.random())
    },
) : AuthorResolutionStore {
    val createdNames = mutableListOf<String>()
    val requiredIds = mutableListOf<AuthorId>()
    var deletedOrphans: Boolean = false

    context(_: RaiseContext)
    override fun getAllAuthors(): List<ExistingAuthor> = authors.toList()

    context(_: RaiseContext)
    override fun requireAuthor(id: AuthorId): AuthorId {
        requiredIds += id
        return id
    }

    context(_: RaiseContext)
    override fun createAuthor(name: String): AuthorId {
        createdNames += name
        val createdId = createIdFactory(name, createdNames.size)
        authors += ExistingAuthor(id = createdId, name = name)
        return createdId
    }

    context(_: RaiseContext)
    override fun deleteOrphanedAuthors() {
        deletedOrphans = true
    }
}
