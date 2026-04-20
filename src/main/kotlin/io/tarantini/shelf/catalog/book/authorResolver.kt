package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.getAllAuthors
import io.tarantini.shelf.catalog.author.getAuthorById
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.AuthorRelinkIntent
import io.tarantini.shelf.catalog.book.domain.canonicalizeBookRelationName

internal data class ExistingAuthor(val id: AuthorId, val name: String)

internal interface AuthorResolutionStore {
    context(_: RaiseContext)
    fun getAllAuthors(): List<ExistingAuthor>

    context(_: RaiseContext)
    fun requireAuthor(id: AuthorId): AuthorId

    context(_: RaiseContext)
    fun createAuthor(name: String): AuthorId

    context(_: RaiseContext)
    fun deleteOrphanedAuthors()
}

internal class SqlDelightAuthorResolutionStore(private val authorQueries: AuthorQueries) :
    AuthorResolutionStore {
    context(_: RaiseContext)
    override fun getAllAuthors(): List<ExistingAuthor> =
        authorQueries.getAllAuthors().map { ExistingAuthor(it.id, it.name) }

    context(_: RaiseContext)
    override fun requireAuthor(id: AuthorId): AuthorId = authorQueries.getAuthorById(id).id.id

    context(_: RaiseContext)
    override fun createAuthor(name: String): AuthorId = authorQueries.createAuthor(name)

    context(_: RaiseContext)
    override fun deleteOrphanedAuthors() {
        authorQueries.deleteOrphanedAuthors()
    }
}

internal class SqlDelightBookAuthorResolver(private val store: AuthorResolutionStore) :
    BookAuthorResolver {
    context(_: RaiseContext)
    override fun resolveAuthorIds(authors: List<AuthorRelinkIntent>): List<AuthorId> {
        val existingByCanonical =
            store.getAllAuthors().associateBy({ canonicalizeBookRelationName(it.name) }, { it.id })

        return authors.map { authorIntent ->
            when (authorIntent) {
                is AuthorRelinkIntent.UseExisting -> store.requireAuthor(authorIntent.authorId)
                is AuthorRelinkIntent.UpsertByName -> {
                    val canonical = canonicalizeBookRelationName(authorIntent.name.value)
                    existingByCanonical[canonical] ?: store.createAuthor(authorIntent.name.value)
                }
            }
        }
    }

    context(_: RaiseContext)
    override fun deleteOrphanedAuthors() {
        store.deleteOrphanedAuthors()
    }
}
