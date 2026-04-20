package io.tarantini.shelf.user.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.tarantini.shelf.user.identity.domain.UserPassword

class PasswordHashTest :
    StringSpec({
        "generateSalt should produce unique salts" {
            val salt1 = generateSalt()
            val salt2 = generateSalt()
            salt1 shouldNotBe salt2
        }

        "hashPassword should produce consistent results for same password and salt" {
            val password = UserPassword.fromRaw("password123")
            val salt = generateSalt()
            val hash1 = hashPassword(password, salt)
            val hash2 = hashPassword(password, salt)
            hash1 shouldBe hash2
        }

        "hashPassword should produce different hashes for different salts" {
            val password = UserPassword.fromRaw("password123")
            val salt1 = generateSalt()
            val salt2 = generateSalt()
            val hash1 = hashPassword(password, salt1)
            val hash2 = hashPassword(password, salt2)
            hash1 shouldNotBe hash2
        }

        "hashPassword should produce different hashes for different passwords" {
            val password1 = UserPassword.fromRaw("password123")
            val password2 = UserPassword.fromRaw("different123")
            val salt = generateSalt()
            val hash1 = hashPassword(password1, salt)
            val hash2 = hashPassword(password2, salt)
            hash1 shouldNotBe hash2
        }
    })
