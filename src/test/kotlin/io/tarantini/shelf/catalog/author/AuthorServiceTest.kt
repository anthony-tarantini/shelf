@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author

import arrow.core.raise.either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.*
import kotlin.uuid.Uuid

class AuthorServiceTest :
    StringSpec({
        "createAuthor orchestrates decide then persist" {
            val repository = mockk<AuthorMutationRepository>()
            val authorId = AuthorId.fromRaw(Uuid.random())
            val saved = AuthorRoot.fromRaw(authorId, "Brandon Sanderson")

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { repository.createAuthor("Brandon Sanderson") }
            } coAnswers
                {
                    calls += "persist"
                    saved
                }

            val service =
                authorService(
                    sqlDriver = mockk(relaxed = true),
                    authorQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.createAuthor(
                    CreateAuthorCommand(name = AuthorName.fromRaw("Brandon Sanderson"))
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe saved })
            calls shouldBe listOf("persist")
        }

        "updateAuthor orchestrates load decide persist" {
            val repository = mockk<AuthorMutationRepository>()
            val authorId = AuthorId.fromRaw(Uuid.random())
            val existing = AuthorRoot.fromRaw(authorId, "B. Sanderson")
            val updated = AuthorRoot.fromRaw(authorId, "Brandon Sanderson")

            val calls = mutableListOf<String>()
            coEvery { with(any<RaiseContext>()) { repository.getAuthorById(authorId) } } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) { repository.updateAuthor(authorId, "Brandon Sanderson") }
            } coAnswers
                {
                    calls += "persist"
                    updated
                }

            val service =
                authorService(
                    sqlDriver = mockk(relaxed = true),
                    authorQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.updateAuthor(
                    UpdateAuthorCommand(
                        id = authorId,
                        name = AuthorName.fromRaw("Brandon Sanderson"),
                    )
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe updated })
            calls shouldBe listOf("load", "persist")
        }

        "updateAuthor preserves existing name when canonically equal" {
            val repository = mockk<AuthorMutationRepository>()
            val authorId = AuthorId.fromRaw(Uuid.random())
            val existing = AuthorRoot.fromRaw(authorId, "Brandon Sanderson")
            val preserved = AuthorRoot.fromRaw(authorId, "Brandon Sanderson")

            val calls = mutableListOf<String>()
            coEvery { with(any<RaiseContext>()) { repository.getAuthorById(authorId) } } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) { repository.updateAuthor(authorId, "Brandon Sanderson") }
            } coAnswers
                {
                    calls += "persist"
                    preserved
                }

            val service =
                authorService(
                    sqlDriver = mockk(relaxed = true),
                    authorQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                service.updateAuthor(
                    UpdateAuthorCommand(
                        id = authorId,
                        name = AuthorName.fromRaw("  brandon sanderson  "),
                    )
                )
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe preserved })
            calls shouldBe listOf("load", "persist")
        }
    })
