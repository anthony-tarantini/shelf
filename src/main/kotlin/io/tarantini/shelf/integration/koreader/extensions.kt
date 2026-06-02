package io.tarantini.shelf.integration.koreader

import arrow.core.raise.catch
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Path.koreaderHash(): String? =
    withContext(Dispatchers.IO) {
        catch({
            val md = MessageDigest.getInstance("MD5")
            val chunkSize = 1024

            FileChannel.open(this@koreaderHash, StandardOpenOption.READ).use { channel ->
                val fileSize = channel.size()
                val buffer = ByteBuffer.allocate(chunkSize)

                for (i in -1..10) {
                    // KOReader partial MD5 reads at offsets step << (2*i) where step=1024.
                    // For i=-1, this is 1024 >> 2 = 256 (Lua right-shifts on negative count).
                    val shift = 2 * i
                    val offset = if (shift >= 0) 1024L shl shift else 1024L shr -shift

                    if (offset >= fileSize) break

                    buffer.clear()
                    channel.position(offset)
                    val bytesRead = channel.read(buffer)

                    if (bytesRead > 0) {
                        buffer.flip()
                        md.update(buffer)
                    }
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }) {
            null
        }
    }
