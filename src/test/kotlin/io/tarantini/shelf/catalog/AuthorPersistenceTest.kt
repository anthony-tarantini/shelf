@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.deleteAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorNotFound
import io.tarantini.shelf.catalog.author.getAllAuthors
import io.tarantini.shelf.catalog.author.getAuthorById
import io.tarantini.shelf.catalog.author.updateAuthor
import kotlin.uuid.ExperimentalUuidApi

class AuthorPersistenceTest :
    IntegrationSpec({
        "createAuthor and getAuthorById" {
            testWithDeps { deps ->
                val queries = deps.database.authorQueries
                recover({
                    val id = queries.createAuthor("Isaac Asimov")
                    val author = queries.getAuthorById(id)
                    author.name shouldBe "Isaac Asimov"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "updateAuthor should modify the name" {
            testWithDeps { deps ->
                val queries = deps.database.authorQueries
                recover({
                    val id = queries.createAuthor("Original Name")
                    queries.updateAuthor(id, "Updated Name")
                    val author = queries.getAuthorById(id)
                    author.name shouldBe "Updated Name"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "deleteAuthor should remove the record" {
            testWithDeps { deps ->
                val queries = deps.database.authorQueries
                recover({
                    val id = queries.createAuthor("To Delete")
                    queries.deleteAuthor(id)

                    recover({
                        queries.getAuthorById(id)
                        fail("Should have failed")
                    }) {
                        it shouldBe AuthorNotFound
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getAllAuthors should return all authors" {
            testWithDeps { deps ->
                val queries = deps.database.authorQueries
                recover({
                    val initialCount = queries.getAllAuthors().size
                    queries.createAuthor("Author 1")
                    queries.createAuthor("Author 2")
                    val authors = queries.getAllAuthors()
                    authors.size shouldBe initialCount + 2
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
