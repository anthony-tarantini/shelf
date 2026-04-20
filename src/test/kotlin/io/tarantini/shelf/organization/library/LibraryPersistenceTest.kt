@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.organization.library

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.organization.library.domain.*
import io.tarantini.shelf.user.identity.createUser
import io.tarantini.shelf.user.identity.domain.*
import kotlin.uuid.ExperimentalUuidApi

class LibraryPersistenceTest :
    IntegrationSpec({
        "createLibrary should create a new library" {
            testWithDeps { deps ->
                val libraryQueries = deps.database.libraryQueries
                val userQueries = deps.database.userQueries

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("lib-test-1@example.com"),
                                username = UserName.fromRaw("libtest1"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    val libraryId = libraryQueries.createLibrary(user.id.id, "My New Library")
                    val fetchedLibrary = libraryQueries.getLibraryById(libraryId)
                    fetchedLibrary.userId shouldBe user.id.id
                    fetchedLibrary.title shouldBe "My New Library"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getLibrariesForUser should return all libraries for a user" {
            testWithDeps { deps ->
                val libraryQueries = deps.database.libraryQueries
                val userQueries = deps.database.userQueries

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("lib-test-2@example.com"),
                                username = UserName.fromRaw("libtest2"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    libraryQueries.createLibrary(user.id.id, "Library One")
                    libraryQueries.createLibrary(user.id.id, "Library Two")

                    val libraries = libraryQueries.getLibrariesForUser(user.id.id)
                    libraries.size shouldBe 2
                    libraries
                        .map { it.title }
                        .shouldContainExactlyInAnyOrder(listOf("Library One", "Library Two"))
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "updateLibrary should update library title" {
            testWithDeps { deps ->
                val libraryQueries = deps.database.libraryQueries
                val userQueries = deps.database.userQueries

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("lib-test-3@example.com"),
                                username = UserName.fromRaw("libtest3"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    val libraryId = libraryQueries.createLibrary(user.id.id, "Old Title")
                    libraryQueries.updateLibrary(libraryId, "New Title")
                    val fetchedLibrary = libraryQueries.getLibraryById(libraryId)
                    fetchedLibrary.title shouldBe "New Title"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "deleteLibrary should remove a library" {
            testWithDeps { deps ->
                val libraryQueries = deps.database.libraryQueries
                val userQueries = deps.database.userQueries

                recover({
                    val user =
                        userQueries.createUser(
                            NewUser(
                                email = UserEmail.fromRaw("lib-test-4@example.com"),
                                username = UserName.fromRaw("libtest4"),
                                role = UserRole.USER,
                                salt = Salt.generate(),
                                hashedPassword = HashedPassword(byteArrayOf(1)),
                            )
                        )

                    val libraryId = libraryQueries.createLibrary(user.id.id, "Library to Delete")
                    libraryQueries.deleteLibrary(libraryId)

                    recover({
                        libraryQueries.getLibraryById(libraryId)
                        fail("Library should have been deleted")
                    }) {
                        it shouldBe LibraryNotFound
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
