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

        context(_: RaiseContext)
        override fun resolve(path: StoragePath): Path {
            val resolved = root.resolve(path.value).normalize()
            ensure(resolved.startsWith(root)) { UnauthorizedAccess }
            return resolved
        }

        context(_: RaiseContext)
        override suspend fun save(path: StoragePath, bytes: FileBytes) {
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                catch({
                    Files.createDirectories(fullPath.parent)
                    Files.write(fullPath, bytes.value)
                }) {
                    raise(StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun save(path: StoragePath, source: Path) {
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                catch({
                    Files.createDirectories(fullPath.parent)
                    Files.copy(source, fullPath, StandardCopyOption.REPLACE_EXISTING)
                }) {
                    raise(StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun read(path: StoragePath) =
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                catch({
                    if (!Files.exists(fullPath)) raise(FileNotFound)
                    FileBytes(Files.readAllBytes(fullPath))
                }) {
                    raise(StorageBackendError)
                }
            }

        context(_: RaiseContext)
        override suspend fun exists(path: StoragePath) =
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                catch({ Files.exists(fullPath) }) { raise(StorageBackendError) }
            }

        context(_: RaiseContext)
        override suspend fun delete(path: StoragePath) {
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                catch({
                    if (Files.exists(fullPath)) {
                        Files.delete(fullPath)
                    }
                }) {
                    raise(StorageBackendError)
                }
            }
        }

        context(_: RaiseContext)
        override suspend fun getReadChannel(path: StoragePath) =
            withContext(Dispatchers.IO) {
                val file = resolve(path)
                catch({
                    ensure(Files.exists(file)) { FileNotFound }
                    observability?.counter("shelf.storage.reads", "result", "success")?.increment()
                    Pair(file.toFile().length(), file.readChannel())
                }) {
                    observability?.counter("shelf.storage.reads", "result", "failure")?.increment()
                    raise(DataError)
                }
            }

        context(_: RaiseContext)
        override suspend fun getZipEntryReadChannel(
            scope: ResourceScope,
            zipPath: StoragePath,
            entryPath: String,
        ): Pair<Long, ByteReadChannel>? =
            withContext(Dispatchers.IO) {
                val zipFileLocal = resolve(zipPath).toFile()
                catch({
                    if (!zipFileLocal.exists()) return@catch null

                    val zipFile =
                        scope.install({ ZipFile(zipFileLocal) }) { zip, _ ->
                            catch({ zip.close() }) {}
                        }

                    val entry = zipFile.getEntry(entryPath.removePrefix("/")) ?: return@catch null
                    Pair(entry.size, zipFile.getInputStream(entry).toByteReadChannel())
                }) {
                    raise(DataError)
                }
            }

        context(_: RaiseContext)
        override suspend fun generateThumbnail(path: StoragePath, targetWidth: Int): StoragePath =
            withContext(Dispatchers.IO) {
                val fullPath = resolve(path)
                val thumbPath = path.thumbnail()
                val thumbFullPath = resolve(thumbPath)
                catch({
                    Files.createDirectories(thumbFullPath.parent)

                    Thumbnails.of(fullPath.toFile())
                        .width(targetWidth)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(0.8)
                        .toFile(thumbFullPath.toFile())

                    thumbPath
                }) {
                    logger.error(it) { "Failed to generate thumbnail." }
                    raise(StorageBackendError)
                }
            }
    }

interface StorageService {
    context(_: RaiseContext)
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
