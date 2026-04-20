@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.PersistenceState
import kotlin.uuid.Uuid

class UserCommandAndDeciderTest :
    StringSpec({
        "new user request maps to register command" {
            recover({
                val command =
                    NewUserRequest("user@example.com", "testuser", "password123")
                        .toRegisterCommand()
                command.email.value shouldBe "user@example.com"
                command.username.value shouldBe "testuser"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "login request rejects missing email" {
            recover({
                LoginUserRequest(email = null, password = "password123").toCommand()
                fail("Should have failed")
            }) {
                it shouldBe EmptyEmail
            }
        }

        "update request maps optional fields" {
            recover({
                val command = UpdateUserRequest(email = "next@example.com").toCommand()
                command.email?.value shouldBe "next@example.com"
                command.username shouldBe null
                command.password shouldBe null
            }) {
                fail("Should not have failed: $it")
            }
        }

        "decider keeps existing fields when update command omits them" {
            val userId = UserId.fromRaw(Uuid.random())
            val salt = Salt.generate()
            val existing =
                UserAggregate<PersistenceState.Persisted>(
                    user =
                        UserRoot.fromRaw(
                            id = userId,
                            email = UserEmail.fromRaw("existing@example.com"),
                            username = UserName.fromRaw("existinguser"),
                            role = UserRole.USER,
                        ),
                    role = UserRole.USER,
                    salt = salt,
                    hashedPassword =
                        HashedPassword.create(UserPassword.fromRaw("password123"), salt),
                )

            val update = UserMutationDecider.decideUpdate(existing, UpdateCurrentUserCommand())
            update.email.value shouldBe "existing@example.com"
            update.username.value shouldBe "existinguser"
            update.role shouldBe UserRole.USER
        }
    })
