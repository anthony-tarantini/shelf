@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.user.identity.domain.*
import kotlin.uuid.ExperimentalUuidApi

class UserPersistenceTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "createUser should insert a user and return SavedUserRoot" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val newUser =
                    NewUser(
                        email = UserEmail.fromRaw("${unique("test")}@example.com"),
                        username = UserName.fromRaw(unique("testuser")),
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(1, 2, 3)),
                    )

                recover({
                    val savedUser = queries.createUser(newUser)
                    savedUser.email shouldBe newUser.email
                    savedUser.username shouldBe newUser.username

                    val fetchedUser = queries.getUserById(savedUser.id.id)
                    fetchedUser.user.email shouldBe newUser.email
                    fetchedUser.user.username shouldBe newUser.username
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getUserByEmail should return the correct user" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val email = UserEmail.fromRaw("${unique("unique")}@example.com")
                val newUser =
                    NewUser(
                        email = email,
                        username = UserName.fromRaw(unique("uniqueuser")),
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(4, 5, 6)),
                    )

                recover({
                    queries.createUser(newUser)
                    val fetchedUser = queries.getUserByEmail(email)
                    fetchedUser.user.email shouldBe email
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getUserByUsername should return the correct user" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val username = UserName.fromRaw(unique("username"))
                val newUser =
                    NewUser(
                        email = UserEmail.fromRaw("${unique("user")}@example.com"),
                        username = username,
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(7, 8, 9)),
                    )

                recover({
                    queries.createUser(newUser)
                    val fetchedUser = queries.getUserByUsername(username)
                    fetchedUser.user.username shouldBe username
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "updateUser should modify user details" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val newUser =
                    NewUser(
                        email = UserEmail.fromRaw("${unique("before")}@example.com"),
                        username = UserName.fromRaw(unique("before")),
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(1)),
                    )

                recover({
                    val savedUser = queries.createUser(newUser)
                    val update =
                        UpdateUser(
                            id = savedUser.id.id,
                            email = UserEmail.fromRaw("${unique("after")}@example.com"),
                            username = UserName.fromRaw(unique("after")),
                            role = UserRole.USER,
                            salt = Salt.generate(),
                            hashedPassword = HashedPassword(byteArrayOf(2)),
                        )

                    queries.updateUser(update)

                    val fetchedUser = queries.getUserById(savedUser.id.id)
                    fetchedUser.user.email shouldBe update.email
                    fetchedUser.user.username shouldBe update.username
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "deleteUserById should remove the user" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val newUser =
                    NewUser(
                        email = UserEmail.fromRaw("${unique("delete")}@example.com"),
                        username = UserName.fromRaw(unique("delete")),
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(0)),
                    )

                recover({
                    val savedUser = queries.createUser(newUser)
                    queries.deleteUserById(savedUser.id.id)

                    recover({
                        queries.getUserById(savedUser.id.id)
                        fail("Should have failed to find user")
                    }) {
                        it shouldBe UserNotFound
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "createUser should fail if email already exists" {
            testWithDeps { deps ->
                val queries = deps.database.userQueries
                val email = UserEmail.fromRaw("${unique("duplicate")}@example.com")
                val newUser =
                    NewUser(
                        email = email,
                        username = UserName.fromRaw(unique("user1")),
                        role = UserRole.USER,
                        salt = Salt.generate(),
                        hashedPassword = HashedPassword(byteArrayOf(1)),
                    )

                recover({
                    queries.createUser(newUser)
                    recover({
                        queries.createUser(
                            newUser.copy(username = UserName.fromRaw(unique("user2")))
                        )
                        fail("Should have failed")
                    }) {
                        it shouldBe EmailAlreadyExists
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
