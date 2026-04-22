package io.tarantini.shelf.integration.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(val ciphertext: ByteArray, val iv: ByteArray)

class EncryptionService(encryptionSecret: String) {
    private val secretKey: SecretKey = deriveKey(encryptionSecret)
    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return EncryptedPayload(ciphertext = cipher.doFinal(plaintext), iv = iv)
    }

    fun decrypt(payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    private fun deriveKey(secret: String): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), "shelf-integration".toByteArray(), 100_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
