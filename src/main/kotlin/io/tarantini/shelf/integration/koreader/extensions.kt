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
                    val offset = if (i == -1) 0L else (1024L shl (2 * i))

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
