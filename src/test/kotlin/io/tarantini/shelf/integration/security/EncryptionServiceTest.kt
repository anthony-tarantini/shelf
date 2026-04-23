package io.tarantini.shelf.integration.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EncryptionServiceTest :
    StringSpec({
        "encrypt and decrypt should round-trip plaintext" {
            val service = EncryptionService("encryption-secret")
            val plaintext = "super secret".encodeToByteArray()

            val encrypted = service.encrypt(plaintext)
            val decrypted = service.decrypt(encrypted)

            decrypted.decodeToString() shouldBe "super secret"
        }

        "encrypt should use unique ivs for same plaintext" {
            val service = EncryptionService("encryption-secret")
            val plaintext = "same value".encodeToByteArray()

            val first = service.encrypt(plaintext)
            val second = service.encrypt(plaintext)

            first.iv.contentEquals(second.iv) shouldBe false
            first.ciphertext.contentEquals(second.ciphertext) shouldBe false
            first.ciphertext.toList() shouldNotBe plaintext.toList()
        }

        "encrypted payload equality should compare byte content" {
            val first =
                EncryptedPayload(ciphertext = byteArrayOf(1, 2, 3), iv = byteArrayOf(9, 8, 7))
            val second =
                EncryptedPayload(ciphertext = byteArrayOf(1, 2, 3), iv = byteArrayOf(9, 8, 7))

            first shouldBe second
            first.hashCode() shouldBe second.hashCode()
        }
    })
