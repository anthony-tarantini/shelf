@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import arrow.core.raise.either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.organization.library.domain.*
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.Uuid

class LibraryServiceTest :
    StringSpec({
        "createLibrary orchestrates decide then persist with auth userId" {
            val repository = mockk<LibraryMutationRepository>()
            val userId = UserId.fromRaw(Uuid.random())
            val libraryId = LibraryId.fromRaw(Uuid.random())
            val saved = LibraryRoot.fromRaw(libraryId, userId, "Fiction")
            val auth = JwtContext(JwtToken("test-token"), userId)

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { repository.createLibrary(userId, "Fiction") }
            } coAnswers
                {
                    calls += "persist"
                    saved
                }

            val service =
                libraryService(
                    libraryQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                with(auth) {
                    service.createLibrary(
                        CreateLibraryCommand(title = LibraryTitle.fromRaw("Fiction"))
                    )
                }
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe saved })
            calls shouldBe listOf("persist")
        }

        "updateLibrary orchestrates ownership check then load decide persist" {
            val repository = mockk<LibraryMutationRepository>()
            val userId = UserId.fromRaw(Uuid.random())
            val libraryId = LibraryId.fromRaw(Uuid.random())
            val existing = LibraryRoot.fromRaw(libraryId, userId, "Fiction")
            val updated = LibraryRoot.fromRaw(libraryId, userId, "Science Fiction")
            val auth = JwtContext(JwtToken("test-token"), userId)

            val calls = mutableListOf<String>()
            coEvery { with(any<RaiseContext>()) { repository.getLibraryById(libraryId) } } coAnswers
                {
                    calls += "load"
                    existing
                }
            coEvery {
                with(any<RaiseContext>()) { repository.updateLibrary(libraryId, "Science Fiction") }
            } coAnswers
                {
                    calls += "persist"
                    updated
                }

            val service =
                libraryService(
                    libraryQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                with(auth) {
                    service.updateLibrary(
                        UpdateLibraryCommand(
                            id = libraryId,
                            title = LibraryTitle.fromRaw("Science Fiction"),
                        )
                    )
                }
            }

            result.fold({ fail("Should not have failed: $it") }, { it shouldBe updated })
            calls shouldBe listOf("load", "persist")
        }

        "updateLibrary rejects non-owner" {
            val repository = mockk<LibraryMutationRepository>()
            val ownerId = UserId.fromRaw(Uuid.random())
            val attackerId = UserId.fromRaw(Uuid.random())
            val libraryId = LibraryId.fromRaw(Uuid.random())
            val existing = LibraryRoot.fromRaw(libraryId, ownerId, "Fiction")
            val attackerAuth = JwtContext(JwtToken("attacker-token"), attackerId)

            coEvery { with(any<RaiseContext>()) { repository.getLibraryById(libraryId) } } returns
                existing

            val service =
                libraryService(
                    libraryQueries = mockk(relaxed = true),
                    bookQueries = mockk(relaxed = true),
                    mutationRepository = repository,
                )

            val result = either {
                with(attackerAuth) {
                    service.updateLibrary(
                        UpdateLibraryCommand(id = libraryId, title = LibraryTitle.fromRaw("Hacked"))
                    )
                }
            }

            result.leftOrNull().shouldBeInstanceOf<AccessDenied>()
        }
    })
