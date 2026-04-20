@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.identity.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PrimitivesTest :
    StringSpec({
        "UserId should be valid for a correct UUID string" {
            val uuid = Uuid.random()
            recover({
                val userId = UserId(uuid.toString())
                userId.value shouldBe uuid
            }) {
                fail("Should not have failed: $it")
            }
        }

        "UserId should fail for an empty string" {
            recover({
                UserId("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyUserId
            }
        }

        "UserId should fail for an invalid UUID string" {
            recover({
                UserId("not-a-uuid")
                fail("Should have failed")
            }) {
                it shouldBe InvalidUserId
            }
        }

        "UserName should be valid for a correct string" {
            recover({
                val userName = UserName("johndoe")
                userName.value shouldBe "johndoe"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "UserName should fail for an empty string" {
            recover({
                UserName("")
                fail("Should have failed")
            }) {
                it shouldBe EmptyUsername
            }
        }

        "UserName should fail for a too short string" {
            recover({
                UserName("jo")
                fail("Should have failed")
            }) {
                it shouldBe TooShortUsername
            }
        }

        "UserEmail should be valid for a correct email" {
            recover({
                val userEmail = UserEmail("test@example.com")
                userEmail.value shouldBe "test@example.com"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "UserEmail should fail for an invalid email" {
            recover({
                UserEmail("invalid-email")
                fail("Should have failed")
            }) {
                it shouldBe InvalidEmail
            }
        }

        "UserPassword should be valid for a correct password" {
            recover({
                val userPassword = UserPassword("password123")
                userPassword.value shouldBe "password123"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "UserPassword should fail for a too short password" {
            recover({
                UserPassword("short")
                fail("Should have failed")
            }) {
                it shouldBe TooShortPassword
            }
        }
    })
