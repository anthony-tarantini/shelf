package io.tarantini.shelf.integration.koreader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

class KoreaderHashTest :
    StringSpec({
        "partial MD5 matches KOReader algorithm for typical file" {
            val tmp = Files.createTempFile("koreader-hash-", ".bin")
            try {
                val bytes = ByteArray(2_000_000) { (it and 0xFF).toByte() }
                Files.write(tmp, bytes)

                val expected = referencePartialMd5(bytes)
                val actual = runBlocking { tmp.koreaderHash() }
                actual shouldBe expected
            } finally {
                Files.deleteIfExists(tmp)
            }
        }

        "partial MD5 handles small file (less than 1024 bytes)" {
            val tmp = Files.createTempFile("koreader-hash-small-", ".bin")
            try {
                val bytes = ByteArray(500) { (it and 0xFF).toByte() }
                Files.write(tmp, bytes)

                val expected = referencePartialMd5(bytes)
                val actual = runBlocking { tmp.koreaderHash() }
                actual shouldBe expected
            } finally {
                Files.deleteIfExists(tmp)
            }
        }

        "partial MD5 i=-1 chunk starts at offset 256" {
            val tmp = Files.createTempFile("koreader-hash-offset-", ".bin")
            try {
                // Two files identical from offset 256 onward but differing in bytes [0,256).
                // KOReader's partial MD5 does not read offsets in [0, 256), so the hashes
                // should be identical.
                val sizeBytes = 8_192
                val baseline = ByteArray(sizeBytes) { (it and 0xFF).toByte() }
                Files.write(tmp, baseline)
                val hashBaseline = runBlocking { tmp.koreaderHash() }

                val varied = baseline.copyOf()
                for (i in 0 until 256) varied[i] = (varied[i].toInt() xor 0xFF).toByte()
                Files.write(tmp, varied)
                val hashVaried = runBlocking { tmp.koreaderHash() }

                hashVaried shouldBe hashBaseline
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
    })

private fun referencePartialMd5(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("MD5")
    val step = 1024
    val size = 1024
    for (i in -1..10) {
        val shift = 2 * i
        val offset = if (shift >= 0) (step.toLong() shl shift) else (step.toLong() shr -shift)
        if (offset >= bytes.size) break
        val end = minOf(offset + size, bytes.size.toLong()).toInt()
        md.update(bytes, offset.toInt(), end - offset.toInt())
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
