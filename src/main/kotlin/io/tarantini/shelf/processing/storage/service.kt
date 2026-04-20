package io.tarantini.shelf.processing.storage

import arrow.core.raise.catch
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import arrow.fx.coroutines.ResourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.DataError
import io.tarantini.shelf.observability.Observability
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails

private val logger = KotlinLogging.logger {}

fun localStorageService(basePath: String, observability: Observability? = null) =
    object : StorageService {
        private val root = Paths.get(basePath).toAbsolutePath().normalize()

        override fun resolve(path: StoragePath): Path {
            val resolved = root.resolve(path.value).normalize()
            if (!resolved.startsWith(root)) {
                throw SecurityException("Resolved path escaped storage root: ${path.value}")
            }
            return resolved
        }

        private fun resolveSecure(path: StoragePath): Path = resolve(path)

        context(_: RaiseContext)
        override suspend fun save(path: StoragePath, bytes: FileBytes) {
            withContext(Dispatchers.IO) {
                catch({
                    val fullPath = resolveSecure(path)
                    Files.createDirectories(fullPath.parent)
                    Files.write(fullPath, bytes.value)
                }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun save(path: StoragePath, source: Path) {
            withContext(Dispatchers.IO) {
                catch({
                    val fullPath = resolveSecure(path)
                    Files.createDirectories(fullPath.parent)
                    Files.copy(source, fullPath, StandardCopyOption.REPLACE_EXISTING)
                }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun read(path: StoragePath) =
            withContext(Dispatchers.IO) {
                catch({
                    val fullPath = resolveSecure(path)
                    if (!Files.exists(fullPath)) raise(FileNotFound)
                    FileBytes(Files.readAllBytes(fullPath))
                }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }

        context(_: RaiseContext)
        override suspend fun exists(path: StoragePath) =
            withContext(Dispatchers.IO) {
                catch({ Files.exists(resolveSecure(path)) }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }

        context(_: RaiseContext)
        override suspend fun delete(path: StoragePath) {
            withContext(Dispatchers.IO) {
                catch({
                    val fullPath = resolveSecure(path)
                    if (Files.exists(fullPath)) {
                        Files.delete(fullPath)
                    }
                }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun getReadChannel(path: StoragePath) =
            withContext(Dispatchers.IO) {
                catch({
                    val file = resolveSecure(path)
                    ensure(Files.exists(file)) { FileNotFound }
                    observability?.counter("shelf.storage.reads", "result", "success")?.increment()
                    Pair(file.toFile().length(), file.readChannel())
                }) {
                    observability?.counter("shelf.storage.reads", "result", "failure")?.increment()
                    raise(if (it is SecurityException) UnauthorizedAccess else DataError)
                }
            }

        context(_: RaiseContext)
        override suspend fun getZipEntryReadChannel(
            scope: ResourceScope,
            zipPath: StoragePath,
            entryPath: String,
        ): Pair<Long, ByteReadChannel>? =
            withContext(Dispatchers.IO) {
                catch({
                    val zipFileLocal = resolveSecure(zipPath).toFile()
                    if (!zipFileLocal.exists()) return@catch null

                    val zipFile =
                        scope.install({ ZipFile(zipFileLocal) }) { zip, _ ->
                            catch({ zip.close() }) {}
                        }

                    val entry = zipFile.getEntry(entryPath.removePrefix("/")) ?: return@catch null
                    Pair(entry.size, zipFile.getInputStream(entry).toByteReadChannel())
                }) {
                    raise(if (it is SecurityException) UnauthorizedAccess else DataError)
                }
            }

        context(_: RaiseContext)
        override suspend fun generateThumbnail(path: StoragePath, targetWidth: Int): StoragePath =
            withContext(Dispatchers.IO) {
                catch({
                    val fullPath = resolveSecure(path)
                    val thumbPath = path.thumbnail()
                    val thumbFullPath = resolveSecure(thumbPath)
                    Files.createDirectories(thumbFullPath.parent)

                    Thumbnails.of(fullPath.toFile())
                        .width(targetWidth)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(0.8)
                        .toFile(thumbFullPath.toFile())

                    thumbPath
                }) {
                    logger.error(it) { "Failed to generate thumbnail for path: $path" }
                    raise(if (it is SecurityException) UnauthorizedAccess else StorageBackendError)
                }
            }
    }

interface StorageService {
    fun resolve(path: StoragePath): Path

    context(_: RaiseContext)
    suspend fun save(path: StoragePath, bytes: FileBytes)

    context(_: RaiseContext)
    suspend fun save(path: StoragePath, source: Path)

    context(_: RaiseContext)
    suspend fun read(path: StoragePath): FileBytes

    context(_: RaiseContext)
    suspend fun exists(path: StoragePath): Boolean

    context(_: RaiseContext)
    suspend fun delete(path: StoragePath)

    context(_: RaiseContext)
    suspend fun getReadChannel(path: StoragePath): Pair<Long, ByteReadChannel>

    context(_: RaiseContext)
    suspend fun getZipEntryReadChannel(
        scope: ResourceScope,
        zipPath: StoragePath,
        entryPath: String,
    ): Pair<Long, ByteReadChannel>?

    context(_: RaiseContext)
    suspend fun generateThumbnail(path: StoragePath, targetWidth: Int = 300): StoragePath
}
