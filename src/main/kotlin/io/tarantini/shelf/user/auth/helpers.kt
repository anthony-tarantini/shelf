package io.tarantini.shelf.user.auth

import io.tarantini.shelf.user.identity.domain.UserPassword
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val secretKeysFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
private const val defaultIterations = 100000
private const val defaultKeyLength = 512

fun generateSalt(): ByteArray = UUID.randomUUID().toString().toByteArray()

fun hashPassword(password: UserPassword, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.value.toCharArray(), salt, defaultIterations, defaultKeyLength)
    return secretKeysFactory.generateSecret(spec).encoded
}
